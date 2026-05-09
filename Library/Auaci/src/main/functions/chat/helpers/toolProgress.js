// src/main/functions/chat/helpers/toolProgress.js
// Tool progress update system for live statistics during execution

const EventEmitter = require('events');

class ToolProgressManager extends EventEmitter {
  constructor() {
    super();
    this.progressData = new Map();
    this.setMaxListeners(100); // Allow many listeners
  }

  /**
   * Register progress for a tool
   * @param {string} toolId - Unique tool identifier
   * @param {object} progress - Progress data object
   */
  updateProgress(toolId, progress) {
    if (!toolId) return;
    
    const currentData = this.progressData.get(toolId) || {};
    const updatedData = { ...currentData, ...progress, updatedAt: Date.now() };
    
    this.progressData.set(toolId, updatedData);
    
    // Emit progress update event
    this.emit('progressUpdate', { toolId, progress: updatedData });
    
    // Clean up old progress data after 5 minutes
    setTimeout(() => {
      if (this.progressData.has(toolId)) {
        const data = this.progressData.get(toolId);
        if (Date.now() - data.updatedAt > 300000) { // 5 minutes
          this.progressData.delete(toolId);
        }
      }
    }, 300000);
  }

  /**
   * Get current progress for a tool
   * @param {string} toolId - Unique tool identifier
   * @returns {object|null} - Progress data or null if not found
   */
  getProgress(toolId) {
    return this.progressData.get(toolId) || null;
  }

  /**
   * Remove progress data for a tool
   * @param {string} toolId - Unique tool identifier
   */
  clearProgress(toolId) {
    this.progressData.delete(toolId);
  }

  /**
   * Generate a unique tool ID
   * @param {string} toolName - Tool name
   * @param {object} input - Tool input
   * @returns {string} - Unique tool ID
   */
  generateToolId(toolName, input) {
    const timestamp = Date.now();
    const randomSuffix = Math.random().toString(36).slice(2, 8);
    const inputHash = JSON.stringify(input || {}).split('').reduce((a, b) => {
      a = ((a << 5) - a) + b.charCodeAt(0);
      return a & a;
    }, 0).toString(36);
    
    return `${toolName}_${timestamp}_${inputHash}_${randomSuffix}`;
  }
}

// Global instance
const toolProgressManager = new ToolProgressManager();

module.exports = toolProgressManager;
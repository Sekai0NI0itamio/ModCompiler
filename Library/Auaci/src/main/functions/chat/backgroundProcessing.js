// Background Processing System
// Ensures GPT cycles and tool execution continue when app window is unfocused

const { ipcRenderer } = require('electron');

class BackgroundProcessor {
  constructor() {
    this.isBackgroundMode = false;
    this.activeTasks = new Map();
    this.windowFocusState = true;
    this.visibilityChangeHandler = null;
    this.blurHandler = null;
    this.focusHandler = null;
    this.mainProcessBackgroundMode = false;
    this.debug = false;
    
    // Performance optimization flags
    this.useRequestIdleCallback = typeof requestIdleCallback !== 'undefined';
    this.useMessageChannel = typeof MessageChannel !== 'undefined';
    
    this.init();
  }

  init() {
    // Set up visibility change detection
    this.setupVisibilityDetection();
    
    // Set up focus/blur handlers
    this.setupFocusHandlers();
    
    // Set up performance optimization for background mode
    this.setupPerformanceOptimizations();
    
    // Set up main process integration
    this.setupMainProcessIntegration();
    
    console.log('[BackgroundProcessor] Initialized');
  }

  setupVisibilityDetection() {
    // Page Visibility API
    if (typeof document !== 'undefined' && document.addEventListener) {
      this.visibilityChangeHandler = () => {
        const isHidden = document.hidden || document.visibilityState === 'hidden';
        this.handleVisibilityChange(!isHidden);
      };
      
      document.addEventListener('visibilitychange', this.visibilityChangeHandler);
    }
  }

  setupFocusHandlers() {
    if (typeof window !== 'undefined' && window.addEventListener) {
      this.blurHandler = () => {
        this.handleWindowFocusChange(false);
      };
      
      this.focusHandler = () => {
        this.handleWindowFocusChange(true);
      };
      
      window.addEventListener('blur', this.blurHandler);
      window.addEventListener('focus', this.focusHandler);
    }
  }

  setupPerformanceOptimizations() {
    // Set up background task scheduling
    // NOTE: requestIdleCallback is heavily throttled in background tabs/windows,
    // so prefer setTimeout to keep tool execution moving when unfocused.
    this.scheduleBackgroundTask = (task, options = {}) => {
      const delay = typeof options.delay === 'number' ? options.delay : 0;
      return new Promise((resolve) => {
        setTimeout(() => {
          task();
          resolve();
        }, delay);
      });
    };
  }

  setupMainProcessIntegration() {
    if (!ipcRenderer) {
      console.warn('[BackgroundProcessor] No IPC renderer available');
      return;
    }

    // Listen for main process background mode changes
    ipcRenderer.on('main-process-background-mode-changed', (event, data) => {
      this.handleMainProcessBackgroundChange(data);
    });

    // Query initial main process background mode
    this.queryMainProcessBackgroundMode();
  }

  handleMainProcessBackgroundChange(data) {
    const wasBackgroundMode = this.isBackgroundMode;
    this.mainProcessBackgroundMode = data.isBackgroundMode;
    
    // Combine renderer and main process background modes
    this.isBackgroundMode = this.mainProcessBackgroundMode || 
                          !this.windowFocusState || 
                          (typeof document !== 'undefined' && (document.hidden || document.visibilityState === 'hidden'));
    
    if (wasBackgroundMode !== this.isBackgroundMode) {
      console.log(`[BackgroundProcessor] Combined background mode: ${this.isBackgroundMode}`);
      this.notifyModeChange();
    }
  }

  async queryMainProcessBackgroundMode() {
    if (!ipcRenderer) return;

    try {
      const mode = await ipcRenderer.invoke('get-main-process-background-mode');
      this.mainProcessBackgroundMode = mode;
      
      // Update combined mode
      this.handleMainProcessBackgroundChange({ isBackgroundMode: mode });
    } catch (err) {
      console.debug('[BackgroundProcessor] Failed to query main process background mode:', err.message);
    }
  }

  handleVisibilityChange(isVisible) {
    const wasBackgroundMode = this.isBackgroundMode;
    
    // Combine visibility state with window focus and main process mode
    this.isBackgroundMode = this.mainProcessBackgroundMode || 
                          !this.windowFocusState || 
                          !isVisible;
    
    if (wasBackgroundMode !== this.isBackgroundMode) {
      console.log(`[BackgroundProcessor] Visibility changed: ${isVisible ? 'visible' : 'hidden'}`);
      this.notifyModeChange();
    }
  }

  handleWindowFocusChange(hasFocus) {
    const wasBackgroundMode = this.isBackgroundMode;
    this.windowFocusState = hasFocus;
    
    // Combine window focus with visibility state and main process mode
    this.isBackgroundMode = this.mainProcessBackgroundMode || 
                          !hasFocus || 
                          (typeof document !== 'undefined' && (document.hidden || document.visibilityState === 'hidden'));
    
    if (wasBackgroundMode !== this.isBackgroundMode) {
      console.log(`[BackgroundProcessor] Focus changed: ${hasFocus ? 'focused' : 'unfocused'}`);
      this.notifyModeChange();
    }
  }

  notifyModeChange() {
    // Notify other parts of the system about background mode changes
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('background-mode-change', {
        detail: { isBackgroundMode: this.isBackgroundMode }
      }));
    }
    
    // Notify main process if needed
    if (ipcRenderer) {
      try {
        ipcRenderer.send('background-mode-changed', this.isBackgroundMode);
      } catch (err) {
        console.debug('[BackgroundProcessor] IPC not available:', err.message);
      }
    }
  }

  // Register a task to be executed in background mode
  registerBackgroundTask(taskId, taskFunction, options = {}) {
    const task = {
      id: taskId,
      execute: taskFunction,
      priority: options.priority || 'normal',
      maxRetries: options.maxRetries || 3,
      retryCount: 0,
      status: 'pending'
    };
    
    this.activeTasks.set(taskId, task);
    if (this.debug) {
      console.log(`[BackgroundProcessor] Registered task: ${taskId}`);
    }
    
    return taskId;
  }

  // Execute a task with background mode awareness
  async executeTask(taskId, ...args) {
    const task = this.activeTasks.get(taskId);
    if (!task) {
      throw new Error(`Task not found: ${taskId}`);
    }
    
    task.status = 'running';
    
    try {
      let result;
      // If in background mode, use optimized scheduling
      if (this.isBackgroundMode) {
        result = await this.executeInBackgroundMode(task.execute, args);
      } else {
        // Normal execution when app is focused
        result = await task.execute(...args);
      }
      
      task.status = 'completed';
      task.retryCount = 0;

      return result;
      
    } catch (error) {
      task.status = 'failed';
      task.lastError = error;
      
      // Retry logic for background mode
      if (this.isBackgroundMode && task.retryCount < task.maxRetries) {
        task.retryCount++;
        console.log(`[BackgroundProcessor] Retrying task ${taskId} (attempt ${task.retryCount})`);
        
        // Schedule retry with exponential backoff
        const delay = Math.min(1000 * Math.pow(2, task.retryCount), 30000);
        return new Promise((resolve, reject) => {
          setTimeout(() => {
            this.executeTask(taskId, ...args).then(resolve).catch(reject);
          }, delay);
        });
      }
      
      throw error;
    }
  }

  async executeInBackgroundMode(taskFunction, args) {
    // Use setTimeout for background execution to avoid idle throttling
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        try {
          const result = taskFunction(...args);
          if (result instanceof Promise) {
            result.then(resolve).catch(reject);
          } else {
            resolve(result);
          }
        } catch (error) {
          reject(error);
        }
      }, 0);
    });
  }

  // Unregister a task
  unregisterTask(taskId) {
    if (this.activeTasks.has(taskId)) {
      this.activeTasks.delete(taskId);
      if (this.debug) {
        console.log(`[BackgroundProcessor] Unregistered task: ${taskId}`);
      }
    }
  }

  // Get current background mode status
  getBackgroundMode() {
    return this.isBackgroundMode;
  }

  // Force background mode (for testing)
  setBackgroundMode(enable) {
    this.isBackgroundMode = enable;
    this.notifyModeChange();
  }

  // Cleanup
  destroy() {
    if (this.visibilityChangeHandler && document && document.removeEventListener) {
      document.removeEventListener('visibilitychange', this.visibilityChangeHandler);
    }
    
    if (this.blurHandler && window && window.removeEventListener) {
      window.removeEventListener('blur', this.blurHandler);
    }
    
    if (this.focusHandler && window && window.removeEventListener) {
      window.removeEventListener('focus', this.focusHandler);
    }
    
    this.activeTasks.clear();
    console.log('[BackgroundProcessor] Destroyed');
  }
}

// Singleton instance
let backgroundProcessor = null;

function getBackgroundProcessor() {
  if (!backgroundProcessor) {
    backgroundProcessor = new BackgroundProcessor();
  }
  return backgroundProcessor;
}

// Export for use in other modules
module.exports = {
  BackgroundProcessor,
  getBackgroundProcessor,
  // Helper functions for common background operations
  
  // Execute tool with background awareness
  async executeToolWithBackgroundSupport(toolName, toolInput, options = {}) {
    const processor = getBackgroundProcessor();
    const taskId = `tool-${toolName}-${Date.now()}`;
    
    // Import tool executor dynamically to avoid circular dependencies
    const { executeTool } = require('./send/modules/toolExecutor');
    
    // Register the task first
    processor.registerBackgroundTask(taskId, () => {
      return executeTool(toolName, toolInput, {
        ...options,
        skipBackground: true
      });
    }, {
      // Higher priority for tool execution to prevent timeout issues
      priority: 'high',
      maxRetries: 2
    });
    
    try {
      const result = await processor.executeTask(taskId);
      
      // Ensure the result is properly handled when app regains focus
      if (processor.getBackgroundMode() && typeof window !== 'undefined') {
        // Dispatch a custom event to notify that a tool completed in background
        window.dispatchEvent(new CustomEvent('tool-completed-in-background', {
          detail: { 
            toolName, 
            result, 
            options,
            sessionId: options.sessionId,
            entryIndex: options.entryIndex,
            toolId: taskId
          }
        }));
      }
      
      return result;
    } finally {
      processor.unregisterTask(taskId);
    }
  },
  
  // Execute GPT cycle with background awareness
  async executeGptCycleWithBackgroundSupport(options) {
    const processor = getBackgroundProcessor();
    const taskId = `gpt-cycle-${Date.now()}`;
    
    // Import GPT cycle dynamically
    const { runGptCycle } = require('./send/modules/gptCycle');
    
    // Register the task first
    processor.registerBackgroundTask(taskId, () => {
      return runGptCycle(options);
    });
    
    return processor.executeTask(taskId);
  },
  
  // Check if app is in background mode
  isInBackgroundMode() {
    const processor = getBackgroundProcessor();
    return processor.getBackgroundMode();
  }
};

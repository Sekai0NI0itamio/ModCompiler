/**
 * Prompt Registry System
 * Manages different prompts for different models and contexts
 * Inspired by vscode-copilot-chat's PromptRegistry
 */

const fs = require('fs').promises;
const path = require('path');

const PROMPT_TYPES = {
  BASE: 'BASE',
  EDITING: 'EDITING',
};

class PromptRegistry {
  constructor() {
    this.prompts = {};
    this.loadedPrompts = {};
  }

  /**
   * Register a prompt type with its file location
   */
  registerPrompt(type, filePath) {
    this.prompts[type] = filePath;
  }

  /**
   * Load a prompt from file
   */
  async loadPrompt(type) {
    if (this.loadedPrompts[type]) {
      return this.loadedPrompts[type];
    }

    const filePath = this.prompts[type];
    if (!filePath) {
      throw new Error(`Prompt type "${type}" not registered`);
    }

    try {
      const content = await fs.readFile(filePath, 'utf8');
      this.loadedPrompts[type] = String(content || '').trim();
      return this.loadedPrompts[type];
    } catch (err) {
      console.error(`Failed to load prompt ${type} from ${filePath}:`, err.message);
      throw err;
    }
  }

  /**
   * Get the appropriate prompt for a given model family
   */
  getPromptForModel(modelFamily) {
    // Unified prompt regardless of model
    return PROMPT_TYPES.BASE;
  }

  /**
   * Clear the cache (for testing)
   */
  clearCache() {
    this.loadedPrompts = {};
  }
}

// Create singleton instance
const registry = new PromptRegistry();

// Register default prompts
const instructionsDir = __dirname;
registry.registerPrompt(PROMPT_TYPES.BASE, path.join(instructionsDir, 'AUACIprompt.txt'));
registry.registerPrompt(PROMPT_TYPES.EDITING, path.join(instructionsDir, 'EDITING_INSTRUCTIONS.txt'));

module.exports = {
  PromptRegistry,
  registry,
  PROMPT_TYPES,
};

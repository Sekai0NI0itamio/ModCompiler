/**
 * Prompt Composer - Utilities for building complex prompts
 * Helps compose prompts from reusable components
 * Inspired by vscode-copilot-chat's prompt element system
 */

/**
 * PromptSection
 * Represents a section of a prompt with a tag and content
 */
class PromptSection {
  constructor(name, content, priority = 100) {
    this.name = name;
    this.content = content;
    this.priority = priority;
    this.conditional = null;
  }

  /**
   * Make this section conditional on a capability
   * @param {Function} condition - Function that returns boolean
   */
  whenCapable(condition) {
    this.conditional = condition;
    return this;
  }

  /**
   * Render the section if conditions are met
   */
  render(context) {
    if (this.conditional && !this.conditional(context)) {
      return '';
    }
    return `<${this.name}>\n${this.content}\n</${this.name}>\n`;
  }
}

/**
 * PromptComposer
 * Assembles prompts from sections
 */
class PromptComposer {
  constructor() {
    this.sections = [];
    this.systemSection = null;
    this.context = {};
  }

  /**
   * Add a prompt section
   */
  addSection(name, content, priority = 100) {
    const section = new PromptSection(name, content, priority);
    this.sections.push(section);
    return section;
  }

  /**
   * Set the system/role section
   */
  setSystem(content) {
    this.systemSection = new PromptSection('system', content, 1000);
    return this;
  }

  /**
   * Set context for conditional sections
   */
  setContext(context) {
    this.context = { ...this.context, ...context };
    return this;
  }

  /**
   * Render the full prompt
   */
  render() {
    const parts = [];

    // System section first
    if (this.systemSection) {
      parts.push(this.systemSection.render(this.context));
    }

    // Sort remaining sections by priority (highest first)
    const sorted = [...this.sections].sort((a, b) => b.priority - a.priority);

    // Render sections
    for (const section of sorted) {
      parts.push(section.render(this.context));
    }

    return parts.join('\n').trim();
  }

  /**
   * Clear all sections
   */
  clear() {
    this.sections = [];
    this.systemSection = null;
    this.context = {};
    return this;
  }
}

/**
 * Build a prompt from template and values
 * Simple string substitution for prompt templates
 */
function buildFromTemplate(template, values = {}) {
  let result = template;
  for (const [key, value] of Object.entries(values)) {
    const pattern = new RegExp(`{{${key}}}`, 'g');
    result = result.replace(pattern, String(value || ''));
  }
  return result;
}

/**
 * Create an instruction tag for conditional tool instructions
 */
function createToolInstruction(toolName, instruction, toolCapabilities) {
  if (!toolCapabilities || !toolCapabilities[toolName]) {
    return '';
  }
  return `When using the ${toolName} tool: ${instruction}\n`;
}

/**
 * Build conditional tool instructions
 * Generates relevant tool instructions based on available tools
 */
function buildToolInstructions(toolCapabilities) {
  const instructions = [];

  if (toolCapabilities.view) {
    instructions.push(
      'When using the view tool, prefer reading a large section over calling view many times. Read large enough context to ensure you get what you need.'
    );
  }

  if (toolCapabilities.str_replace || toolCapabilities.batch_str_replace) {
    instructions.push(
      'When editing files with str_replace or batch_str_replace, include 3-5 lines of context before and after the target text to ensure your replacement is unambiguous.'
    );
  }

  if (toolCapabilities.run_command) {
    instructions.push(
      'Do not call run_command multiple times in parallel. Instead, run one command and wait for the output before running the next command.'
    );
  }

  if (toolCapabilities.semantic_search) {
    instructions.push(
      'If you do not know exactly what you are searching for, use semantic_search to do a semantic search across the workspace.'
    );
  }

  return instructions.join('\n\n');
}

/**
 * Merge multiple prompts together
 */
function mergePrompts(...prompts) {
  return prompts
    .filter(p => p && String(p).trim())
    .map(p => String(p).trim())
    .join('\n\n');
}

module.exports = {
  PromptSection,
  PromptComposer,
  buildFromTemplate,
  createToolInstruction,
  buildToolInstructions,
  mergePrompts,
};

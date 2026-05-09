// src/main/functions/configHandler/defaultconfig.js
// Centralized default configuration for Auaci.
// This file is required by the configHandler so defaults live in one place.

const DEFAULT_CONFIG = {
  models: ['GPT-5-mini', 'GPT-5.1-codex', 'GPT-5-nano'],
  defaultModel: 'GPT-5-mini',
  defaultPrompt: 'Auaci',
  apiPort: 8129,
  // New chat provider settings
  apiProvider: 'local', // 'local' | 'openai'
  openaiApiKey: '',
  openaiBaseUrl: 'https://api.openai.com/v1',
  openaiOrganization: '',
  // Optional: default hidden files for directory viewer
  hiddenFiles: ['.DS_Store', '.Trash'],
  // Checkpoint mode: 'lite' or 'git'
  checkpointMode: 'lite',
  // GPT system logic: how the main assistant coordinates tools.
  // - 'tool-openai': use OpenAI/Poe extra_body options (reasoning_effort, etc.).
  // - 'tool-message-self': legacy inline <tool_use> parsing only.
  gptSystemLogic: 'tool-openai',
};

module.exports = { DEFAULT_CONFIG };

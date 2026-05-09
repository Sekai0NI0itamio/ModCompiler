// src/main/functions/chat/messageCache.js
// OPTIMIZED: Efficient message caching with better performance
const fs = require('fs').promises;
const { createRenderer } = require('./renderer');
const marked = require('marked');

const logPath = '/tmp/events.log';

// Markdown cache with better hashing (same as responseRenderer)
const markdownCache = new Map();
const MARKDOWN_CACHE_MAX_SIZE = 200;
const MARKDOWN_CACHE_TTL = 120000;

/**
 * OPTIMIZED: Better hash function to prevent collisions
 */
function hashContent(text) {
  if (!text) return '0';
  const len = text.length;
  const sample1 = text.substring(0, Math.min(20, len));
  const sample2 = len > 40 ? text.substring(len - 20) : '';
  const sample3 = len > 100 ? text.substring(Math.floor(len / 2), Math.floor(len / 2) + 20) : '';
  const combined = sample1 + sample2 + sample3;
  let hash = 0;
  for (let i = 0; i < combined.length; i++) {
    const char = combined.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash;
  }
  return hash.toString(36) + ':' + len;
}

/**
 * OPTIMIZED: Markdown caching for loaded messages
 */
function parseMarkdownWithCache(text) {
  if (!text || typeof text !== 'string') return '';
  const hash = hashContent(text);
  const cached = markdownCache.get(hash);
  if (cached && Date.now() - cached.timestamp < MARKDOWN_CACHE_TTL) {
    return cached.html;
  }
  const html = marked.parse(text);
  if (markdownCache.size >= MARKDOWN_CACHE_MAX_SIZE) {
    const firstKey = markdownCache.keys().next().value;
    markdownCache.delete(firstKey);
  }
  markdownCache.set(hash, { html, timestamp: Date.now() });
  return html;
}

// Track code blocks with listeners to prevent duplication
const codeBlocksWithListeners = new Set();

/**
 * OPTIMIZED: Only attach listeners to new code blocks
 */
function attachListenersToNewCodeBlocks(container) {
  if (!container) return;
  const codeBlocks = container.querySelectorAll('.copy-button');
  codeBlocks.forEach((btn) => {
    const btnId = btn.id;
    if (btnId && !codeBlocksWithListeners.has(btnId)) {
      try {
        btn.addEventListener('click', (e) => {
          e.preventDefault();
          const code = decodeURIComponent(btn.dataset.code || '');
          if (code) {
            navigator.clipboard.writeText(code).then(() => {
              const checkIcon = btn.querySelector('.check-icon');
              const copyIcon = btn.querySelector('.copy-icon');
              if (checkIcon && copyIcon) {
                copyIcon.style.display = 'none';
                checkIcon.style.display = 'inline';
                setTimeout(() => {
                  copyIcon.style.display = 'inline';
                  checkIcon.style.display = 'none';
                }, 2000);
              }
            });
          }
        });
        codeBlocksWithListeners.add(btnId);
      } catch (_) {}
    }
  });
}

function startCacheManager() {
  setInterval(() => {
    cacheOldMessages();
  }, 5 * 60 * 1000);
}

function cacheOldMessages() {
  const chatMessages = document.getElementById('chat-messages');
  if (!chatMessages || window.allChatEntries.length === 0) return;
  const renderedMessages = chatMessages.querySelectorAll('.message.user-message').length;
  if (renderedMessages <= 5) return;
  const messagesToCache = renderedMessages - 5;
  let userMessagesFound = 0;
  let elementsToRemove = [];
  const allMessages = chatMessages.querySelectorAll('.message');
  for (let element of allMessages) {
    if (element.classList.contains('user-message')) {
      userMessagesFound++;
      if (userMessagesFound <= messagesToCache) {
        elementsToRemove.push(element);
        const nextElement = element.nextElementSibling;
        if (nextElement && nextElement.classList.contains('gpt-message')) {
          elementsToRemove.push(nextElement);
        }
      } else {
        break;
      }
    }
  }
  if (elementsToRemove.length === 0) return;
  const currentScrollTop = chatMessages.scrollTop;
  const currentScrollHeight = chatMessages.scrollHeight;
  elementsToRemove.forEach(element => element.remove());
  const newScrollHeight = chatMessages.scrollHeight;
  const heightDifference = currentScrollHeight - newScrollHeight;
  const newScrollTop = Math.max(0, currentScrollTop - heightDifference);
  window.renderedStartIndex += messagesToCache;
  let cacheLoader = chatMessages.querySelector('.cache-loader');
  if (!cacheLoader) {
    cacheLoader = document.createElement('div');
    cacheLoader.className = 'cache-loader';
    cacheLoader.innerHTML = `
      <div class="cache-loader-content">
        <span class="cache-loader-text">Previous messages have been cached</span>
        <button class="cache-loader-button">Click to load more messages</button>
      </div>
    `;
    chatMessages.insertBefore(cacheLoader, chatMessages.firstChild);
    const button = cacheLoader.querySelector('.cache-loader-button');
    button.addEventListener('click', () => loadCachedMessages());
  }
  chatMessages.scrollTop = newScrollTop;
  fs.appendFile(logPath, `[${new Date().toISOString()}] Cached ${messagesToCache} message pairs\n`);
}

async function loadCachedMessages() {
  if (window.renderedStartIndex <= 0 || window.isLoadingMore) return;
  window.isLoadingMore = true;
  const chatMessages = document.getElementById('chat-messages');
  const oldScrollHeight = chatMessages.scrollHeight;
  const oldScrollTop = chatMessages.scrollTop;
  let messagesToLoad = Math.min(5, window.renderedStartIndex);
  let newStartIndex = Math.max(0, window.renderedStartIndex - messagesToLoad);
  const entriesToLoad = window.allChatEntries.slice(newStartIndex, window.renderedStartIndex);
  const fragment = document.createDocumentFragment();
  for (const entry of entriesToLoad) {
    const userDiv = document.createElement('div');
    userDiv.className = 'message user-message';
    const userContent = document.createElement('div');
    userContent.className = 'message-content';
    if (entry.user.text) {
      const textDiv = document.createElement('div');
      textDiv.textContent = entry.user.text;
      userContent.appendChild(textDiv);
    }
    if (entry.user.files) {
      entry.user.files.forEach(file => {
        userContent.innerHTML += require('./fileUtils').createHistoryFileBox(file);
      });
    }
    userDiv.appendChild(userContent);
    fragment.appendChild(userDiv);
    if (entry.gpt) {
      // Skip rendering if GPT response is just "empty" placeholder or whitespace
      const gptText = String(entry.gpt || '').trim();
      const isEmptyPlaceholder = gptText === 'empty' || gptText === '' || gptText === '(empty)' || gptText === '[empty]';
      
      if (!isEmptyPlaceholder) {
        const gptDiv = document.createElement('div');
        gptDiv.className = 'message gpt-message';
        const gptContent = document.createElement('div');
        gptContent.className = 'message-content';
        // OPTIMIZED: Use cached markdown parsing
        gptContent.innerHTML = parseMarkdownWithCache(entry.gpt);
        gptDiv.appendChild(gptContent);
        fragment.appendChild(gptDiv);
      }
    }
  }
  const cacheLoader = chatMessages.querySelector('.cache-loader');
  if (cacheLoader) {
    chatMessages.insertBefore(fragment, cacheLoader.nextSibling);
  } else {
    chatMessages.insertBefore(fragment, chatMessages.firstChild);
  }
  if (newStartIndex === 0 && cacheLoader) {
    cacheLoader.remove();
  }
  const newScrollHeight = chatMessages.scrollHeight;
  const heightDifference = newScrollHeight - oldScrollHeight;
  chatMessages.scrollTop = oldScrollTop + heightDifference;
  window.renderedStartIndex = newStartIndex;
  
  // OPTIMIZED: Only attach listeners to new code blocks
  attachListenersToNewCodeBlocks(fragment);
  
  window.isLoadingMore = false;
  await fs.appendFile(logPath, `[${new Date().toISOString()}] Loaded ${messagesToLoad} cached message pairs\n`);
}

module.exports = { startCacheManager, cacheOldMessages, loadCachedMessages };
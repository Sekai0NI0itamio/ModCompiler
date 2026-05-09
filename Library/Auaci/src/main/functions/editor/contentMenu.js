// src/main/functions/editor/contentMenu.js
const { saveAllOption } = require('./contextMenuOptions/saveAll');
const { closeAllSavedOption } = require('./contextMenuOptions/closeAllSaved');
// New: close all option
const { closeAllOption } = require('./contextMenuOptions/closeAll');

function createMenuItem(text, onClick) {
  const item = document.createElement('button');
  item.className = 'tabs-menu-item';
  item.type = 'button';
  item.textContent = text;
  item.addEventListener('click', async (e) => {
    e.stopPropagation();
    try {
      await onClick();
    } catch (err) {
      console.error('Menu action error:', err);
    }
    closeMenu();
  });
  return item;
}

let menuEl = null;
let wrapperEl = null;
let moreBtn = null;

/**
 * Position the menu so it appears below the button in viewport coordinates.
 * The menu is appended to document.body and uses position: fixed.
 */
function positionMenuBelowButton() {
  if (!menuEl || !moreBtn) return;

  // Make menu visible (but hidden for measurement) if needed
  menuEl.style.visibility = 'hidden';
  menuEl.classList.add('open');

  const btnRect = moreBtn.getBoundingClientRect();
  const menuRect = menuEl.getBoundingClientRect();

  // Compute left so menu aligns with the button's left edge
  let left = btnRect.left;

  // If menu would overflow right edge, shift left
  const viewportRight = window.innerWidth;
  let menuRight = left + menuRect.width;
  if (menuRight > viewportRight - 8) { // small margin
    left -= (menuRight - (viewportRight - 8));
  }

  // Ensure menu doesn't go off the left edge
  if (left < 8) left = 8;

  // Place menu right below the button (leave a small gap)
  const top = btnRect.bottom + 6; // 6px gap

  menuEl.style.left = `${Math.round(left)}px`;
  menuEl.style.top = `${Math.round(top)}px`;

  // Restore visibility
  menuEl.style.visibility = '';
}

/**
 * Open the menu and set up outside click handling.
 */
function openMenu() {
  if (!menuEl || !moreBtn) return;
  positionMenuBelowButton();
  menuEl.classList.add('open');
  document.addEventListener('click', outsideClickHandler);
  if (moreBtn) moreBtn.setAttribute('aria-expanded', 'true');
}

/**
 * Close the menu and remove handlers.
 */
function closeMenu() {
  if (!menuEl || !moreBtn) return;
  menuEl.classList.remove('open');
  menuEl.style.left = '';
  menuEl.style.top = '';
  document.removeEventListener('click', outsideClickHandler);
  if (moreBtn) moreBtn.setAttribute('aria-expanded', 'false');
}

/**
 * Clicks outside the menu AND the more button should close the menu.
 */
function outsideClickHandler(e) {
  if (!menuEl || !moreBtn) return;
  if (!menuEl.contains(e.target) && !moreBtn.contains(e.target)) {
    closeMenu();
  }
}

/**
 * Builds the menu UI and wires events.
 * The menu DOM node is appended to document.body so it is not clipped by
 * any parent overflow or stacking contexts.
 */
function initTabsMenu() {
  const tabsEl = document.getElementById('editor-tabs');
  if (!tabsEl) return;

  // If wrapper already created, reuse references
  if (tabsEl.parentElement && tabsEl.parentElement.classList.contains('editor-tabs-wrapper')) {
    wrapperEl = tabsEl.parentElement;
    moreBtn = wrapperEl.querySelector('#tabs-more-btn');
    // If menu already exists and is appended to body, pick it up
    menuEl = document.getElementById('tabs-more-menu');
    if (menuEl && menuEl.parentElement !== document.body) {
      // Move to body to ensure topmost placement
      document.body.appendChild(menuEl);
    }
    return;
  }

  // Create wrapper and move tabs inside it
  wrapperEl = document.createElement('div');
  wrapperEl.className = 'editor-tabs-wrapper';
  const parent = tabsEl.parentNode;
  parent.replaceChild(wrapperEl, tabsEl);
  wrapperEl.appendChild(tabsEl);

  // Create the more button appended inside the wrapper (keeps layout)
  moreBtn = document.createElement('button');
  moreBtn.id = 'tabs-more-btn';
  moreBtn.type = 'button';
  moreBtn.title = 'More';
  moreBtn.setAttribute('aria-expanded', 'false');
  moreBtn.textContent = '⋯';
  wrapperEl.appendChild(moreBtn);

  // Create menu container and append it to body for topmost stacking
  menuEl = document.createElement('div');
  menuEl.id = 'tabs-more-menu';
  menuEl.setAttribute('role', 'menu');
  // Append to body to avoid clipping / stacking context issues
  document.body.appendChild(menuEl);

  // Populate menu with options
  // New: Create new file opens an untitled buffer in the editor
  const newFileBtn = createMenuItem('Create new file', async () => {
    try {
      const tabs = require('./tabManagement');
      if (tabs && typeof tabs.createUntitledTab === 'function') {
        tabs.createUntitledTab('Untitled');
      }
    } catch (err) {
      console.error('Failed to create new file:', err);
    }
  });

  const saveAllBtn = createMenuItem('Save all', saveAllOption);
  const closeAllSavedBtn = createMenuItem('Close all saved', closeAllSavedOption);
  const closeAllBtn = createMenuItem('Close all', closeAllOption); // new

  menuEl.appendChild(newFileBtn);
  menuEl.appendChild(saveAllBtn);
  menuEl.appendChild(closeAllSavedBtn);
  menuEl.appendChild(closeAllBtn);

  // Toggle menu on button click
  moreBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const opened = menuEl.classList.contains('open');
    if (opened) {
      closeMenu();
    } else {
      openMenu();
    }
  });

  // Reposition menu on window resize/scroll so it remains under the button
  window.addEventListener('resize', () => {
    if (menuEl.classList.contains('open')) positionMenuBelowButton();
  });
  window.addEventListener('scroll', () => {
    if (menuEl.classList.contains('open')) positionMenuBelowButton();
  }, true); // capture so scrolling ancestors trigger reposition

  // Close menu on Escape
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      closeMenu();
    }
  });
}

module.exports = { initTabsMenu };
// src/main/functions/chat/helpers/smoothScroll.js
// Small utility for smooth scrolling (vertical and horizontal) with requestAnimationFrame.
// Exports:
//   - smoothScrollTo(element, target, { axis = 'y', duration = 220 })
//   - scrollElementIntoView(container, child, { axis = 'x', align = 'center', duration = 220 })

function easeOutCubic(t) {
  return 1 - Math.pow(1 - t, 3);
}

function _cancelExisting(el, axis) {
  if (!el || !el.__auaciScrolls) return;
  const s = el.__auaciScrolls[axis];
  if (s && s.rafId) cancelAnimationFrame(s.rafId);
  if (el.__auaciScrolls) delete el.__auaciScrolls[axis];
}

/**
 * Smoothly animate el.scrollTop or el.scrollLeft to `to`.
 * axis: 'y' (scrollTop) or 'x' (scrollLeft)
 * duration: ms
 */
function smoothScrollTo(el, to, opts = {}) {
  if (!el || typeof to !== 'number') return;
  const axis = opts.axis === 'x' ? 'x' : 'y';
  const duration = (typeof opts.duration === 'number') ? Math.max(0, opts.duration) : 220;

  el.__auaciScrolls = el.__auaciScrolls || {};

  // Cancel previous animation on same axis
  _cancelExisting(el, axis);

  const from = (axis === 'y') ? el.scrollTop : el.scrollLeft;
  const delta = to - from;
  if (Math.abs(delta) < 1) {
    // small delta -> set immediately (no animation)
    if (axis === 'y') el.scrollTop = to;
    else el.scrollLeft = to;
    return;
  }

  const startTime = performance.now();
  const state = { from, to, duration, startTime, rafId: null };
  el.__auaciScrolls[axis] = state;

  function step(now) {
    const elapsed = Math.max(0, now - state.startTime);
    const t = Math.min(1, elapsed / state.duration);
    const eased = easeOutCubic(t);
    const val = state.from + (state.to - state.from) * eased;
    if (axis === 'y') el.scrollTop = val;
    else el.scrollLeft = val;

    if (t < 1) {
      state.rafId = requestAnimationFrame(step);
    } else {
      // done
      if (el.__auaciScrolls) delete el.__auaciScrolls[axis];
    }
  }

  state.rafId = requestAnimationFrame(step);
}

/**
 * Ensure `child` is visible inside `container` by smoothly scrolling container horizontally or vertically.
 * axis: 'x' (horizontal tabs) or 'y' (vertical messages)
 * align: 'center' | 'start' | 'end'
 */
function scrollElementIntoView(container, child, opts = {}) {
  if (!container || !child) return;
  const axis = opts.axis === 'y' ? 'y' : 'x';
  const align = opts.align || 'center';
  const duration = (typeof opts.duration === 'number') ? opts.duration : 220;

  if (axis === 'x') {
    // compute child's left relative to container.scrollLeft
    // Use offsetLeft to compute position inside container (works if child is direct descendant or relatively positioned)
    const childLeft = child.offsetLeft;
    const childWidth = child.offsetWidth;
    const containerWidth = container.clientWidth;

    let target;
    if (align === 'center') {
      target = Math.round(childLeft - (containerWidth - childWidth) / 2);
    } else if (align === 'start') {
      target = childLeft;
    } else { // end
      target = Math.round(childLeft - containerWidth + childWidth);
    }

    target = Math.max(0, Math.min(target, container.scrollWidth - containerWidth));
    smoothScrollTo(container, target, { axis: 'x', duration });
  } else {
    // vertical
    const childTop = child.offsetTop;
    const childHeight = child.offsetHeight;
    const containerHeight = container.clientHeight;

    let target;
    if (align === 'center') {
      target = Math.round(childTop - (containerHeight - childHeight) / 2);
    } else if (align === 'start') {
      target = childTop;
    } else {
      target = Math.round(childTop - containerHeight + childHeight);
    }

    target = Math.max(0, Math.min(target, container.scrollHeight - containerHeight));
    smoothScrollTo(container, target, { axis: 'y', duration });
  }
}

module.exports = { smoothScrollTo, scrollElementIntoView };
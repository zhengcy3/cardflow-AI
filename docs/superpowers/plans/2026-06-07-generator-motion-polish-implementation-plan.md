# Generator Motion Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a technology-fluid animated background and a two-pass title highlight sweep to the existing CardFlow AI generator.

**Architecture:** Add one presentational background container with three inert light layers to the Vue template. Keep all visual behavior in `global.css`, using transform and opacity keyframes, responsive reductions, and the existing reduced-motion media query. Remove the temporary comparison page after validation.

**Tech Stack:** Vue 3, TypeScript, Vite, CSS animations

---

### Task 1: Add ambient motion layers and title sweep

**Files:**
- Modify: `apps/web/src/App.vue:146`
- Modify: `apps/web/src/styles/global.css:24-62`
- Modify: `apps/web/src/styles/global.css:206-219`

- [ ] **Step 1: Add the decorative Vue markup**

Insert before `<main class="page">`:

```vue
<div class="ambient-motion" aria-hidden="true">
  <i class="ambient-light ambient-light-violet" />
  <i class="ambient-light ambient-light-rose" />
  <i class="ambient-light ambient-light-cyan" />
</div>
```

Do not add state, refs, lifecycle hooks, or event handlers.

- [ ] **Step 2: Add the ambient layer foundation**

Add fixed, non-interactive CSS:

```css
.ambient-motion {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.ambient-light {
  position: absolute;
  border-radius: 50%;
  will-change: transform, opacity;
}
```

Keep `.page`, `.bottom-bar`, and `.preview-modal` above this layer.

- [ ] **Step 3: Style the three fluid lights**

Use oversized radial-gradient elements:

```css
.ambient-light-violet {
  width: 54vw;
  height: 44vw;
  min-width: 680px;
  min-height: 560px;
  left: -18vw;
  top: -18vw;
  background: radial-gradient(circle, rgba(98, 73, 208, 0.42), transparent 68%);
  animation: ambient-violet 9s ease-in-out infinite alternate;
}

.ambient-light-rose {
  width: 52vw;
  height: 42vw;
  min-width: 660px;
  min-height: 540px;
  right: -20vw;
  top: -10vw;
  background: radial-gradient(circle, rgba(203, 64, 100, 0.34), transparent 69%);
  animation: ambient-rose 11s ease-in-out infinite alternate;
}

.ambient-light-cyan {
  width: 40vw;
  height: 36vw;
  min-width: 520px;
  min-height: 470px;
  left: 22vw;
  top: 34vh;
  background: radial-gradient(circle, rgba(52, 190, 183, 0.2), transparent 70%);
  animation: ambient-cyan 13s ease-in-out infinite alternate;
}
```

Do not use CSS `filter`.

- [ ] **Step 4: Add transform-and-opacity keyframes**

Create three independent keyframes. Each keyframe may only animate `transform` and `opacity`.

```css
@keyframes ambient-violet {
  to {
    transform: translate3d(12vw, 8vh, 0) scale(1.14);
    opacity: 0.78;
  }
}

@keyframes ambient-rose {
  to {
    transform: translate3d(-11vw, 9vh, 0) scale(0.94);
    opacity: 0.8;
  }
}

@keyframes ambient-cyan {
  to {
    transform: translate3d(8vw, -9vh, 0) scale(1.16);
    opacity: 0.72;
  }
}
```

- [ ] **Step 5: Animate the highlighted title twice**

Update `h1 span`:

```css
h1 span {
  display: inline-block;
  background:
    linear-gradient(
      100deg,
      #fff8e7 0%,
      #d6ad5c 24%,
      #ffffff 43%,
      #d6ad5c 57%,
      #f08bb3 76%,
      #76e4f7 100%
    );
  background-size: 240% 100%;
  background-position: 100% 0;
  background-clip: text;
  color: transparent;
  animation: title-shine 4s ease-in-out 2;
}

@keyframes title-shine {
  0%,
  16% {
    background-position: 100% 0;
  }
  82%,
  100% {
    background-position: -100% 0;
  }
}
```

The animation must not change layout, opacity, or transform.

- [ ] **Step 6: Run build and formatting checks**

From `apps/web`:

```bash
/Users/zhengchenyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/vue-tsc/bin/vue-tsc.js -b
/Users/zhengchenyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/vite/bin/vite.js build
```

From the repository root:

```bash
git diff --check -- apps/web/src/App.vue apps/web/src/styles/global.css
```

Expected: all commands exit with code `0`.

### Task 2: Add responsive fallbacks and remove the comparison page

**Files:**
- Modify: `apps/web/src/styles/global.css:956-1030`
- Delete: `apps/web/public/motion-direction.html`

- [ ] **Step 1: Reduce motion cost on mobile**

Inside `@media (max-width: 560px)`:

```css
.ambient-light {
  opacity: 0.72;
}

.ambient-light-violet {
  animation-duration: 15s;
}

.ambient-light-rose {
  animation-duration: 17s;
}

.ambient-light-cyan {
  display: none;
}
```

Keep the existing mobile `filter`-free and `backdrop-filter: none` behavior.

- [ ] **Step 2: Add a complete reduced-motion fallback**

Inside the existing `@media (prefers-reduced-motion: reduce)`:

```css
.ambient-light,
h1 span {
  animation: none !important;
}

.ambient-light-violet,
.ambient-light-rose,
.ambient-light-cyan {
  transform: none;
}

h1 span {
  background-position: 50% 0;
}
```

The static background and title gradient remain visible.

- [ ] **Step 3: Delete the temporary comparison page**

Delete:

```text
apps/web/public/motion-direction.html
```

- [ ] **Step 4: Verify layout metrics**

Check `http://localhost:5173/` at:

- `1280 × 900`.
- `820 × 900`.
- `390 × 844`.

For every size, verify:

- `document.documentElement.scrollWidth === document.documentElement.clientWidth`.
- `.ambient-motion` has `pointer-events: none`.
- `.page`, `.bottom-bar`, and `.preview-modal` remain above the ambient layer.

At mobile size, verify `.ambient-light-cyan` has `display: none`.

- [ ] **Step 5: Verify animation definitions**

Read computed styles after reload:

- Violet duration is `9s` at desktop and `15s` at mobile.
- Rose duration is `11s` at desktop and `17s` at mobile.
- Cyan duration is `13s` at desktop and hidden at mobile.
- Title animation iteration count is `2`.

- [ ] **Step 6: Run final verification**

From `apps/web`:

```bash
/Users/zhengchenyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/vue-tsc/bin/vue-tsc.js -b
/Users/zhengchenyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/vite/bin/vite.js build
```

From the repository root:

```bash
git diff --check
git status --short
```

Expected:

- Build exits with code `0`.
- No whitespace errors.
- `apps/web/public/motion-direction.html` is absent.
- No unrelated source files were modified by this motion task.

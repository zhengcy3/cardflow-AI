# Generator Visual Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine the existing CardFlow AI generator into a restrained editorial-dark workbench without changing its business behavior or information architecture.

**Architecture:** Keep the current Vue template and state flow intact. Implement the visual system primarily through CSS custom properties and scoped component selectors in `global.css`, adding template classes only if a stable styling hook is missing. Verify the result with the production build and browser checks at desktop and mobile sizes.

**Tech Stack:** Vue 3, TypeScript, Vite, CSS, Codex in-app browser

---

### Task 1: Establish the restrained editorial-dark foundation

**Files:**
- Modify: `apps/web/src/styles/global.css:1-168`

- [ ] **Step 1: Capture the current desktop visual baseline**

Open `http://localhost:5173/` in the in-app browser at its default desktop viewport and capture a screenshot.

Expected: the current purple-red background, floating navigation, hero, and top of the generator workspace are visible.

- [ ] **Step 2: Replace the global visual tokens**

Update `:root` with explicit surface, border, text, accent, radius, and shadow levels:

```css
:root {
  color-scheme: dark;
  --bg: #09090b;
  --surface-1: rgba(17, 17, 21, 0.88);
  --surface-2: rgba(24, 24, 29, 0.82);
  --surface-3: rgba(255, 255, 255, 0.045);
  --line: rgba(255, 255, 255, 0.09);
  --line-strong: rgba(255, 255, 255, 0.16);
  --text: #f3efe7;
  --soft: #c6c2ba;
  --muted: #8e8d96;
  --accent: #d6ad5c;
  --accent-soft: rgba(214, 173, 92, 0.12);
  --green: #42d6a4;
  --danger: #d77979;
  --radius-control: 10px;
  --radius-panel: 18px;
  --shadow-panel: 0 18px 48px rgba(0, 0, 0, 0.3);
  --shadow-float: 0 24px 72px rgba(0, 0, 0, 0.46);
}
```

Remove or replace references to superseded tokens such as `--panel`, `--field`, `--amber`, and the single shared `--shadow`.

- [ ] **Step 3: Refine the page background**

Replace the highly saturated body gradient with:

```css
body {
  position: relative;
  background:
    radial-gradient(900px 560px at -8% -12%, rgba(91, 72, 184, 0.34), transparent 68%),
    radial-gradient(880px 520px at 108% -10%, rgba(158, 58, 73, 0.28), transparent 70%),
    linear-gradient(180deg, #15111c 0%, #0d0b10 48%, var(--bg) 100%);
  background-attachment: fixed;
}

body::before {
  content: "";
  position: fixed;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(rgba(255, 255, 255, 0.012) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.01) 1px, transparent 1px);
  background-size: 72px 72px;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.45), transparent 72%);
}
```

Ensure `.page`, `.bottom-bar`, and `.preview-modal` remain above the decorative layer.

- [ ] **Step 4: Refine navigation and hero hierarchy**

Adjust the existing selectors so that:

```css
.page {
  padding-top: 22px;
}

.topbar {
  margin-bottom: 44px;
  border-color: var(--line);
  background: rgba(16, 14, 18, 0.82);
  box-shadow: var(--shadow-float);
}

.headline {
  margin-bottom: 38px;
}

h1 {
  font-size: clamp(40px, 4.6vw, 66px);
  letter-spacing: -0.045em;
}
```

Keep the existing hero copy and layout.

- [ ] **Step 5: Run the build**

Run:

```bash
npm run build:web
```

Expected: exit code `0`, with Vue TypeScript and Vite build completing successfully.

- [ ] **Step 6: Commit the foundation**

```bash
git add apps/web/src/styles/global.css
git commit -m "style: refine generator visual foundation"
```

### Task 2: Refine panels, form controls, and selectable cards

**Files:**
- Modify: `apps/web/src/styles/global.css:169-438`

- [ ] **Step 1: Differentiate surface levels**

Update primary panels:

```css
.panel {
  border-color: var(--line);
  border-radius: var(--radius-panel);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.018), transparent 34%),
    var(--surface-1);
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(18px);
}

.panel.pad {
  padding: 24px;
}
```

Reduce `.left-stack` and `.right-stack` gaps to `16px`.

- [ ] **Step 2: Improve section typography**

Set section titles and metadata to distinct levels:

```css
.panel-title b,
.preview-header > span:first-child {
  color: var(--soft);
  font-size: 14px;
  font-weight: 760;
}

.panel-title > span,
.preview-header > span:last-child {
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
```

- [ ] **Step 3: Refine inputs and focus states**

Apply a consistent nested-control treatment:

```css
input,
textarea {
  border-color: rgba(255, 255, 255, 0.055);
  background: rgba(255, 255, 255, 0.035);
  transition: border-color 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
}

input:hover,
textarea:hover {
  border-color: rgba(255, 255, 255, 0.11);
  background: rgba(255, 255, 255, 0.045);
}

input:focus,
textarea:focus {
  border-color: rgba(214, 173, 92, 0.56);
  background: rgba(255, 255, 255, 0.052);
  box-shadow: 0 0 0 3px rgba(214, 173, 92, 0.1);
}
```

Reduce `.text-input` from `30px` to approximately `27px` and preserve its strong hierarchy.

- [ ] **Step 4: Standardize interactive states**

Add shared transitions and keyboard focus treatment:

```css
.nav-item,
.status-pill,
.user-pill,
.menu-button,
.segment,
.chip,
.engine-card,
.format-card,
.preview-link,
.page-links button,
.delete-button,
.generate-button {
  transition:
    color 160ms ease,
    border-color 160ms ease,
    background-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

button:focus-visible,
input:focus-visible,
textarea:focus-visible {
  outline: 2px solid rgba(214, 173, 92, 0.75);
  outline-offset: 3px;
}
```

Add subtle hover states using border and background changes. Limit transforms to prominent clickable cards and the generation button.

- [ ] **Step 5: Refine selected and disabled states**

Use the warm accent consistently:

```css
.engine-card.active,
.format-card.active {
  border-color: rgba(214, 173, 92, 0.58);
  background:
    linear-gradient(180deg, rgba(214, 173, 92, 0.12), rgba(214, 173, 92, 0.055));
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.035);
}
```

Keep `.segment.active` and `.chip.active` light for clear selection, but change the light fill to the shared warm off-white. Ensure disabled controls do not respond to hover transforms.

- [ ] **Step 6: Run the build**

Run:

```bash
npm run build:web
```

Expected: exit code `0`.

- [ ] **Step 7: Commit component refinements**

```bash
git add apps/web/src/styles/global.css
git commit -m "style: polish generator controls and panels"
```

### Task 3: Strengthen preview hierarchy and quiet recent works

**Files:**
- Modify: `apps/web/src/styles/global.css:439-722`
- Modify only if needed: `apps/web/src/App.vue:283-335`

- [ ] **Step 1: Make the preview stage the right-column anchor**

Style the existing preview container:

```css
.preview-card {
  position: relative;
  overflow: hidden;
  border-color: rgba(255, 255, 255, 0.13);
  background:
    radial-gradient(420px 260px at 50% 32%, rgba(214, 173, 92, 0.07), transparent 72%),
    var(--surface-1);
}

.preview-stage {
  margin: 0 14px 14px;
  padding: 24px 18px 26px;
  min-height: 360px;
  border: 1px solid rgba(255, 255, 255, 0.055);
  border-radius: 14px;
  background:
    radial-gradient(circle at 50% 38%, rgba(255, 255, 255, 0.04), transparent 58%),
    rgba(0, 0, 0, 0.16);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.025);
}
```

Preserve all current image and modal behavior.

- [ ] **Step 2: Refine preview interaction**

Reduce the generated-image shadow spread and add a small hover lift:

```css
.generated-preview {
  box-shadow: 0 18px 42px rgba(0, 0, 0, 0.42);
  transition: transform 180ms ease, box-shadow 180ms ease;
}

.generated-preview:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 48px rgba(0, 0, 0, 0.48);
}
```

Apply the same cleaner shadow to `.mock-card` without changing its output-card design.

- [ ] **Step 3: Reduce recent-work visual weight**

Update `.work-item`, `.work-preview-button`, and `.delete-button` so list items use quieter backgrounds, long titles truncate, status text is smaller, and delete becomes red only on hover or focus.

Required title behavior:

```css
.work-preview-button span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

- [ ] **Step 4: Refine the fixed action bar**

Use the strongest floating surface treatment for `.bottom-bar`. Make `.generate-button` use warm off-white, add a restrained hover lift, and keep the disabled state stable:

```css
.generate-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 28px rgba(238, 232, 223, 0.12);
}
```

- [ ] **Step 5: Add reduced-motion handling**

Add:

```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}
```

- [ ] **Step 6: Run the build**

Run:

```bash
npm run build:web
```

Expected: exit code `0`.

- [ ] **Step 7: Commit hierarchy refinements**

```bash
git add apps/web/src/App.vue apps/web/src/styles/global.css
git commit -m "style: strengthen generator preview hierarchy"
```

### Task 4: Verify desktop and responsive presentation

**Files:**
- Modify if required by verification: `apps/web/src/styles/global.css`

- [ ] **Step 1: Verify the desktop page**

Open or reload `http://localhost:5173/` at the default desktop viewport.

Check:

- The hero hands attention to the workspace quickly.
- Background glows do not reduce form readability.
- Input, output settings, preview, and recent works have distinct visual weight.
- The live preview is the right-column focal point.
- The fixed action bar does not cover active controls.

- [ ] **Step 2: Verify keyboard-visible states**

Use keyboard tab navigation through the top navigation, form controls, output cards, preview controls, recent works, and generate button.

Expected: every interactive element has a clearly visible focus indicator and no focus outline is clipped.

- [ ] **Step 3: Verify tablet width**

Set the in-app browser viewport to approximately `820 × 900`, reload the page, and check:

- Workspace stacks to one column.
- Output cards remain readable.
- Preview width does not overflow.
- Fixed action bar remains usable.

- [ ] **Step 4: Verify mobile width**

Set the viewport to approximately `390 × 844`, reload the page, and check:

- Navigation content does not force horizontal overflow.
- Heading and inputs remain readable.
- Engine and format cards stack correctly.
- The fixed action bar does not obscure the last panel.

- [ ] **Step 5: Correct responsive issues**

If verification finds clipping or overflow, adjust only the existing media queries. Prefer spacing and width changes over markup changes.

- [ ] **Step 6: Run final verification**

Run:

```bash
npm run build:web
git diff --check
```

Expected:

- Build exits with code `0`.
- `git diff --check` exits with code `0`.

- [ ] **Step 7: Review the final diff**

Run:

```bash
git diff -- apps/web/src/App.vue apps/web/src/styles/global.css
```

Confirm:

- No Vue state or API behavior changed.
- No unrelated user edits were removed.
- Changes remain within the approved visual-polish scope.

- [ ] **Step 8: Commit verification fixes**

If Task 4 changed files:

```bash
git add apps/web/src/App.vue apps/web/src/styles/global.css
git commit -m "fix: tune generator responsive polish"
```

If Task 4 required no file changes, do not create an empty commit.

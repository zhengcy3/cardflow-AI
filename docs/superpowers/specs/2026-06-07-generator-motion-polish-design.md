# CardFlow AI Generator Motion Polish Design

Date: 2026-06-07
Status: Approved design, pending implementation plan

## 1. Goal

Add a more technological sense of motion to the existing editorial-dark generator without changing its layout, controls, or business behavior.

The motion should make the page feel alive while keeping the content workspace readable during long sessions.

## 2. Confirmed Direction

The selected direction is **Technology Fluid**:

- Three slow ambient light layers.
- Violet, rose, and restrained cyan-green colors.
- Independent drifting and scaling cycles between 9 and 13 seconds.
- A short highlight sweep across the phrase `高转化`.
- The title sweep runs twice after page entry, then stops.

The effect must remain secondary to the generator workspace.

## 3. Scope

### Included

- Animated ambient background layers.
- Entry animation for the highlighted title phrase.
- Desktop and mobile motion variants.
- Reduced-motion fallback.
- Removal of the temporary motion comparison page after implementation.

### Excluded

- Animation libraries.
- Canvas, WebGL, video, or image assets.
- Scroll-linked effects.
- Cursor-following effects.
- Continuous title flashing.
- Changes to the generator workflow or component structure.

## 4. Background Motion

### 4.1 Layer Structure

Add a decorative background container with three non-interactive light layers:

1. Violet light near the upper-left.
2. Rose light near the upper-right.
3. Cyan-green support light lower in the composition.

The layers must:

- Sit behind `.page`, `.bottom-bar`, and `.preview-modal`.
- Use `pointer-events: none`.
- Never create horizontal overflow.
- Use oversized radial-gradient shapes with soft edges.
- Preserve the current near-black base background.

### 4.2 Animation

Each layer uses an independent animation:

- Violet: approximately 9 seconds.
- Rose: approximately 11 seconds.
- Cyan-green: approximately 13 seconds.

Animation properties are limited to:

- `transform`.
- `opacity`.

Movement combines gentle translation and small scale changes. The layers must not rotate rapidly, pulse sharply, or cross the entire viewport.

### 4.3 Visual Strength

The light layers should be clearly visible near the hero but fade behind the workspace.

They must not:

- Reduce text contrast.
- Create bright color directly behind form values.
- Make panel borders difficult to perceive.
- Resemble a full-page neon wash.

## 5. Title Highlight Motion

The existing `高转化` span remains the only animated title segment.

Use:

- A warm-gold, white, and restrained rose/cyan gradient.
- A wide gradient background that moves horizontally.
- Approximately 4 seconds per sweep.
- Exactly two iterations after page entry.
- A final resting state that remains readable and visually balanced.

The full heading must not blink, move, scale, or change layout.

## 6. Mobile Behavior

At `max-width: 560px`:

- Use only the violet and rose layers.
- Hide the cyan-green support layer.
- Reduce opacity.
- Slow the motion.
- Do not apply blur filters.
- Keep the existing mobile performance reductions for panels and navigation.

The mobile page must remain free of horizontal overflow.

## 7. Reduced Motion

Under `prefers-reduced-motion: reduce`:

- Disable all ambient background animations.
- Disable the title sweep.
- Render a static gradient background and static title gradient.

Content and contrast must remain unchanged.

## 8. Implementation Boundaries

Expected files:

- `apps/web/src/App.vue`: add one decorative background wrapper and three empty decorative layers if pseudo-elements are insufficient.
- `apps/web/src/styles/global.css`: implement layer appearance, keyframes, title sweep, responsive rules, and reduced-motion behavior.
- `apps/web/public/motion-direction.html`: delete the temporary comparison page.

Do not change:

- Vue state.
- API calls.
- Generation behavior.
- Preview behavior.
- History behavior.

## 9. Validation

Implementation is complete when:

- The background visibly flows at desktop size without distracting from form content.
- `高转化` sweeps twice after reload and then remains static.
- No decorative layer captures pointer input.
- Desktop, tablet, and mobile layouts have no horizontal overflow.
- Mobile uses two lower-cost light layers.
- Reduced-motion produces a static presentation.
- The temporary comparison page is removed.
- The Vue TypeScript check and Vite production build pass.
- `git diff --check` passes.

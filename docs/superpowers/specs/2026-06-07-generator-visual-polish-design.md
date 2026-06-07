# CardFlow AI Generator Visual Polish Design

Date: 2026-06-07
Status: Approved design, pending implementation plan

## 1. Goal

Improve the perceived quality of the existing generator page without replacing its information architecture or turning it into a new product layout.

The result should feel:

- Dark, restrained, and editorial rather than neon-heavy.
- More precise in spacing, borders, shadows, and control states.
- Clearly organized around the content input and card preview.
- Slightly more compact on desktop while preserving the current workflow.

This is a visual refinement of the current page, not a redesign.

## 2. Confirmed Direction

The selected visual direction is **Editorial Dark** with a light influence from the **Compact Workbench** layout.

The current structure remains:

```text
Top navigation
Hero introduction
Two-column generator workspace
├── Content input
├── Output settings
├── Live preview
└── Recent works
Fixed generation action bar
```

The compact-workbench influence is limited to reducing unnecessary vertical space and making the workflow easier to understand within a typical desktop viewport.

## 3. Scope

### 3.1 Included

- Background color and ambient lighting refinement.
- Panel material, border, radius, and shadow refinement.
- Typography hierarchy and spacing rhythm.
- Input, textarea, segmented control, chip, card, and button states.
- Stronger visual emphasis for the live preview.
- Lower visual emphasis for the recent-works list.
- Small desktop spacing reductions.
- Responsive review at existing breakpoints.
- Reduced-motion support for any added transitions.

### 3.2 Excluded

- Business-logic changes.
- API or data-model changes.
- New generation modes or templates.
- New navigation behavior.
- Component-system migration.
- Large template or markup restructuring.
- Drag-and-drop editing.
- New animation libraries.

## 4. Visual System

### 4.1 Background

The existing purple-red gradient remains recognizable but becomes darker and less saturated.

Use:

- A near-black neutral base.
- One restrained violet glow near the upper-left.
- One restrained warm red or rose glow near the upper-right.
- A very subtle grid or grain layer for depth.
- No bright full-page color wash.

The background must support the workspace rather than compete with it.

### 4.2 Surfaces

Create three surface levels:

1. **Primary workspace panels**: dark translucent surface, clear fine border, moderate shadow.
2. **Nested controls and list items**: quieter solid or nearly solid dark surface.
3. **Floating navigation and action bar**: slightly stronger blur and elevation than workspace panels.

Not every element should use the same shadow. Nested controls should rely primarily on border and tonal contrast.

### 4.3 Borders and Radius

Use a consistent radius scale:

- Small controls: 9-11 px.
- Cards and panels: 16-18 px.
- Pills and floating bars: fully rounded.

Default borders should be subtle neutral white. Active borders use a restrained warm-gold tone. Hover and focus states may increase border brightness without adding strong glow.

### 4.4 Color

Primary palette:

- Text: warm off-white.
- Secondary text: neutral cool gray.
- Accent: muted warm gold.
- Success/status: green, used only for system status.
- Destructive action: muted red, visible mainly on hover or focus.

The current multicolor identity may remain in the logo, but the rest of the interface should not repeat all logo colors.

### 4.5 Typography

Keep the existing system-font stack.

Refine hierarchy by:

- Reducing excessive hero dominance slightly.
- Increasing contrast between section titles and metadata labels.
- Using tighter letter spacing on large headings.
- Using modest positive letter spacing for small English metadata labels.
- Keeping form values readable and bold without making every field look like a headline.

## 5. Layout and Hierarchy

### 5.1 Hero

Keep the hero and its current message, but reduce the space below the navigation and around the headline.

The hero should introduce the tool, then quickly hand attention to the workspace. It must not behave like a marketing landing-page hero.

### 5.2 Main Workspace

Keep the current two-column structure and right-column width.

Refinements:

- Slightly reduce panel padding and vertical gaps on desktop.
- Keep the content-input panel as the primary left-side surface.
- Keep output settings directly below it.
- Preserve current responsive stacking behavior.

No substantial template restructuring is required unless a tiny wrapper or class improves styling clarity.

### 5.3 Live Preview

The live preview becomes the visual anchor of the right column.

Use:

- A subtly differentiated stage background.
- More intentional spacing around the generated card.
- A cleaner image shadow with less blur spread.
- A quiet highlight or inset border around the preview stage.
- Clear hover and focus feedback for opening the large preview.

The generated card itself remains unchanged because it represents output content, not application chrome.

### 5.4 Recent Works

Recent works stays visible but becomes quieter than the preview.

Use:

- Lower-contrast item backgrounds.
- Better title truncation and status hierarchy.
- A subdued delete control that becomes clearer on hover and keyboard focus.
- Compact item spacing.

## 6. Component States

All interactive controls need consistent states:

- Default.
- Hover.
- Keyboard focus-visible.
- Active or selected.
- Disabled.

Focus-visible styling must be clearly perceivable and should use a warm accent ring or border without relying only on color changes inside the control.

Transitions should be limited to color, border, background, opacity, and small transforms. Respect `prefers-reduced-motion`.

## 7. Implementation Boundaries

Most changes should remain in:

- `apps/web/src/styles/global.css`

Small class additions in:

- `apps/web/src/App.vue`

are acceptable only when required to create a clear visual hierarchy or accessible state.

Do not change the existing Vue state, API calls, generation workflow, modal behavior, or project-history behavior as part of this work.

## 8. Responsive Behavior

At the existing tablet and mobile breakpoints:

- Preserve single-column stacking.
- Avoid fixed heights that clip form content.
- Keep controls large enough for touch use.
- Ensure the fixed action bar does not obscure content.
- Reduce decorative effects when screen space is limited.

Desktop compacting must not reduce mobile readability.

## 9. Validation

Implementation is complete when:

- The generator still presents the same content and controls.
- Existing interactions continue to work.
- The workspace has a clearer primary, secondary, and tertiary hierarchy.
- Background lighting no longer competes with form content.
- Inputs and selectable cards have consistent hover, focus, active, and disabled states.
- The live preview is the strongest visual element in the right column.
- The page remains usable at desktop, tablet, and mobile widths.
- `npm run build:web` passes.
- The page is visually checked in the in-app browser at desktop and mobile widths.

## 10. Non-Goals

This work does not attempt to define a reusable design system for future management pages. It establishes a coherent visual treatment for the current generator page only. Shared tokens may be introduced where they directly reduce inconsistency, but no generalized component library should be created.

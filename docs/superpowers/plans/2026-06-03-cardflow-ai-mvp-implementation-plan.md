# CardFlow AI MVP Implementation Plan

Date: 2026-06-03
Status: Draft for implementation review
Related PRD: `docs/superpowers/specs/2026-06-02-ai-card-factory-prd-design.md`
Prototype: `prototypes/ai-card-factory-generator.html`

## 1. Goal

Build the MVP of CardFlow AI: a local-first AI knowledge-card generator that supports topic generation, article generation, structured content review, HTML-template rendering, PNG export, and work history.

The first implementation must prove this core workflow:

```text
User enters topic
→ LLM returns validated structured JSON
→ User reviews/edits content
→ User selects format and template
→ Backend renders HTML
→ Playwright screenshots PNG
→ PNG is saved locally
→ Work appears in history and can be downloaded
```

## 2. Confirmed MVP Stack

Frontend:

- Vue 3
- Vite
- TypeScript
- CSS modules or scoped component styles

Backend:

- Spring Boot
- Java 17 or later
- SQLite
- Local file storage
- Playwright for screenshot rendering

AI:

- One LLM provider first
- Provider abstraction for later DeepSeek, Gemini, OpenAI, or Doubao switching

Deferred infrastructure:

- MySQL is deferred until production deployment.
- Redis is deferred until quota and task throughput require it.
- MinIO or S3-compatible storage is deferred until remote deployment.
- Third-party AI image generation is deferred to V1.
- Hybrid rendering is deferred to V1.

## 3. MVP Scope

Included:

- Immersive AI Generator page based on the approved prototype.
- Topic generation.
- Article generation.
- Article extraction controls: summarize ideas, split into carousel, generate cover copy.
- Page-count controls: automatic, 3 pages, 5 pages, 7 pages.
- Content style controls: professional concise, Xiaohongshu style, knowledge-commerce style.
- Output formats:
  - Xiaohongshu 3:4
  - YouTube cover 16:9
  - Bilibili cover 16:9
  - Douyin cover 9:16
- Rendering mode selector in UI:
  - Precise Card enabled
  - AI Creative Image disabled or marked as coming soon
  - Hybrid Mode disabled or marked as coming soon
- Three built-in HTML templates.
- Structured content review and text editing.
- PNG rendering and local download.
- My Works list.
- Daily quota tracked in SQLite.
- Basic usage records.

Excluded:

- Third-party AI image generation.
- Hybrid AI background plus HTML overlay.
- Online payment.
- Team collaboration.
- Custom template builder.
- Drag-and-drop design editor.
- MinIO, Redis, and MySQL deployment.

## 4. Milestones

### M1. Project Skeleton

Objective:

Create a runnable monorepo-style project with frontend and backend separated clearly.

Tasks:

- Create project layout:

```text
apps/
├── web/
└── api/
storage/
├── outputs/
└── templates/
docs/
prototypes/
```

- Scaffold Vue 3 Vite TypeScript app in `apps/web`.
- Scaffold Spring Boot app in `apps/api`.
- Add backend configuration for SQLite database file at `data/cardflow.db`.
- Add local storage path config for `storage/outputs`.
- Add local development docs.

Acceptance:

- Frontend dev server runs.
- Backend dev server runs.
- Backend can open SQLite database.
- Backend can write a test file under `storage/outputs`.

### M2. Frontend Generator UI

Objective:

Convert the approved static prototype into Vue components.

Suggested components:

```text
GeneratorPage.vue
TopNav.vue
HeroHeader.vue
ContentInputPanel.vue
TopicInputForm.vue
ArticleInputForm.vue
RenderModeSelector.vue
OutputFormatSelector.vue
PreviewPanel.vue
StyleSelector.vue
BottomGenerateBar.vue
```

Tasks:

- Preserve the approved visual direction from `prototypes/ai-card-factory-generator.html`.
- Implement topic/article switching.
- Implement rendering-mode selector with Precise Card enabled and V1 modes disabled.
- Implement output format selector.
- Implement local UI state for form values.
- Implement responsive layout.

Acceptance:

- User can switch between topic generation and article generation.
- User can select output platform format.
- User can see rendering-mode choices.
- Page matches prototype direction on desktop and remains usable on mobile.

### M3. Backend Data Model and APIs

Objective:

Implement durable records for users, templates, projects, pages, generation tasks, and usage.

MVP simplification:

- Authentication is postponed.
- The first implementation uses a single default local user.

SQLite tables:

```text
user
template
card_project
card_page
generate_task
usage_record
```

Core APIs:

```text
GET  /api/health
GET  /api/me

GET  /api/templates
GET  /api/templates/{id}

POST /api/generations/content
POST /api/projects
GET  /api/projects
GET  /api/projects/{id}
PUT  /api/projects/{id}/content
POST /api/projects/{id}/render
GET  /api/tasks/{id}

GET  /api/usage/summary
GET  /api/usage/records
```

Tasks:

- Add migrations or startup schema initialization.
- Seed three built-in templates.
- Implement project create/list/detail/update.
- Implement generation task records.
- Implement daily quota and usage records in SQLite.
- Return local image URLs through backend static file serving.

Acceptance:

- Templates can be listed.
- A project can be created from structured JSON.
- Project content can be updated.
- Usage records can be created and queried.
- Generated local image files can be served by backend.

### M4. LLM Structured Content Generation

Objective:

Generate stable content JSON from topic or article input.

Backend modules:

```text
llm/
├── LlmProvider
├── LlmRequest
├── LlmResponse
├── StructuredContentService
├── JsonRepairService
└── ContentSchemaValidator
```

Supported schemas:

- `single_card`
- `carousel_card`

Tasks:

- Implement provider abstraction.
- Implement one actual provider or a local mock provider if API keys are not ready.
- Build prompts for topic generation.
- Build prompts for article generation.
- Validate JSON schema.
- Repair non-JSON or incomplete JSON once.
- Return field-level warnings for overlong text.

Acceptance:

- Topic input returns valid `single_card` or `carousel_card` JSON.
- Article input returns valid structured content using extraction controls.
- Invalid LLM output is repaired once or fails with clear error.
- Quota is checked before model call and usage is recorded after success.

Current implementation note:

- A `LlmProvider` abstraction and `MockLlmProvider` are in place.
- A real provider can replace the mock without changing the generator API.

### M5. HTML Template Rendering and PNG Export

Objective:

Render validated content JSON into PNG images through HTML templates and Playwright.

Backend modules:

```text
rendering/
├── TemplateRegistry
├── TemplateRenderer
├── HtmlRenderModel
├── PlaywrightScreenshotService
└── LocalFileStorageService
```

Tasks:

- Define template metadata:
  - supported content types
  - supported ratios
  - field limits
  - page limits
- Implement three templates:
  - Xiaohongshu highlight
  - Minimal Apple style
  - Business briefing
- Generate HTML from content JSON and selected template.
- Use Playwright to screenshot HTML.
- Save PNG to `storage/outputs`.
- Update `card_page.image_url` and `card_project.cover_url`.
- Support multiple PNGs for carousel pages.

Acceptance:

- Single-card project renders one PNG.
- Carousel project renders one PNG per page.
- Render failure updates task status and error message.
- Rendered images are visible in project detail and downloadable.

Current implementation note:

- HTML template rendering and Playwright PNG screenshot services are in place.
- Multi-page carousel renders one PNG per page and stores each page in `card_page`.

### M6. My Works and Download Flow

Objective:

Complete the user-facing history and reuse loop.

Tasks:

- Implement My Works page.
- Show title, preview, ratio, template, status, creation time.
- Add search by title.
- Add download action.
- Add delete action.
- Add reopen/edit action.
- Add regenerate-from-existing action if time permits.

Acceptance:

- Generated works appear in history.
- User can preview and download PNG.
- User can delete a work.
- User can reopen a work for text edits.

## 5. Suggested Build Order

Build the thinnest working slice first:

```text
M1 skeleton
→ M2 generator UI shell
→ M3 templates/projects APIs
→ M4 mock LLM structured JSON
→ M5 one Xiaohongshu HTML template render
→ Replace mock LLM with real provider
→ Add article generation
→ Add more formats and templates
→ Add My Works
```

This order avoids blocking the rendering workflow on a real LLM provider.

## 6. Data Model Notes

Use SQLite for MVP.

Recommended file:

```text
data/cardflow.db
```

Recommended local output path:

```text
storage/outputs/{userId}/{projectId}/{pageIndex}.png
```

Use string enums for MVP:

- Project type: `single_card`, `carousel_card`
- Ratio: `xhs_3_4`, `youtube_16_9`, `bilibili_16_9`, `douyin_9_16`
- Render mode: `precise_card`, `ai_creative_image`, `hybrid`
- Task status: `pending`, `running`, `succeeded`, `failed`
- Usage type: `content_generation`, `image_render`

## 7. Frontend State Model

Generator page state:

```ts
type GenerationMode = "topic" | "article";
type RenderMode = "precise_card" | "ai_creative_image" | "hybrid";
type OutputFormat = "xhs_3_4" | "youtube_16_9" | "bilibili_16_9" | "douyin_9_16";

interface GeneratorState {
  generationMode: GenerationMode;
  renderMode: RenderMode;
  outputFormat: OutputFormat;
  templateId: string;
  topicInput: {
    title: string;
    subtitle: string;
    instructions: string;
  };
  articleInput: {
    body: string;
    extractionGoal: "summarize" | "carousel" | "cover_copy";
    pageCount: "auto" | 3 | 5 | 7;
    style: "concise" | "xiaohongshu" | "knowledge_commerce";
  };
}
```

## 8. Verification Plan

Frontend:

- Run TypeScript check.
- Run production build.
- Use browser verification for desktop and mobile layouts.
- Confirm topic/article switching.
- Confirm disabled V1 render modes communicate status clearly.

Backend:

- Run unit tests for schema validation.
- Run unit tests for quota logic.
- Run integration test for project create/update/list.
- Run rendering test with a deterministic template.
- Verify PNG exists on disk after render.

End-to-end:

- Generate a topic card.
- Edit structured content.
- Render Xiaohongshu 3:4 PNG.
- Download PNG.
- Confirm work appears in My Works.

## 9. Risks and Decisions

Risks:

- Playwright may require browser runtime installation.
- LLM JSON may be unstable without strong schema validation and repair.
- Chinese text overflow can break templates if limits are too loose.
- Local file paths need careful URL mapping.
- SQLite is not intended for high-concurrency production usage.

Decisions before coding:

- Choose Java build tool: Maven or Gradle.
- Choose SQLite migration tool or startup schema approach.
- Choose first LLM provider or start with mock provider.
- Decide which LLM provider replaces the mock provider first.

Recommended defaults:

- Maven for Spring Boot.
- Flyway for schema migrations if SQLite support is acceptable in the chosen setup; otherwise use explicit startup SQL for MVP.
- Mock provider first, real provider second.
- Default local user first. Real authentication is added only after the generation loop works.

## 10. Done Definition

MVP implementation is considered done when:

- The app runs locally with one frontend command and one backend command.
- SQLite persists projects, tasks, templates, and usage records.
- Local file storage persists generated PNGs.
- User can generate structured content from topic and article inputs.
- User can render at least one template to PNG through Playwright.
- User can view generated works in history and download images.
- AI Creative Image and Hybrid Mode are clearly marked as future modes if not implemented.

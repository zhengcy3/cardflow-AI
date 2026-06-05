# AI Knowledge Card Factory SaaS PRD Design

Date: 2026-06-02
Status: Draft for user review
Repository note: The current folder is not a Git repository, so this spec is written to disk but not committed.

## 1. Product Summary

### 1.1 Product Name

AI Knowledge Card Factory

Chinese product name: AI 知识卡片工厂

### 1.2 Positioning

AI Knowledge Card Factory is a SaaS product for individual content creators. It helps users turn a topic, opinion, or article into publishable knowledge-card images for Xiaohongshu, with support for both single-page cards and multi-page carousel cards.

The product should not start as a full design editor. The first version focuses on a fast generation workflow: structured AI content, stable templates, simple text editing, image rendering, export, and history.

### 1.3 Product Vision

Let creators who do not know design produce clear, consistent, and high-quality knowledge cards in under 30 seconds.

### 1.4 Core Value

- Lower the design barrier for knowledge-card creation.
- Improve content production speed.
- Help creators create visually consistent posts.
- Convert raw ideas and long articles into shareable visual content.

## 2. Target Users

### 2.1 Primary MVP User

Individual Xiaohongshu and self-media bloggers.

Examples:

- Xiaohongshu bloggers
- Knowledge bloggers
- AI bloggers
- Programmer bloggers
- Personal-growth bloggers

Primary needs:

- Quickly create Xiaohongshu-style cover cards and carousel cards.
- Convert one idea into a structured post.
- Improve posting frequency.
- Avoid spending time in general-purpose design tools.

### 2.2 Secondary V1 User

Knowledge-commerce creators.

Examples:

- Course instructors
- Consultants
- Training creators

Primary needs:

- Convert articles into course promotion cards.
- Build repeatable content formats.
- Apply author signature and light brand assets.

### 2.3 V2 User

Enterprise new-media teams.

Primary needs:

- Batch-generate branded content assets.
- Keep visual identity consistent.
- Share templates, brand assets, and works across team members.

## 3. Scope Strategy

The full PRD describes a SaaS product, but the implementation route should be phased.

### 3.1 MVP Principle

The MVP must validate one core promise:

> A personal creator can generate a publishable Xiaohongshu knowledge card or carousel in 30 seconds.

MVP prioritizes:

- Stable generation workflow.
- Stable JSON schema.
- Fixed templates.
- Text editing only.
- PNG export.
- Work history.

MVP excludes:

- Drag-and-drop design editing.
- Custom template builder.
- Team collaboration.
- Full brand center.
- Online payment.
- Marketplace-style template ecosystem.

### 3.2 Recommended Product Approach

Use the "content generation loop first" approach.

This means the first release should complete the path from input to generated image instead of investing heavily in template marketplace, brand management, or team collaboration.

Reasoning:

- It best matches the primary user pain.
- It can be built and tested quickly.
- It concentrates technical risk in LLM structured output and HTML screenshot rendering.
- It gives the product a clear activation metric: successful image exports.

## 4. Information Architecture

### 4.1 Full SaaS Architecture

```text
Workspace
├── AI Generator
│   ├── Topic generation
│   ├── Article-to-card generation
│   ├── Viral-title generation
│   └── Generation history entry
├── My Works
│   ├── All works
│   ├── Favorites
│   └── Drafts and failed tasks
├── Template Center
│   ├── System templates
│   ├── Style categories
│   └── Favorite templates
├── Brand Center
│   ├── Brand colors
│   ├── Logo and watermark
│   └── Author signature
└── Account Center
    ├── Profile
    ├── Usage quota
    └── Membership and orders
```

### 4.2 MVP Navigation

MVP should only expose four primary navigation entries:

- AI Generator
- My Works
- Template Center
- Account Center

Brand Center should be documented as a future monetization feature but not shown as a primary MVP module.

## 5. Experience Direction

### 5.1 Visual Direction

Use a hybrid visual model:

- The AI Generator uses a dark, immersive AI creation-console style.
- Management pages use a clean, light, productivity-oriented style.

This gives the product a memorable first-screen experience without making history, account, and record pages harder to scan.

### 5.2 Generator Page Layout

The AI Generator should be the first screen after entering the app. It should not be a marketing landing page.

Recommended layout:

```text
Top Navigation
├── Logo and product name
├── System status
├── Generation records
├── Usage records
├── Quota or membership entry
└── User menu

Main Creation Workspace
├── Main content input area
│   ├── Generation method: topic or article
│   ├── Topic/title input
│   ├── Article or context input
│   └── Tone selector
├── Output format area
│   ├── Xiaohongshu 3:4
│   ├── YouTube cover 16:9
│   ├── Bilibili cover 16:9
│   └── Douyin cover 9:16
├── Rendering mode area
│   ├── Precise Card
│   ├── AI Creative Image
│   └── Hybrid Mode
├── Template and style area
│   ├── Recommended templates
│   ├── Style selector
│   └── Recently used templates
└── Fixed action bar
    ├── Remaining quota
    ├── Estimated cost
    └── Generate card button
```

### 5.3 Editing Boundary

MVP only supports text editing and page order adjustment.

Allowed:

- Edit title.
- Edit subtitle.
- Edit summary.
- Edit bullet points.
- Edit page body text.
- Reorder carousel pages.
- Regenerate content.
- Switch template before rendering.

Not allowed in MVP:

- Dragging elements.
- Freeform layout editing.
- Uploading custom background images.
- Editing exact coordinates.
- Changing every visual style token.

## 6. Core User Flow

MVP flow:

```text
Choose generation method
→ Enter topic or article
→ Choose generation type and output size
→ Generate structured AI content
→ Review and edit text
→ Choose template and preview
→ Render images
→ Download PNG and save work
```

Important product decision:

AI generation should return structured content first. The system should not immediately create final images before the user can review and edit the text.

This reduces failed image output, improves user control, and makes LLM errors easier to recover from.

## 7. Feature Requirements

### 7.1 AI Generator

MVP generation methods:

- Topic generation
- Article generation

Topic generation fields:

- Topic title
- Subtitle or context
- Additional instructions

Article generation fields:

- Article body
- Extraction goal: summarize ideas, split into carousel, or generate cover copy
- Page count: automatic, 3 pages, 5 pages, or 7 pages
- Content style: professional and concise, Xiaohongshu style, or knowledge-commerce style

V1 generation method:

- Viral-title generation

MVP generation types:

- Single-page knowledge card
- Multi-page carousel card

MVP output sizes:

- Xiaohongshu 3:4
- YouTube cover 16:9
- Bilibili cover 16:9
- Douyin cover 9:16

V1 output sizes:

- WeChat article cover
- Square 1:1
- Landscape presentation image

MVP tone options:

- Professional and concise
- Xiaohongshu style

### 7.2 Structured Content Review

After AI generation, users see editable structured content.

Single-page fields:

- Title
- Subtitle
- Summary
- Points
- Author

Carousel fields:

- Cover page
- Point pages
- Summary page

Rules:

- MVP carousel supports 3 to 6 pages.
- Cover page defaults to the first page.
- Summary page defaults to the last page.
- Users can reorder point pages.
- Users can edit all text fields.

### 7.3 Template Center

MVP templates:

- Minimal Apple style
- Xiaohongshu highlight style
- Business briefing style

Template cards show:

- Preview image
- Template name
- Supported content types
- Supported ratios

V1 template features:

- Template categories
- Favorites
- Recently used templates
- Usage count
- More style packs

### 7.4 Image Rendering

MVP should expose three rendering modes at the product-design level:

- Precise Card: HTML template rendering. This is the MVP default because it keeps text accurate and layout stable.
- AI Creative Image: third-party image-generation model. This is suitable for visual covers, illustration, and atmosphere images, but should not be trusted for dense text.
- Hybrid Mode: AI-generated background or visual asset plus HTML-rendered title, points, logo, and watermark. This is the preferred long-term differentiated mode.

MVP implementation scope:

- Build Precise Card first.
- Keep AI Creative Image and Hybrid Mode visible in the prototype as product direction, but implement them in V1 unless the first implementation scope is expanded.

Precise Card rendering flow:

```text
Validated content JSON
→ Inject into HTML template
→ Render in Playwright
→ Screenshot to PNG
→ Upload to object storage
→ Save image URL to work record
```

Design rationale:

- Lower cost than direct AI image generation.
- Better text accuracy.
- More consistent visual style.
- Easier template reuse.

AI Creative Image flow:

```text
User input and structured prompt
→ Third-party image-generation model
→ Generated image preview
→ Optional regenerate
→ Upload to object storage
→ Save work record
```

Hybrid Mode flow:

```text
User input
→ Structured content JSON
→ AI-generated background or visual asset
→ HTML overlay for accurate title and text
→ Playwright screenshot
→ PNG output
```

### 7.5 My Works

MVP capabilities:

- List works.
- Search by title.
- View preview.
- Download PNG.
- Delete work.
- Reopen for text editing.
- Regenerate from existing content.

Work fields:

- Title
- Creation time
- Template
- Ratio
- Status
- Preview image

### 7.6 Account Center

MVP capabilities:

- Profile display.
- Daily quota display.
- Basic usage records.

V1 capabilities:

- Membership information.
- Order records.
- Payment status.
- Invoice or billing fields if needed.

### 7.7 Brand Center

Brand Center is not part of MVP.

V1/V2 capabilities:

- Logo
- Brand name
- Brand color
- Font preference
- Watermark
- Author name
- Default signature

Brand assets apply automatically to future generated cards.

## 8. AI Content Architecture

### 8.1 Model Providers

Potential providers:

- DeepSeek
- Gemini
- OpenAI
- Doubao

The application should abstract model calls behind a provider interface so the product can switch providers without changing business logic.

### 8.2 Output Principle

AI output must be schema-first. The product should optimize for renderable, valid JSON rather than expressive but unstable text.

### 8.3 Single-Card Schema

```json
{
  "type": "single_card",
  "title": "为什么学了很多却还是没成长",
  "subtitle": "消费知识不等于成长",
  "summary": "成长不是输入更多信息，而是完成从理解到行动的闭环。",
  "points": [
    "收藏不等于行动",
    "知道不等于做到",
    "输入不等于输出"
  ],
  "author": "AI知识卡片工厂"
}
```

Validation rules:

- `title` is required, recommended length 8 to 24 Chinese characters.
- `subtitle` is optional, max 24 Chinese characters.
- `summary` is optional for some templates, max 60 Chinese characters.
- `points` is required, 3 to 5 items.
- Each point should be 8 to 22 Chinese characters.

### 8.4 Carousel Schema

```json
{
  "type": "carousel_card",
  "title": "为什么学了很多却还是没成长",
  "pages": [
    {
      "role": "cover",
      "title": "为什么学了很多却还是没成长",
      "subtitle": "消费知识不等于成长"
    },
    {
      "role": "point",
      "title": "收藏不等于行动",
      "body": "真正改变你的不是收藏夹里的内容，而是你今天实际做了什么。"
    },
    {
      "role": "summary",
      "title": "成长的关键",
      "body": "少一点无效输入，多一点行动反馈。"
    }
  ]
}
```

Validation rules:

- `pages` must contain 3 to 6 pages.
- The first page should be a cover page.
- The last page should be a summary page.
- Point pages should contain one clear idea per page.
- Each body field should respect template field limits.

### 8.5 AI Failure Handling

Required MVP handling:

- Non-JSON output: retry once with a repair prompt.
- Missing required fields: backend attempts schema repair.
- Still invalid after repair: return a user-facing generation failure.
- Text too long: allow user into editor and mark overlong fields.
- Provider timeout: mark task failed and allow retry.
- Quota insufficient: block before calling the model.

## 9. Template Rendering Architecture

### 9.1 Template Metadata

Each template declares its supported content types, ratios, page limits, and text limits.

Example:

```json
{
  "id": "xiaohongshu-highlight-001",
  "name": "小红书高亮风",
  "supportedTypes": ["single_card", "carousel_card"],
  "supportedRatios": ["3:4", "1:1"],
  "maxPages": 6,
  "fieldLimits": {
    "title": 24,
    "subtitle": 24,
    "point": 22,
    "body": 80
  }
}
```

### 9.2 Rendering Requirements

- Templates consume validated `content_json`, not raw user input.
- Templates must have deterministic output.
- Screenshots should be generated from server-side HTML rendering.
- Generated images must be saved as PNG.
- Each carousel page should be exported as a separate PNG.
- V1 can add ZIP export for multi-page downloads.

### 9.3 Rendering Failure Handling

Required MVP handling:

- Screenshot failure: mark render task failed and allow retry.
- Template mismatch: block rendering and ask user to choose a compatible template.
- Upload failure: keep local task state and allow upload retry.
- Overlong text: show field-level warnings before rendering.

## 10. Data Model

### 10.1 `user`

- `id`
- `email`
- `password_hash`
- `nickname`
- `avatar_url`
- `plan`
- `daily_quota`
- `created_at`
- `updated_at`

### 10.2 `template`

- `id`
- `name`
- `description`
- `style_key`
- `cover_url`
- `supported_types`
- `supported_ratios`
- `field_limits_json`
- `status`
- `created_at`

### 10.3 `card_project`

- `id`
- `user_id`
- `title`
- `type`
- `ratio`
- `template_id`
- `content_json`
- `cover_url`
- `status`
- `created_at`
- `updated_at`

### 10.4 `card_page`

- `id`
- `project_id`
- `page_index`
- `role`
- `content_json`
- `image_url`
- `created_at`

### 10.5 `generate_task`

- `id`
- `user_id`
- `project_id`
- `task_type`
- `input_text`
- `input_params_json`
- `status`
- `error_message`
- `started_at`
- `finished_at`
- `created_at`

### 10.6 `usage_record`

- `id`
- `user_id`
- `task_id`
- `usage_type`
- `amount`
- `model_name`
- `created_at`

## 11. API Requirements

MVP APIs:

```text
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

API behavior:

- `POST /api/generations/content` calls the LLM and returns structured JSON.
- `POST /api/projects` saves structured content as a draft work.
- `PUT /api/projects/{id}/content` saves edited text and page order.
- `POST /api/projects/{id}/render` renders final PNG images.
- `GET /api/tasks/{id}` returns async task status.

## 12. Technical Architecture

### 12.1 Proposed Stack

```text
Frontend: Vue 3
Backend: Spring Boot
Database: SQLite for MVP, MySQL for later production deployment
Cache and quota: database-backed quota records for MVP, Redis optional later
Object storage: local file storage for MVP, MinIO or S3-compatible storage later
Screenshot rendering: Playwright
AI providers: DeepSeek, Gemini, OpenAI, Doubao
```

### 12.2 Main System Flow

```text
User input
→ Backend validates request and quota
→ LLM provider returns structured JSON
→ Backend validates and repairs JSON
→ Frontend shows editable structured content
→ User chooses template
→ Backend renders HTML through template
→ Playwright screenshots PNG
→ PNG saved to local storage
→ Project and task records updated
```

## 13. Commercialization

### 13.1 Free Plan

- 10 generations per day.
- Basic templates.
- Platform watermark from V1 onward. MVP can omit watermark enforcement to keep export flow simple.
- Limited history retention if needed later.

### 13.2 Pro Plan

Suggested price: 39 CNY/month.

Capabilities:

- More generation quota or near-unlimited generation with fair-use controls.
- Advanced templates.
- No watermark.
- Author signature.
- Batch download.
- Priority generation queue if needed.

### 13.3 Team Plan

Suggested price: from 99 CNY/month.

Capabilities:

- Team members.
- Shared brand assets.
- Shared work library.
- Shared quota.
- Role permissions.

## 14. Roadmap

### 14.1 MVP: 2-Week Validation Version

Goal:

Help users generate a directly publishable Xiaohongshu knowledge card within 30 seconds.

Included:

- Default local user. Email registration and login are deferred.
- Immersive AI Generator.
- Topic generation.
- Article generation.
- Single-page card.
- Multi-page carousel.
- Xiaohongshu 3:4.
- YouTube cover 16:9.
- Bilibili cover 16:9.
- Douyin cover 9:16.
- Precise Card rendering through HTML templates.
- 3 built-in templates.
- Text editing.
- Page order adjustment.
- PNG export.
- My Works.
- Daily free quota.
- Basic usage records.

Excluded:

- Email registration and login.
- Brand Center.
- Team collaboration.
- Custom templates.
- Drag-and-drop design editor.
- Direct third-party AI image generation.
- Hybrid rendering mode.
- Online payment.
- Template marketplace.

### 14.2 V1: Content Efficiency Version

Goal:

Improve repeat usage and paid conversion.

Add:

- Viral-title generation.
- Template favorites.
- Recently used templates.
- More template categories.
- AI Creative Image mode through a third-party image-generation model.
- Hybrid Mode with AI-generated background and HTML text overlay.
- Batch export.
- Brand watermark.
- Author signature.
- Pro membership and orders.
- Generation quality feedback.
- Better failed-task retry.

### 14.3 V2: Team and Brand Version

Goal:

Serve knowledge-commerce creators and enterprise new-media teams.

Add:

- Team workspace.
- Full Brand Center.
- Member permissions.
- Asset library.
- Custom templates.
- Batch article-to-carousel generation.
- Analytics dashboard.
- API access.

## 15. MVP Success Metrics

Activation:

- Percentage of new users who generate at least one valid card.

Core success:

- Percentage of generation tasks that produce valid structured JSON.
- Percentage of rendered projects that successfully export PNG.
- Median time from input to first PNG.

Retention:

- Percentage of users who return within 7 days.
- Average generated works per active user.

Quality:

- Regeneration rate.
- Manual edit rate.
- Download rate after preview.

Commercial signal:

- Free quota exhaustion rate.
- Click-through rate on upgrade entry.

## 16. Open Product Decisions

These are not blockers for MVP PRD approval, but should be decided before implementation planning:

- When authentication is added after MVP, whether registration should support email only or also phone number.
- Whether MVP should include public sharing links or only PNG download.
- Whether multi-page carousel should export individual PNG files only or also ZIP.
- Whether watermark enforcement should be added in V1 or delayed until paid conversion is being tested.
- Which LLM provider should be used first for cost, latency, and JSON stability.
- Which third-party image-generation provider should be used for AI Creative Image mode in V1.

## 17. Non-Goals

MVP should not attempt to be:

- A Canva replacement.
- A full image-generation tool.
- A full social-media scheduling tool.
- A team collaboration product.
- A template marketplace.

## 18. Review Status

This PRD design incorporates the currently approved decisions:

- Product route: full SaaS PRD with MVP, V1, and V2 phases.
- MVP primary user: Xiaohongshu and self-media individual bloggers.
- MVP scope: single-page and carousel generation with constrained editing.
- Editing depth: text editing and page order only.
- AI priority: stable structured schema first.
- Visual direction: dark immersive generator page, light management pages.
- Repository handling: do not initialize Git at this stage.

# CardFlow AI Agent Guide

常驻上下文请保持简短。项目最新状态以 `README.md` 为准。

## 项目定位

CardFlow AI 是本地优先的 AI 知识卡片生成器 MVP，目标是跑通创作者从主题或文章到可发布 PNG 卡片的核心链路。

核心流程：

```text
输入主题/文章 -> AI 生成结构化 JSON -> 用户编辑文本 -> HTML 模板渲染 -> Playwright 截图 -> 本地 PNG 输出 -> 作品历史
```

## 技术栈

- 前端：Vue 3、Vite、TypeScript
- 后端：Spring Boot、Java 17、SQLite
- 存储：本地文件，默认 `storage/outputs`
- AI：`LlmProvider` 抽象，当前先用 Mock Provider
- 渲染：HTML 模板 + Playwright PNG 截图

## 关键目录

```text
apps/api/       Spring Boot 后端
apps/web/       Vue 前端
data/           SQLite 数据库
storage/        本地生成图片
docs/           PRD 和实施计划
prototypes/     已确认静态原型
```

先读这些文件：

- `README.md`
- `docs/superpowers/specs/2026-06-02-ai-card-factory-prd-design.md`
- `docs/superpowers/plans/2026-06-03-cardflow-ai-mvp-implementation-plan.md`
- `prototypes/ai-card-factory-generator.html`

## 常用命令

从仓库根目录执行：

```bash
npm run dev:web
npm run build:web
npm run dev:api
npm run test:api
```

Playwright Chromium 安装：

```bash
mvn -f apps/api/pom.xml exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

## MVP 边界

保持产品聚焦在生成闭环，不要做成完整设计编辑器。

MVP 可以做：

- 主题/文章生成结构化内容
- 编辑标题、正文、要点和 JSON
- 选择模板和输出比例
- 保存、渲染、重新渲染、查看历史

暂缓：

- 拖拽编辑器和自由坐标编辑
- 自定义模板构建器
- 真实用户体系、支付、团队协作
- 品牌中心和三方 AI 生图

## 协作规则

- 非小改动前先读 `README.md`、本文件和相关源码。
- 保持改动范围小，优先沿用现有模式。
- 不要重置或覆盖用户已有改动。
- 后端 API DTO 优先放在 `ApiModels.java`，渲染结构放在 `RenderModels.java`。
- LLM 供应商逻辑必须留在 `LlmProvider` 边界内。
- 前端 API 调用集中在 `apps/web/src/api/client.ts`。
- UI 不要替换成营销落地页，生成器是第一屏核心产品。
- 行为、启动方式或项目状态变化时，同步更新 `README.md`。
- 不能跑验证时，说明尝试过的命令和具体阻塞原因。


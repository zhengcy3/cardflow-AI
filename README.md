# CardFlow AI

CardFlow AI 是一个本地优先的 AI 知识卡片生成器 MVP。

当前目标是先跑通一条核心链路：

```text
输入主题
→ AI 生成结构化内容 JSON
→ AI 生成动态 HTML
→ HTML 安全校验
→ Playwright 截图
→ 本地输出 PNG
→ 作品历史记录
```

## 技术栈

- 前端：Vue 3、Vite、TypeScript
- 后端：Spring Boot、Java 17、SQLite
- 存储：本地文件，默认输出到 `storage/outputs`
- AI：DeepSeek 文本生成 + MiniMax image-01 文生图（AI 创意图模式）
- 截图渲染：Playwright

## 目录结构

```text
apps/
├── api/        Spring Boot 后端
└── web/        Vue 前端
data/           SQLite 数据库目录
storage/        本地生成图片目录
docs/           PRD 和实施计划
prototypes/     已确认的静态原型
```

## 启动后端

后端命令建议从项目根目录执行，这样 SQLite 和生成图片会落在预期目录：

```bash
export DEEPSEEK_API_KEY=你的 DeepSeek API Key
npm run dev:api
```

MiniMax 生图 Key 配置在 `apps/api/src/main/resources/application.yml` 的 `cardflow.image.minimax.api-key`。AI 创意图模式会先用 DeepSeek 生成生图 prompt，再调用 MiniMax 输出 PNG。

DeepSeek 默认使用 `deepseek-v4-flash`（比 v4-pro 便宜约 3 倍），输出上限 `spring.ai.openai.chat.options.max-tokens: 2048`。每次生成会在日志中输出 API 轮次、token 用量和实际 responseModel。

后端地址：

```text
http://localhost:8090
```

SQLite 数据库默认位置：

```text
data/cardflow.db
```

生成图片默认位置：

```text
storage/outputs
```

## 启动前端

首次启动前端需要先安装依赖：

```bash
cd apps/web
npm install
npm run dev
```

依赖安装完成后，也可以从项目根目录启动：

```bash
npm run dev:web
```

前端地址：

```text
http://localhost:5178
```

## 本地缓存问题

如果 npm 或 Maven 的用户级缓存有权限问题，可以改用项目内缓存。

npm：

```bash
cd apps/web
npm install --cache ../../.npm-cache
```

Maven：

```bash
mvn -f apps/api/pom.xml -Dmaven.repo.local=.m2 test
```

## Playwright 浏览器安装

后端渲染 PNG 前，Playwright Java 还需要安装 Chromium：

```bash
mvn -f apps/api/pom.xml exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

## 当前 MVP 已实现

- 默认本地用户
- SQLite 表结构初始化
- 默认模板种子数据
- LLM Provider 抽象层
- DeepSeek 主题生成 Provider
- AI 动态 HTML 卡片生成协议
- 作品创建、列表、详情、更新、删除 API
- HTML 模板渲染和动态 HTML 渲染
- Playwright 本地 PNG 截图输出，使用 2 倍设备像素提升清晰度
- 多页轮播作品按页输出 PNG
- Vue 生成器页面
- 主题生成
- 文章生成入口暂时禁用，等待真实 AI 规则接入
- 小红书、YouTube、B站、抖音输出比例选择
- HTML 精准卡片、AI 创意图两种出图方式
- 最近作品列表
- 右侧未生成前展示卡片占位预览，生成后展示真实 PNG，并使用应用内弹窗预览大图
- 动态 HTML 内容生成，并自动保存、渲染到作品历史
- AI 创意图模式：DeepSeek 生成生图 prompt + MiniMax image-01 文生图输出 PNG

## 当前还没完成

- 文章生成真实 AI 接入
- 登录和用户体系
- 会员、额度、订单能力
- 模板中心的完整管理能力
- 品牌中心
- 团队协作

## 当前验证状态

目前已验证：

- `mvn -f apps/api/pom.xml test`
- `npm run build:web`

还没有完成以下验证：

- 使用真实 `DEEPSEEK_API_KEY` 做一次端到端主题生成
- 前后端联调
- Playwright 实际 PNG 渲染

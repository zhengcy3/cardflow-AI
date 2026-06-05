# CardFlow AI

CardFlow AI 是一个本地优先的 AI 知识卡片生成器 MVP。

当前目标是先跑通一条核心链路：

```text
输入主题或文章
→ AI 生成结构化内容 JSON
→ HTML 模板渲染
→ Playwright 截图
→ 本地输出 PNG
→ 作品历史记录
```

## 技术栈

- 前端：Vue 3、Vite、TypeScript
- 后端：Spring Boot、Java 17、SQLite
- 存储：本地文件，默认输出到 `storage/outputs`
- AI：先使用 Mock Provider，后续替换为真实 LLM Provider
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
npm run dev:api
```

后端地址：

```text
http://localhost:8080
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
http://localhost:5173
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
- Mock 内容生成 API
- LLM Provider 抽象层
- 作品创建、列表、详情、更新、删除 API
- HTML 模板渲染
- Playwright 本地 PNG 截图输出
- 多页轮播作品按页输出 PNG
- Vue 生成器页面
- 主题生成 / 文章生成切换
- 小红书、YouTube、B站、抖音输出比例选择
- HTML 精准卡片、AI 创意图、混合模式的产品入口
- 最近作品列表
- 右侧预览和生成图片链接
- 结构化 JSON 编辑，并支持保存后重新渲染

## 当前还没完成

- 真实 LLM Provider 接入
- 三方 AI 生图模型接入
- 登录和用户体系
- 会员、额度、订单能力
- 模板中心的完整管理能力
- 品牌中心
- 团队协作

## 当前验证状态

目前已经做过不依赖下载的静态检查，包括 `package.json` JSON 解析和关键源码链路检查。

因为依赖安装之前受网络或沙箱影响没有完整成功，所以还没有完成以下验证：

- `npm install`
- `npm run build`
- `mvn test`
- 前后端联调
- Playwright 实际 PNG 渲染

下一步建议先安装依赖并跑通本地启动，再接真实 LLM。

# CardFlow AI 图片风格目录设计

Date: 2026-06-21
Status: Approved

## 1. 背景

CardFlow AI 有三种出图模式：`precise_card`（HTML）、`ai_creative_image`（创意封面）、`ai_knowledge_poster`（知识海报）。风格选择 UI 已有「封面风格」面板，但 `templateId` 尚未注入生成 prompt。

参考两个开源 Skill 项目：

- **baoyu-skills**（`baoyu-cover-image` / `baoyu-infographic`）：Palette × Rendering 五维风格体系 + 内容信号自动选型
- **guizang-ppt-skill**：电子杂志 × 电子墨水 / 瑞士国际主义两套美学 + 固定主题色预设 + 配图 prompt 后缀规范

## 2. 设计原则

1. **固定预设，禁止随意 hex**：用户只能从 9 套预设中选，避免 AI 乱配色（借鉴 guizang `themes.md`）
2. **Palette × Rendering 组合**：每套风格 = 色板 + 渲染技法 + 平台适配说明（借鉴 baoyu `style-presets.md`）
3. **Skill 外置 prompt**：创意图 / 知识海报的系统 prompt 从 `GenerationService` 硬编码迁到 `SKILL.md`
4. **风格注入链路**：`templateId` → DB `style_key` → `ImageStylePresets` → user prompt 追加风格约束
5. **一份作品一种风格**：生成全程不中途换风格

## 3. 九套可选风格

| ID | styleKey | 显示名 | Palette | Rendering | 主要适用模式 | 美学来源 |
|----|----------|--------|---------|-----------|--------------|----------|
| xiaohongshu-highlight | xiaohongshu_highlight | 小红书高亮 | warm | flat-vector | 全部 | CardFlow 原有 |
| minimal-apple | minimal_apple | 极简苹果风 | mono | digital | 全部 | CardFlow 原有 |
| business-briefing | business_briefing | 商业简报 | cool | digital | HTML / 海报 | CardFlow 原有 |
| editorial-ink | editorial_ink | 电子杂志墨水 | mono | digital | 创意图 / 海报 | guizang 风格 A |
| swiss-grid | swiss_grid | 瑞士网格 | cool | flat-vector | 全部 | guizang 风格 B |
| hand-sketch | hand_sketch | 手绘笔记 | macaron | hand-drawn | HTML / 海报 | baoyu sketch-notes |
| flat-infographic | flat_infographic | 扁平信息图 | pastel | flat-vector | HTML / 海报 | baoyu flat-doodle |
| warm-documentary | warm_documentary | 人文纪实 | earth | painterly | 创意图 | guizang 纪实 + baoyu nature |
| bold-cover | bold_cover | 高冲击封面 | vivid | digital | 创意图 | baoyu propaganda/cinematic |

## 4. 自动推荐规则（LLM 可选参考）

| 内容信号 | 推荐风格 |
|----------|----------|
| 知识清单 / 避坑 / 教程 | hand_sketch 或 flat_infographic |
| 商业 / 数据 / 理性分析 | business_briefing 或 swiss_grid |
| 人文 / 生活方式 / 情绪故事 | warm_documentary 或 editorial_ink |
| 产品发布 / 强观点 / 封面冲击 | bold_cover |
| 不确定 / 通用分享 | xiaohongshu_highlight |

用户在前端选了风格则以用户选择为准，自动推荐仅作补充说明字段。

## 5. Skill 文件结构

```text
skills/
├── cardflow.html-card-generator/     # 已有，补充风格映射表
├── cardflow.creative-image-generator/ # 新建，ai_creative_image 协议
├── cardflow.knowledge-poster-generator/ # 新建，ai_knowledge_poster 协议
└── cardflow.image-styles/            # 新建，九套风格完整目录 + 自动选型
```

## 6. 不在本次范围

- 前端风格面板 UI 改版（仍读 `/api/templates`）
- 用户自定义 hex 颜色
- 参考图 / `--ref` 风格迁移（baoyu 高级能力，后续迭代）

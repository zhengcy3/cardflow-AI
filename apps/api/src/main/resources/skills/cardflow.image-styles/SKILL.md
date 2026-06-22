# CardFlow 图片风格目录

## 使用方式

用户在前端选择「封面风格」→ 后端解析 `templateId` 得到 `styleKey` → 注入到生成 prompt。
一份作品全程只用一套风格，禁止中途混搭色板。

需要完整风格细节时，调用 `read_skill("cardflow.image-styles")`。

## 九套预设（固定，禁止用户自定义 hex）

| styleKey | 名称 | Palette | Rendering | 适用模式 |
|----------|------|---------|-----------|----------|
| xiaohongshu_highlight | 小红书高亮 | warm | flat-vector | 全部 |
| minimal_apple | 极简苹果风 | mono | digital | 全部 |
| business_briefing | 商业简报 | cool | digital | HTML / 海报 |
| editorial_ink | 电子杂志墨水 | mono | digital | 创意图 / 海报 |
| swiss_grid | 瑞士网格 | cool | flat-vector | 全部 |
| hand_sketch | 手绘笔记 | macaron | hand-drawn | HTML / 海报 |
| flat_infographic | 扁平信息图 | pastel | flat-vector | HTML / 海报 |
| warm_documentary | 人文纪实 | earth | painterly | 创意图 |
| bold_cover | 高冲击封面 | vivid | digital | 创意图 |

## 风格详解

### xiaohongshu_highlight · 小红书高亮
- 色板：暖橙 #ED8936、奶油底 #FFFAF0、深棕强调 #744210
- 渲染：扁平矢量、色块高亮、标签、下划线
- HTML：强标题 + 3-6 模块，关键词高亮
- 创意图：暖色隐喻封面，主体居中，少文字
- 海报：左文右图、便签贴纸、手写装饰、暖米色底

### minimal_apple · 极简苹果风
- 色板：深灰 #1a1a1a、白底 #FAFAFA、单一强调色
- 渲染：数字抛光、大留白、细线分割
- HTML：sparse/balanced 布局，字号对比强，元素极少
- 创意图：单一主体 + 大量负空间，禁止 clutter
- 海报：大标题 + 极少要点，留白 60%+

### business_briefing · 商业简报
- 色板：冷蓝 #2B6CB0、浅灰底 #EDF2F7、数据绿 #38A169
- 渲染：数字信息图、细线图表、编号模块
- HTML：dense/list/comparison，理性层级
- 海报：数据块 + 短标签，禁止卡通

### editorial_ink · 电子杂志墨水（guizang 风格 A）
- 色板：墨黑 #0a0a0b、暖米纸 #f1efea、低饱和强调
- 渲染：电子墨水杂志、衬线标题气质、纸张质感
- 创意图：纪实摄影感、自然光、低饱和、留标题空间
- 海报：黑白灰为主 + 一个低饱和 accent，细线网格

### swiss_grid · 瑞士网格（guizang 风格 B）
- 色板：黑 #000、白 #FFF、单一 accent（IKB 蓝 #0047AB 或柠檬黄）
- 渲染：12 列网格、直角模块、发丝线、无圆角无阴影
- HTML：quadrant/comparison，左对齐，极大字号对比
- 海报：网格排版、短标签 ≤8 字、禁止渐变圆角

### hand_sketch · 手绘笔记
- 色板：马卡龙 #FED7E2/#BEE3F8/#FEEBC8、纸纹底
- 渲染：手绘线条、马克笔填充、便签感
- HTML：list/flow，手写标签、波浪箭头
- 海报：手绘装饰语、涂鸦图标、教育感

### flat_infographic · 扁平信息图
- 色板：粉彩 #FED7E2/#C6F6D5/#BEE3F8
- 渲染：扁平矢量、几何图标、无渐变
- HTML：flow/mindmap/quadrant，图标 + 短句
- 海报：对勾清单 + 简单插画角标；**单列竖排卡片、禁止重叠**（勿用 HTML 的 mindmap/quadrant 布局）

### warm_documentary · 人文纪实
- 色板：大地色 #744210/#F6AD55、象牙底
- 渲染：水彩/绘画感、自然光摄影
- 创意图：Fujifilm/Leica 纪实风格，真实场景，禁止 AI 机器人
- 海报：不适用（优先创意图）

### bold_cover · 高冲击封面
- 色板：高饱和 #E53E3E/#3182CE、强对比 duotone
- 渲染：数字抛光、电影海报感
- 创意图：单一强隐喻、高对比、动态能量
- 海报：不适用（优先创意图）

## 自动选型（用户未指定时的推荐）

| 内容信号 | 推荐 styleKey |
|----------|---------------|
| 清单/避坑/教程 | hand_sketch 或 flat_infographic |
| 商业/数据/分析 | business_briefing 或 swiss_grid |
| 人文/生活方式 | warm_documentary 或 editorial_ink |
| 发布/强观点/封面 | bold_cover |
| 默认 | xiaohongshu_highlight |

## MiniMax 生图后缀（创意图 / 海报通用）

每个配图 prompt 末尾追加：
- 主体居中，保留边距，画面密度中等
- 禁止页眉页脚页码角标装饰边框
- 禁止 gibberish 伪中文、英文乱入海报正文
- 竖屏 3:4 时标题区靠上，场景靠下

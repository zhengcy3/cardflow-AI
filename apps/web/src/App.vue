<script setup lang="ts">
import {
  Circle,
  Clapperboard,
  Gem,
  Heart,
  Network,
  Palette,
  Sparkles,
  User,
  Zap,
  type LucideIcon
} from "lucide-vue-next";
import { computed, ref } from "vue";
import bilibiliIcon from "./assets/icons/bilibili.svg";
import douyinIcon from "./assets/icons/tiktok.svg";
import xiaohongshuIcon from "./assets/icons/xiaohongshu.jpg";
import youtubeIcon from "./assets/icons/youtube.png";
import {
  createProject,
  deleteProject,
  generateContent,
  getUsageSummary,
  listProjects,
  listTemplates,
  normalizeRenderMode,
  renderProject,
  type GenerationMode,
  type OutputFormat,
  type ProjectResponse,
  type RenderMode,
  type TemplateResponse
} from "./api/client";

type AppView = "generator" | "records";

interface HtmlCardContent {
  kind?: string;
  title?: string;
}

const STYLE_TILE_ICONS: LucideIcon[] = [Sparkles, Zap, Circle, Gem, Palette, Clapperboard, Heart, Network];
const RECORDS_PAGE_SIZE = 16;
const SECTION_PREVIEW_MAX = 8;
const SECTION_PREVIEW_OVERFLOW = 4;

type RecordsFilter = "all" | "xhs_3_4" | "landscape_16_9" | "douyin_9_16";

const RECORDS_FILTER_TABS: { id: RecordsFilter; label: string }[] = [
  { id: "all", label: "全部" },
  { id: "xhs_3_4", label: "小红书" },
  { id: "landscape_16_9", label: "横版" },
  { id: "douyin_9_16", label: "抖音" }
];

interface RecordSection {
  filter: RecordsFilter;
  label: string;
  coverClass: string;
  items: ProjectResponse[];
  previewItems: ProjectResponse[];
  hiddenCount: number;
}

const RATIO_LABELS: Record<OutputFormat, string> = {
  xhs_3_4: "小红书",
  youtube_16_9: "YouTube",
  bilibili_16_9: "B站",
  douyin_9_16: "抖音"
};

const FORMAT_RATIO_HINTS: Record<OutputFormat, string> = {
  xhs_3_4: "3:4 · 竖版卡片",
  youtube_16_9: "16:9 · 横版封面",
  bilibili_16_9: "16:9 · 横版封面",
  douyin_9_16: "9:16 · 竖屏封面"
};

const generationMode = ref<GenerationMode>("topic");
const renderMode = ref<RenderMode>("precise_card");
const outputFormat = ref<OutputFormat>("xhs_3_4");
const templateId = ref("xiaohongshu-highlight");

const topicInput = ref({
  title: "为什么学了很多却还是没成长？",
  subtitle: "消费知识不等于成长",
  instructions: "希望内容适合小红书知识博主，观点要清晰，语言要短句化。"
});

const articleInput = ref({
  body: "粘贴一篇长文，AI 会先提炼核心观点，再拆成适合发布的知识卡片结构。",
  extractionGoal: "summarize",
  pageCount: "auto",
  style: "concise"
});

const activeView = ref<AppView>("generator");
const contentJson = ref("");
const currentProject = ref<ProjectResponse | null>(null);
const projects = ref<ProjectResponse[]>([]);
const templates = ref<TemplateResponse[]>([]);
const isGenerating = ref(false);
const isPreviewModalOpen = ref(false);
const activePreviewUrl = ref<string | null>(null);
const activePreviewTitle = ref("生成图片预览");
const statusMessage = ref("填写主题后一键生成卡片");
const statusIsError = ref(false);
const usageSummary = ref({ dailyQuota: 10, usedToday: 0, remainingToday: 10 });
const recordsPage = ref(1);
const recordsFilter = ref<RecordsFilter>("all");

function setStatus(message: string, isError = false) {
  statusMessage.value = message;
  statusIsError.value = isError;
}

const previewRatioClass = computed(() => ({
  "mock-card-wide": outputFormat.value === "youtube_16_9" || outputFormat.value === "bilibili_16_9",
  "mock-card-vertical": outputFormat.value === "douyin_9_16"
}));

const hasRenderedImage = computed(() => Boolean(currentProject.value?.coverUrl));
const currentPreviewUrl = computed(() => currentProject.value?.coverUrl ?? null);
const primaryButtonLabel = computed(() => (isGenerating.value ? "生成中..." : "生成卡片 →"));
const isBusy = computed(() => isGenerating.value);

function projectMatchesFilter(project: ProjectResponse, filter: RecordsFilter) {
  if (filter === "all") return true;
  if (filter === "landscape_16_9") {
    return project.ratio === "youtube_16_9" || project.ratio === "bilibili_16_9";
  }
  return project.ratio === filter;
}

const filteredProjects = computed(() =>
  projects.value.filter((project) => projectMatchesFilter(project, recordsFilter.value))
);

const recordsTotalPages = computed(() => {
  if (recordsFilter.value === "all") return 1;
  return Math.max(1, Math.ceil(filteredProjects.value.length / RECORDS_PAGE_SIZE));
});

const paginatedProjects = computed(() => {
  if (recordsFilter.value === "all") return [];
  const start = (recordsPage.value - 1) * RECORDS_PAGE_SIZE;
  return filteredProjects.value.slice(start, start + RECORDS_PAGE_SIZE);
});

const recordSections = computed<RecordSection[]>(() => {
  const buildSection = (
    filter: RecordsFilter,
    label: string,
    coverClass: string,
    items: ProjectResponse[]
  ): RecordSection => {
    const previewItems = sectionPreviewItems(items);
    return {
      filter,
      label,
      coverClass,
      items,
      previewItems,
      hiddenCount: Math.max(0, items.length - previewItems.length)
    };
  };

  const sections: RecordSection[] = [
    buildSection(
      "xhs_3_4",
      "小红书 3:4",
      "is-portrait",
      projects.value.filter((project) => project.ratio === "xhs_3_4")
    ),
    buildSection(
      "landscape_16_9",
      "横版 16:9",
      "is-wide",
      projects.value.filter(
        (project) => project.ratio === "youtube_16_9" || project.ratio === "bilibili_16_9"
      )
    ),
    buildSection(
      "douyin_9_16",
      "抖音 9:16",
      "is-vertical",
      projects.value.filter((project) => project.ratio === "douyin_9_16")
    )
  ];
  return sections.filter((section) => section.items.length > 0);
});

const recordsSummary = computed(() => {
  if (recordsFilter.value === "all") {
    return `${projects.value.length} 张 · 按平台分组`;
  }
  return `${filteredProjects.value.length} 张 · 第 ${recordsPage.value} / ${recordsTotalPages.value} 页`;
});

const activeFilterCoverClass = computed(() => filterCoverClass(recordsFilter.value));

function formatRatioLabel(ratio: OutputFormat) {
  return RATIO_LABELS[ratio] ?? ratio;
}

function sectionPreviewItems(items: ProjectResponse[]) {
  if (items.length > SECTION_PREVIEW_MAX) {
    return items.slice(0, SECTION_PREVIEW_OVERFLOW);
  }
  return items.slice(0, SECTION_PREVIEW_MAX);
}

function filterCoverClass(filter: RecordsFilter) {
  if (filter === "landscape_16_9") return "is-wide";
  if (filter === "douyin_9_16") return "is-vertical";
  return "is-portrait";
}

function selectRecordsFilter(filter: RecordsFilter) {
  recordsFilter.value = filter;
  recordsPage.value = 1;
}

function goRecordsPage(page: number) {
  recordsPage.value = Math.min(Math.max(1, page), recordsTotalPages.value);
}
const showBottomBar = computed(() => activeView.value === "generator");

function parseHtmlCard(json: string): HtmlCardContent | null {
  try {
    return JSON.parse(json) as HtmlCardContent;
  } catch {
    return null;
  }
}

function styleTileIcon(index: number) {
  return STYLE_TILE_ICONS[index % STYLE_TILE_ICONS.length];
}

function openPreview(url?: string | null, title?: string) {
  if (!url) return;
  activePreviewUrl.value = url;
  activePreviewTitle.value = title || currentProject.value?.title || "生成图片预览";
  isPreviewModalOpen.value = true;
}

function closePreview() {
  isPreviewModalOpen.value = false;
  activePreviewUrl.value = null;
}

function downloadPreview() {
  if (!activePreviewUrl.value) return;
  const link = document.createElement("a");
  link.href = activePreviewUrl.value;
  link.download = `${activePreviewTitle.value || "cardflow-card"}.png`;
  link.click();
}

function openGeneratorView() {
  activeView.value = "generator";
  statusIsError.value = false;
}

function openRecordsView() {
  activeView.value = "records";
  recordsFilter.value = "all";
  recordsPage.value = 1;
  void refreshProjects();
}

function loadProject(project: ProjectResponse) {
  currentProject.value = project;
  contentJson.value = project.contentJson;
  outputFormat.value = project.ratio;
  renderMode.value = normalizeRenderMode(project.renderMode);
  templateId.value = project.templateId;
  activeView.value = "generator";
  setStatus(project.coverUrl ? "已加载作品" : "已加载作品草稿");
}

function openProjectPreview(project: ProjectResponse) {
  openPreview(project.coverUrl, project.title);
}

async function refreshProjects() {
  projects.value = await listProjects();
}

async function runGeneration() {
  if (isGenerating.value) return;

  isGenerating.value = true;
  setStatus(
    renderMode.value === "ai_knowledge_poster"
      ? "AI 正在生成知识海报文案"
      : renderMode.value === "ai_creative_image"
        ? "AI 正在生成创意图提示词"
        : "AI 正在生成卡片"
  );

  try {
    const generated = await generateContent({
      generationMode: generationMode.value,
      outputFormat: outputFormat.value,
      renderMode: renderMode.value,
      templateId: templateId.value,
      topicInput: topicInput.value,
      articleInput: articleInput.value
    });

    contentJson.value = generated.contentJson;
    const card = parseHtmlCard(generated.contentJson);
    const title = card?.title?.trim() || topicInput.value.title;

    setStatus(
      renderMode.value === "ai_knowledge_poster" || renderMode.value === "ai_creative_image"
        ? "MiniMax 正在生图"
        : "正在出图"
    );

    currentProject.value = await createProject({
      title,
      type: generationMode.value === "article" ? "carousel_card" : "single_card",
      ratio: outputFormat.value,
      renderMode: renderMode.value,
      templateId: templateId.value,
      contentJson: generated.contentJson
    });

    await renderProject(currentProject.value.id);
    await refreshProjects();
    usageSummary.value = await getUsageSummary();
    currentProject.value = projects.value.find((project) => project.id === currentProject.value?.id) ?? currentProject.value;
    setStatus("生成完成，可预览或下载");
  } catch (error) {
    setStatus(error instanceof Error ? error.message : "生成失败", true);
  } finally {
    isGenerating.value = false;
  }
}

async function removeProject(projectId: string) {
  await deleteProject(projectId);
  await refreshProjects();
  if (recordsPage.value > recordsTotalPages.value) {
    recordsPage.value = recordsTotalPages.value;
  }
  if (currentProject.value?.id === projectId) {
    currentProject.value = null;
    contentJson.value = "";
    closePreview();
    setStatus("作品已删除");
  }
}

void refreshProjects();
void getUsageSummary().then((summary) => {
  usageSummary.value = summary;
});
void listTemplates().then((items) => {
  templates.value = items;
  if (items.length > 0 && !items.some((item) => item.id === templateId.value)) {
    templateId.value = items[0].id;
  }
});
</script>

<template>
  <main class="page">
    <nav class="topbar" aria-label="主导航">
      <button class="brand brand-button" type="button" @click="openGeneratorView">
        <div class="logo" aria-hidden="true"><span /></div>
        <div class="brand-name">CardFlow AI</div>
      </button>
      <div class="nav">
        <button class="status-pill" type="button"><span class="dot" /> 系统正常</button>
        <button class="nav-item" :class="{ active: activeView === 'records' }" type="button" @click="openRecordsView">生成记录</button>
        <button class="nav-item" type="button">使用记录</button>
        <button class="nav-item" type="button">模板中心</button>
      </div>
      <div class="account">
        <div class="avatar" aria-label="访客" title="用户登录将在后续版本接入">
          <User :size="16" :stroke-width="2" />
        </div>
        <button class="user-pill" type="button">免费版</button>
        <button class="menu-button" type="button" aria-label="打开菜单">≡</button>
      </div>
    </nav>

    <template v-if="activeView === 'generator'">
      <section class="hero" aria-label="AI 生成器">
        <header class="headline">
          <div class="engine"><span class="dot" /> ENGINE V1.0 已就绪</div>
          <h1>打造<span>高转化</span>知识卡片</h1>
          <p class="subline">输入主题一键出图，选择风格后即可生成可发布的知识卡片。</p>
        </header>
      </section>

      <div class="workspace-tray">
        <section class="workspace" aria-label="工作区">
          <div class="left-stack">
            <section class="panel pad">
              <div class="panel-title">
                <b>内容核心</b>
                <span>STRUCTURED INPUT</span>
              </div>

              <div class="field-grid">
                <div class="segmented" aria-label="生成方式">
                  <button class="segment" :class="{ active: generationMode === 'topic' }" type="button" @click="generationMode = 'topic'">主题生成</button>
                  <button class="segment" type="button" disabled title="真实 AI 文章生成后续接入">文章生成</button>
                </div>

                <template v-if="generationMode === 'topic'">
                  <div class="field">
                    <label for="topic">主题标题 <strong>*</strong></label>
                    <input id="topic" v-model="topicInput.title" class="text-input" />
                  </div>
                  <div class="field">
                    <label for="context">副标题 / 上下文</label>
                    <input id="context" v-model="topicInput.subtitle" class="small-input" />
                  </div>
                  <div class="field">
                    <label for="prompt">补充说明</label>
                    <textarea id="prompt" v-model="topicInput.instructions" class="prompt-area" />
                  </div>
                </template>

                <template v-else>
                  <div class="field">
                    <label for="article">文章正文 <strong>*</strong></label>
                    <textarea id="article" v-model="articleInput.body" class="prompt-area article-text" />
                  </div>
                </template>
              </div>
            </section>

            <section class="panel pad">
              <div class="panel-title">
                <b>输出设置</b>
                <span>FORMAT</span>
              </div>

              <div class="engines" aria-label="出图方式">
                <button class="engine-card" :class="{ active: renderMode === 'precise_card' }" type="button" @click="renderMode = 'precise_card'">
                  <span class="engine-name">精准卡片</span>
                  <span class="engine-desc">HTML 模板渲染，文字准确。</span>
                </button>
                <button class="engine-card" :class="{ active: renderMode === 'ai_creative_image' }" type="button" @click="renderMode = 'ai_creative_image'">
                  <span class="engine-name">AI 创意图</span>
                  <span class="engine-desc">MiniMax 生图，适合文章插图和氛围图。</span>
                </button>
                <button class="engine-card" :class="{ active: renderMode === 'ai_knowledge_poster' }" type="button" @click="renderMode = 'ai_knowledge_poster'">
                  <span class="engine-name">AI 知识海报</span>
                  <span class="engine-desc">一次生成带标题要点的完整卡片海报。</span>
                </button>
              </div>

              <div class="formats">
                <button class="format-card" :class="{ active: outputFormat === 'xhs_3_4' }" type="button" @click="outputFormat = 'xhs_3_4'">
                  <span class="format-icon"><img :src="xiaohongshuIcon" alt="小红书" /></span>
                  <span class="format-title">小红书</span>
                  <span class="format-ratio">{{ FORMAT_RATIO_HINTS.xhs_3_4 }}</span>
                </button>
                <button class="format-card" :class="{ active: outputFormat === 'douyin_9_16' }" type="button" @click="outputFormat = 'douyin_9_16'">
                  <span class="format-icon"><img :src="douyinIcon" alt="抖音" /></span>
                  <span class="format-title">抖音封面</span>
                  <span class="format-ratio">{{ FORMAT_RATIO_HINTS.douyin_9_16 }}</span>
                </button>
                <button class="format-card" :class="{ active: outputFormat === 'bilibili_16_9' }" type="button" @click="outputFormat = 'bilibili_16_9'">
                  <span class="format-icon"><img :src="bilibiliIcon" alt="B站" /></span>
                  <span class="format-title">B站封面</span>
                  <span class="format-ratio">{{ FORMAT_RATIO_HINTS.bilibili_16_9 }}</span>
                </button>
                <button class="format-card" :class="{ active: outputFormat === 'youtube_16_9' }" type="button" @click="outputFormat = 'youtube_16_9'">
                  <span class="format-icon"><img :src="youtubeIcon" alt="YouTube" /></span>
                  <span class="format-title">YouTube</span>
                  <span class="format-ratio">{{ FORMAT_RATIO_HINTS.youtube_16_9 }}</span>
                </button>
              </div>
            </section>
          </div>

          <aside class="right-stack">
            <section class="panel preview-card preview-card-compact">
              <div class="preview-header">
                <span>{{ hasRenderedImage ? "生成结果" : "实时预览" }}</span>
                <span>{{ FORMAT_RATIO_HINTS[outputFormat] }}</span>
              </div>
              <div class="preview-stage preview-stage-compact">
                <button v-if="hasRenderedImage && currentPreviewUrl" class="generated-preview" type="button" @click="openPreview(currentPreviewUrl)">
                  <img :src="currentPreviewUrl" :alt="`${currentProject?.title ?? '生成卡片'}预览图`" />
                </button>

                <article v-else class="mock-card" :class="previewRatioClass">
                  <div class="mock-label">知识卡片 01</div>
                  <h3>{{ topicInput.title }}</h3>
                  <p>{{ topicInput.subtitle || "填写主题后点击生成卡片" }}</p>
                  <div class="mock-footer">
                    <span>CardFlow AI</span>
                    <span>01 / 01</span>
                  </div>
                </article>

                <p class="preview-hint">
                  {{ isGenerating ? "正在生成，请稍候…" : hasRenderedImage ? "点击预览大图或下载 PNG" : "生成后在这里查看成品图" }}
                </p>

                <button v-if="hasRenderedImage && currentPreviewUrl" class="preview-link" type="button" @click="openPreview(currentPreviewUrl)">预览大图</button>

                <div v-if="hasRenderedImage && currentProject?.pages.length" class="page-links">
                  <button
                    v-for="page in currentProject.pages"
                    :key="page.id"
                    type="button"
                    @click="openPreview(page.imageUrl)"
                  >
                    第 {{ page.pageIndex + 1 }} 页
                  </button>
                </div>
              </div>
            </section>

            <section class="panel pad style-panel">
              <div class="panel-title">
                <b>封面风格</b>
                <span>STYLE</span>
              </div>

              <div v-if="templates.length > 0" class="style-grid">
                <button
                  v-for="(template, index) in templates"
                  :key="template.id"
                  class="style-tile"
                  :class="{ active: templateId === template.id }"
                  type="button"
                  :title="template.description"
                  @click="templateId = template.id"
                >
                  <span class="style-tile-icon" aria-hidden="true">
                    <component :is="styleTileIcon(index)" :size="18" :stroke-width="2" />
                  </span>
                  <span class="style-tile-label">{{ template.name }}</span>
                </button>
              </div>
              <div v-else class="empty-state style-empty">正在加载可用风格…</div>

              <p class="style-hint">风格列表来自模板配置，后续会随提示词与 Skill 动态扩展。</p>
            </section>
          </aside>
        </section>
      </div>
    </template>

    <template v-else>
      <section class="records-hero" aria-label="生成记录">
        <header class="headline records-headline">
          <h2>生成记录</h2>
          <p class="subline">查看历史作品，预览或删除已生成的卡片。</p>
        </header>
      </section>

      <div class="workspace-tray">
        <section class="panel pad records-panel">
          <div class="panel-title">
            <b>全部作品</b>
            <span>{{ recordsSummary }}</span>
          </div>

          <div class="records-filter" aria-label="按平台筛选">
            <button
              v-for="tab in RECORDS_FILTER_TABS"
              :key="tab.id"
              class="chip"
              :class="{ active: recordsFilter === tab.id }"
              type="button"
              @click="selectRecordsFilter(tab.id)"
            >
              {{ tab.label }}
            </button>
          </div>

          <template v-if="projects.length === 0">
            <div class="empty-state">暂无生成记录，回到首页生成一张卡片。</div>
          </template>

          <template v-else-if="recordsFilter === 'all'">
            <section
              v-for="section in recordSections"
              :key="section.filter"
              class="records-section"
              :aria-label="section.label"
            >
              <div class="records-section-header">
                <h3>{{ section.label }}</h3>
                <span>{{ section.items.length }} 张</span>
                <button
                  v-if="section.hiddenCount > 0"
                  class="ghost-button records-section-more"
                  type="button"
                  @click="selectRecordsFilter(section.filter)"
                >
                  查看全部 {{ section.items.length }} 张
                </button>
              </div>
              <p v-if="section.hiddenCount > 0" class="records-section-hint">
                超过 {{ SECTION_PREVIEW_MAX }} 张时，预览区展示最近 {{ SECTION_PREVIEW_OVERFLOW }} 张
              </p>
              <div class="records-grid">
                <article v-for="project in section.previewItems" :key="project.id" class="record-card">
                  <button
                    class="record-card-cover"
                    :class="section.coverClass"
                    type="button"
                    @click="openProjectPreview(project)"
                  >
                    <img v-if="project.coverUrl" :src="project.coverUrl" :alt="project.title" />
                    <span v-else class="record-card-placeholder">无预览</span>
                  </button>
                  <div class="record-card-body">
                    <strong>{{ project.title }}</strong>
                    <small>{{ project.status }} · {{ formatRatioLabel(project.ratio) }}</small>
                    <div class="record-card-actions">
                      <button class="ghost-button" type="button" @click="loadProject(project)">打开</button>
                      <button class="ghost-button" type="button" @click="openProjectPreview(project)">预览</button>
                      <button class="delete-button" type="button" @click="removeProject(project.id)">删除</button>
                    </div>
                  </div>
                </article>
              </div>
            </section>
          </template>

          <template v-else>
            <div v-if="filteredProjects.length > 0" class="records-grid">
              <article v-for="project in paginatedProjects" :key="project.id" class="record-card">
                <button
                  class="record-card-cover"
                  :class="activeFilterCoverClass"
                  type="button"
                  @click="openProjectPreview(project)"
                >
                  <img v-if="project.coverUrl" :src="project.coverUrl" :alt="project.title" />
                  <span v-else class="record-card-placeholder">无预览</span>
                </button>
                <div class="record-card-body">
                  <strong>{{ project.title }}</strong>
                  <small>{{ project.status }} · {{ formatRatioLabel(project.ratio) }}</small>
                  <div class="record-card-actions">
                    <button class="ghost-button" type="button" @click="loadProject(project)">打开</button>
                    <button class="ghost-button" type="button" @click="openProjectPreview(project)">预览</button>
                    <button class="delete-button" type="button" @click="removeProject(project.id)">删除</button>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state">当前平台暂无作品，试试切换其他平台或回到首页生成。</div>

            <nav v-if="recordsTotalPages > 1" class="records-pagination" aria-label="作品分页">
              <button class="ghost-button" type="button" :disabled="recordsPage <= 1" @click="goRecordsPage(recordsPage - 1)">
                上一页
              </button>
              <span class="records-pagination-label">第 {{ recordsPage }} / {{ recordsTotalPages }} 页</span>
              <button
                class="ghost-button"
                type="button"
                :disabled="recordsPage >= recordsTotalPages"
                @click="goRecordsPage(recordsPage + 1)"
              >
                下一页
              </button>
            </nav>
          </template>
        </section>
      </div>
    </template>
  </main>

  <div v-if="showBottomBar" class="bottom-bar">
    <div class="quota">
      <span><b>●</b> 免费额度 {{ usageSummary.remainingToday }}</span>
      <span>每次生成消耗 1 次</span>
    </div>
    <div class="bottom-status" :class="{ 'is-error': statusIsError }">{{ statusMessage }}</div>
    <button class="generate-button" type="button" :disabled="isBusy" @click="runGeneration">
      {{ primaryButtonLabel }}
    </button>
  </div>

  <div v-if="isPreviewModalOpen && activePreviewUrl" class="preview-modal" role="dialog" aria-modal="true" aria-label="生成图片预览" @click.self="closePreview">
    <div class="preview-modal-panel">
      <div class="preview-modal-header">
        <span>{{ activePreviewTitle }}</span>
        <div class="preview-modal-actions">
          <button type="button" @click="downloadPreview">下载 PNG</button>
          <button type="button" @click="closePreview">关闭</button>
        </div>
      </div>
      <div class="preview-modal-stage">
        <img :src="activePreviewUrl" alt="生成图片预览" />
      </div>
    </div>
  </div>
</template>

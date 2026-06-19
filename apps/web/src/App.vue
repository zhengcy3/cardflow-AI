<script setup lang="ts">
import { computed, ref } from "vue";
import {
  createProject,
  deleteProject,
  generateContent,
  getUsageSummary,
  listProjects,
  renderProject,
  type GenerationMode,
  type OutputFormat,
  type ProjectResponse,
  type RenderMode
} from "./api/client";

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

const contentJson = ref("");
const currentProject = ref<ProjectResponse | null>(null);
const projects = ref<ProjectResponse[]>([]);
const isGenerating = ref(false);
const isPreviewModalOpen = ref(false);
const activePreviewUrl = ref<string | null>(null);
const statusMessage = ref("准备生成");
const usageSummary = ref({ dailyQuota: 10, usedToday: 0, remainingToday: 10 });

const parsedContent = computed(() => {
  if (!contentJson.value) return null;
  try {
    return JSON.parse(contentJson.value) as { title?: string; subtitle?: string; summary?: string; points?: string[] };
  } catch {
    return null;
  }
});

const previewTitle = computed(() => parsedContent.value?.title ?? topicInput.value.title);
const previewSummary = computed(() => parsedContent.value?.summary ?? "消费知识不等于成长，真正改变结果的是行动反馈闭环。");
const previewPoints = computed(() => parsedContent.value?.points ?? ["收藏不等于行动", "知道不等于做到", "输入不等于输出"]);
const previewPageLabel = computed(() => {
  const total = currentProject.value?.pages.length;
  return total && total > 1 ? `01 / ${String(total).padStart(2, "0")}` : "01 / 01";
});
const previewRatioClass = computed(() => ({
  "mock-card-wide": outputFormat.value === "youtube_16_9" || outputFormat.value === "bilibili_16_9",
  "mock-card-vertical": outputFormat.value === "douyin_9_16"
}));
const currentPreviewUrl = computed(() => currentProject.value?.coverUrl ?? null);

function openPreview(url?: string | null) {
  if (!url) return;
  activePreviewUrl.value = url;
  isPreviewModalOpen.value = true;
}

function closePreview() {
  isPreviewModalOpen.value = false;
  activePreviewUrl.value = null;
}

function openProjectPreview(project: ProjectResponse) {
  currentProject.value = project;
  openPreview(project.coverUrl);
}

async function runGeneration() {
  if (renderMode.value !== "precise_card") {
    statusMessage.value = "AI 创意图和混合模式将在 V1 实现";
    return;
  }

  isGenerating.value = true;
  statusMessage.value = "生成结构化内容中";

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
    const title = parsedContent.value?.title ?? topicInput.value.title;
    statusMessage.value = "保存作品草稿";

    currentProject.value = await createProject({
      title,
      type: generationMode.value === "article" ? "carousel_card" : "single_card",
      ratio: outputFormat.value,
      renderMode: renderMode.value,
      templateId: templateId.value,
      contentJson: generated.contentJson
    });

    statusMessage.value = "生成本地预览图";
    await renderProject(currentProject.value.id);
    projects.value = await listProjects();
    usageSummary.value = await getUsageSummary();
    currentProject.value = projects.value.find((project) => project.id === currentProject.value?.id) ?? currentProject.value;
    statusMessage.value = "生成完成";
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : "生成失败";
  } finally {
    isGenerating.value = false;
  }
}

void listProjects().then((items) => {
  projects.value = items;
});

void getUsageSummary().then((summary) => {
  usageSummary.value = summary;
});

async function removeProject(projectId: string) {
  await deleteProject(projectId);
  projects.value = await listProjects();
  if (currentProject.value?.id === projectId) {
    currentProject.value = null;
    closePreview();
  }
}

</script>

<template>
  <div class="ambient-motion" aria-hidden="true">
    <i class="ambient-light ambient-light-violet" />
    <i class="ambient-light ambient-light-rose" />
    <i class="ambient-light ambient-light-cyan" />
  </div>

  <main class="page">
    <nav class="topbar" aria-label="主导航">
      <div class="brand">
        <div class="logo" aria-hidden="true"><span /></div>
        <div class="brand-name">CardFlow AI</div>
      </div>
      <div class="nav">
        <button class="status-pill" type="button"><span class="dot" /> 系统正常</button>
        <button class="nav-item" type="button">生成记录</button>
        <button class="nav-item" type="button">使用记录</button>
        <button class="nav-item" type="button">模板中心</button>
      </div>
      <div class="account">
        <div class="avatar" aria-label="用户头像" />
        <button class="user-pill" type="button">免费版</button>
        <button class="menu-button" type="button" aria-label="打开菜单">≡</button>
      </div>
    </nav>

    <section class="hero" aria-label="AI 生成器">
      <header class="headline">
        <div class="engine"><span class="dot" /> ENGINE V1.0 已就绪</div>
        <h1>打造<span>高转化</span>知识卡片</h1>
        <p class="subline">输入主题或文章，30 秒生成适合小红书发布的知识卡片。</p>
      </header>
    </section>

    <section class="workspace">
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
              <div class="option-row">
                <label>提炼目标</label>
                <div class="chips">
                  <button class="chip" :class="{ active: articleInput.extractionGoal === 'summarize' }" type="button" @click="articleInput.extractionGoal = 'summarize'">总结观点</button>
                  <button class="chip" :class="{ active: articleInput.extractionGoal === 'carousel' }" type="button" @click="articleInput.extractionGoal = 'carousel'">拆成轮播</button>
                  <button class="chip" :class="{ active: articleInput.extractionGoal === 'cover_copy' }" type="button" @click="articleInput.extractionGoal = 'cover_copy'">生成封面文案</button>
                </div>
              </div>
              <div class="option-row">
                <label>生成页数</label>
                <div class="chips">
                  <button v-for="count in ['auto', '3', '5', '7']" :key="count" class="chip" :class="{ active: articleInput.pageCount === count }" type="button" @click="articleInput.pageCount = count">
                    {{ count === "auto" ? "自动" : `${count} 页` }}
                  </button>
                </div>
              </div>
              <div class="option-row">
                <label>内容风格</label>
                <div class="chips">
                  <button class="chip" :class="{ active: articleInput.style === 'concise' }" type="button" @click="articleInput.style = 'concise'">专业简洁</button>
                  <button class="chip" :class="{ active: articleInput.style === 'xiaohongshu' }" type="button" @click="articleInput.style = 'xiaohongshu'">小红书风</button>
                  <button class="chip" :class="{ active: articleInput.style === 'knowledge_commerce' }" type="button" @click="articleInput.style = 'knowledge_commerce'">知识付费风</button>
                </div>
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
              <span class="engine-desc">V1：封面视觉和氛围图。</span>
            </button>
            <button class="engine-card" :class="{ active: renderMode === 'hybrid' }" type="button" @click="renderMode = 'hybrid'">
              <span class="engine-name">混合模式</span>
              <span class="engine-desc">V1：AI 背景 + HTML 文案。</span>
            </button>
          </div>

          <div class="formats">
            <button class="format-card" :class="{ active: outputFormat === 'xhs_3_4' }" type="button" @click="outputFormat = 'xhs_3_4'">
              <span class="format-icon">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M22.405 9.879c.538 4.757-1.374 9.064-4.577 12.186-3.834 3.738-9.066 5.378-13.882 3.913-1.638-.501-3.21-1.28-4.63-2.315.11-.08.22-.16.33-.24 3.123 2.148 6.945 2.753 10.518 1.638 3.864-1.18 6.772-4.148 7.747-8.081.706-2.836.425-5.696-.757-8.318.599.362 1.155.77 1.664 1.222 1.353 1.206 2.213 2.809 2.587 4.615zM12.016 0c4.135.035 8.164 1.768 11.096 4.708 2.934 2.943 4.673 6.974 4.708 11.11-.035 4.136-1.774 8.167-4.708 11.11-2.932 2.94-6.961 4.673-11.096 4.708-4.135-.035-8.164-1.768-11.096-4.708C1.774 23.985.035 19.954 0 15.818.035 11.682 1.774 7.651 4.708 4.708 7.64 1.768 11.669.035 15.804 0h.212z"/>
                </svg>
              </span>
              <span class="format-title">小红书</span>
              <span class="format-ratio">单页 / 轮播</span>
            </button>
            <button class="format-card" :class="{ active: outputFormat === 'youtube_16_9' }" type="button" @click="outputFormat = 'youtube_16_9'">
              <span class="format-icon">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M21.543 6.498C22 8.21 22 12 22 12s0 3.79-.457 5.502a2.502 2.502 0 0 1-1.76 1.76C18.07 19.722 12 19.722 12 19.722s-6.07 0-7.783-.46a2.502 2.502 0 0 1-1.76-1.76C2 15.79 2 12 2 12s0-3.79.457-5.502a2.502 2.502 0 0 1 1.76-1.76C5.93 4.278 12 4.278 12 4.278s6.07 0 7.783.46a2.502 2.502 0 0 1 1.76 1.76zM9.75 8.75v6.5L15.5 12l-5.75-3.25z"/>
                </svg>
              </span>
              <span class="format-title">YouTube</span>
              <span class="format-ratio">横版封面</span>
            </button>
            <button class="format-card" :class="{ active: outputFormat === 'bilibili_16_9' }" type="button" @click="outputFormat = 'bilibili_16_9'">
              <span class="format-icon">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M18.5 5.5h-2.17l1.7-1.7a.75.75 0 1 0-1.06-1.06L14.38 5.33A.75.75 0 0 0 14.5 5.5H9.5a.75.75 0 0 0 .12-.17L7.03 2.74a.75.75 0 1 0-1.06 1.06l1.7 1.7H5.5A3.5 3.5 0 0 0 2 9v8a3.5 3.5 0 0 0 3.5 3.5h13A3.5 3.5 0 0 0 22 17V9a3.5 3.5 0 0 0-3.5-3.5zm2 11.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2v8zM7.5 10.5h2v2h-2zm7 0h2v2h-2z"/>
                </svg>
              </span>
              <span class="format-title">B站封面</span>
              <span class="format-ratio">横版视频</span>
            </button>
            <button class="format-card" :class="{ active: outputFormat === 'douyin_9_16' }" type="button" @click="outputFormat = 'douyin_9_16'">
              <span class="format-icon">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M19.497 7.915a5.538 5.538 0 0 1-3.412-1.161V14.5a6.5 6.5 0 1 1-6.5-6.5c.348 0 .684.032 1.012.088V12.18A4.478 4.478 0 1 0 14 14.5V2.5h3.987a5.526 5.526 0 0 0 1.51 5.415z"/>
                </svg>
              </span>
              <span class="format-title">抖音封面</span>
              <span class="format-ratio">竖屏视频</span>
            </button>
          </div>
        </section>

      </div>

      <aside class="right-stack">
        <section class="panel preview-card">
          <div class="preview-header">
            <span>实时预览</span>
            <span>{{ outputFormat }}</span>
          </div>
          <div class="preview-stage">
            <button v-if="currentPreviewUrl" class="generated-preview" type="button" @click="openPreview(currentPreviewUrl)">
              <img :src="currentPreviewUrl" :alt="`${currentProject?.title ?? '生成卡片'}预览图`" />
            </button>
            <article v-else class="mock-card" :class="previewRatioClass">
              <div class="mock-label">知识卡片 01</div>
              <h3>{{ previewTitle }}</h3>
              <p>{{ previewSummary }}</p>
              <div class="mock-points">
                <div v-for="point in previewPoints" :key="point" class="mock-point"><i /><span>{{ point }}</span></div>
              </div>
              <div class="mock-footer">
                <span>CardFlow AI</span>
                <span>{{ previewPageLabel }}</span>
              </div>
            </article>
            <button v-if="currentPreviewUrl" class="preview-link" type="button" @click="openPreview(currentPreviewUrl)">预览大图</button>
            <div v-if="currentProject?.pages.length" class="page-links">
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

        <section class="panel pad">
          <div class="panel-title">
            <b>最近作品</b>
            <span>{{ projects.length }} ITEMS</span>
          </div>
          <div class="works">
            <div v-for="project in projects.slice(0, 4)" :key="project.id" class="work-item">
              <button class="work-preview-button" type="button" @click="openProjectPreview(project)">
                <span>{{ project.title }}</span>
                <small>{{ project.status }}</small>
              </button>
              <button class="delete-button" type="button" @click="removeProject(project.id)">删除</button>
            </div>
            <div v-if="projects.length === 0" class="empty-state">暂无作品，先生成一张卡片。</div>
          </div>
        </section>
      </aside>
    </section>
  </main>

  <div class="bottom-bar">
    <div class="quota">
      <span><b>●</b> 免费额度 {{ usageSummary.remainingToday }}</span>
      <span><b>1</b> 次消耗</span>
    </div>
    <button class="generate-button" type="button" :disabled="isGenerating" @click="runGeneration">
      {{ isGenerating ? "生成中..." : "生成卡片 →" }}
    </button>
  </div>

  <div v-if="isPreviewModalOpen && activePreviewUrl" class="preview-modal" role="dialog" aria-modal="true" aria-label="生成图片预览" @click.self="closePreview">
    <div class="preview-modal-panel">
      <div class="preview-modal-header">
        <span>{{ currentProject?.title ?? "生成图片预览" }}</span>
        <button type="button" @click="closePreview">关闭</button>
      </div>
      <div class="preview-modal-stage">
        <img :src="activePreviewUrl" alt="生成图片预览" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import {
  createProject,
  deleteProject,
  generateContent,
  getUsageSummary,
  listProjects,
  renderProject,
  updateProjectContent,
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
const statusMessage = ref("准备生成");
const usageSummary = ref({ dailyQuota: 10, usedToday: 0, remainingToday: 10 });
const isSavingContent = ref(false);

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
  }
}

async function saveContentAndRender() {
  if (!currentProject.value) {
    statusMessage.value = "请先生成一个作品";
    return;
  }

  try {
    JSON.parse(contentJson.value);
  } catch {
    statusMessage.value = "JSON 格式不正确，请检查后再保存";
    return;
  }

  isSavingContent.value = true;
  statusMessage.value = "保存编辑内容";
  try {
    currentProject.value = await updateProjectContent(currentProject.value.id, contentJson.value);
    statusMessage.value = "重新渲染图片";
    await renderProject(currentProject.value.id);
    projects.value = await listProjects();
    currentProject.value = projects.value.find((project) => project.id === currentProject.value?.id) ?? currentProject.value;
    statusMessage.value = "编辑已保存";
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : "保存失败";
  } finally {
    isSavingContent.value = false;
  }
}
</script>

<template>
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
        <div class="helper">当前状态：{{ statusMessage }}</div>
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
              <button class="segment" :class="{ active: generationMode === 'article' }" type="button" @click="generationMode = 'article'">文章生成</button>
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
              <span class="format-icon">3:4</span>
              <span class="format-title">小红书</span>
              <span class="format-ratio">单页 / 轮播</span>
            </button>
            <button class="format-card" :class="{ active: outputFormat === 'youtube_16_9' }" type="button" @click="outputFormat = 'youtube_16_9'">
              <span class="format-icon">16:9</span>
              <span class="format-title">YouTube</span>
              <span class="format-ratio">横版封面</span>
            </button>
            <button class="format-card" :class="{ active: outputFormat === 'bilibili_16_9' }" type="button" @click="outputFormat = 'bilibili_16_9'">
              <span class="format-icon">16:9</span>
              <span class="format-title">B站封面</span>
              <span class="format-ratio">横版视频</span>
            </button>
            <button class="format-card" :class="{ active: outputFormat === 'douyin_9_16' }" type="button" @click="outputFormat = 'douyin_9_16'">
              <span class="format-icon">9:16</span>
              <span class="format-title">抖音封面</span>
              <span class="format-ratio">竖屏视频</span>
            </button>
          </div>
        </section>

        <section v-if="contentJson" class="panel pad">
          <div class="panel-title">
            <b>内容编辑</b>
            <span>CONTENT JSON</span>
          </div>
          <textarea v-model="contentJson" class="prompt-area content-json-editor" spellcheck="false" />
          <div class="editor-actions">
            <button class="secondary-button" type="button" :disabled="isSavingContent" @click="contentJson = currentProject?.contentJson ?? contentJson">恢复</button>
            <button class="primary-button" type="button" :disabled="isSavingContent" @click="saveContentAndRender">
              {{ isSavingContent ? "保存中..." : "保存并重新渲染" }}
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
            <article class="mock-card" :class="previewRatioClass">
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
            <a v-if="currentProject?.coverUrl" class="preview-link" :href="currentProject.coverUrl" target="_blank">打开生成预览</a>
            <div v-if="currentProject?.pages.length" class="page-links">
              <a
                v-for="page in currentProject.pages"
                :key="page.id"
                :href="page.imageUrl ?? '#'"
                target="_blank"
              >
                第 {{ page.pageIndex + 1 }} 页
              </a>
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
              <a :href="project.coverUrl ?? '#'" target="_blank">
                <span>{{ project.title }}</span>
                <small>{{ project.status }}</small>
              </a>
              <button type="button" @click="removeProject(project.id)">删除</button>
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
</template>

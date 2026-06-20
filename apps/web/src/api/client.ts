export type GenerationMode = "topic" | "article";
export type RenderMode = "precise_card" | "ai_creative_image" | "hybrid";
export type OutputFormat = "xhs_3_4" | "youtube_16_9" | "bilibili_16_9" | "douyin_9_16";

export interface GenerateContentPayload {
  generationMode: GenerationMode;
  outputFormat: OutputFormat;
  renderMode: RenderMode;
  templateId: string;
  topicInput?: {
    title: string;
    subtitle: string;
    instructions: string;
  };
  articleInput?: {
    body: string;
    extractionGoal: string;
    pageCount: string;
    style: string;
  };
}

export interface GenerateContentResponse {
  taskId: string;
  contentJson: string;
}

export interface ProjectResponse {
  id: string;
  title: string;
  type: string;
  ratio: OutputFormat;
  renderMode: RenderMode;
  templateId: string;
  contentJson: string;
  coverUrl: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
  pages: CardPageResponse[];
}

export interface CardPageResponse {
  id: string;
  pageIndex: number;
  role: string;
  contentJson: string;
  imageUrl: string | null;
  createdAt: string;
}

export interface UsageSummaryResponse {
  dailyQuota: number;
  usedToday: number;
  remainingToday: number;
}

export interface TemplateResponse {
  id: string;
  name: string;
  description: string;
  styleKey: string;
  supportedTypes: string;
  supportedRatios: string;
}

interface ApiErrorBody {
  message?: string;
}

const DEFAULT_ERROR_MESSAGE = "操作失败，请稍后重试。";

async function readApiError(response: Response): Promise<string> {
  const text = (await response.text()).trim();
  if (!text) {
    return DEFAULT_ERROR_MESSAGE;
  }

  try {
    const body = JSON.parse(text) as ApiErrorBody;
    if (body.message?.trim()) {
      return sanitizeClientMessage(body.message.trim());
    }
  } catch {
    // 非 JSON 响应
  }

  if (text.startsWith("{") || text.startsWith("[") || text.includes("\"error\"")) {
    return DEFAULT_ERROR_MESSAGE;
  }

  if (text.includes("\n\tat ") || text.includes("Caused by:")) {
    return DEFAULT_ERROR_MESSAGE;
  }

  return sanitizeClientMessage(text.length > 160 ? DEFAULT_ERROR_MESSAGE : text);
}

function sanitizeClientMessage(message: string): string {
  const lower = message.toLowerCase();
  if (lower.includes("authentication") || lower.includes("api key") || lower.includes("401")) {
    return "AI 服务认证失败，请检查 DEEPSEEK_API_KEY 是否配置正确。";
  }
  if (message.startsWith("{") || message.startsWith("[") || message.includes("\"error\"")) {
    return DEFAULT_ERROR_MESSAGE;
  }
  return message;
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options?.headers ?? {})
    },
    ...options
  });

  if (!response.ok) {
    throw new Error(await readApiError(response));
  }

  return response.json() as Promise<T>;
}

export async function generateContent(payload: GenerateContentPayload) {
  return request<GenerateContentResponse>("/api/generations/content", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function createProject(payload: {
  title: string;
  type: string;
  ratio: OutputFormat;
  renderMode: RenderMode;
  templateId: string;
  contentJson: string;
}) {
  return request<ProjectResponse>("/api/projects", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function renderProject(projectId: string) {
  return request<{ taskId: string; imageUrls: string[] }>(`/api/projects/${projectId}/render`, {
    method: "POST"
  });
}

export async function updateProjectContent(projectId: string, contentJson: string) {
  return request<ProjectResponse>(`/api/projects/${projectId}/content`, {
    method: "PUT",
    body: JSON.stringify({ contentJson })
  });
}

export async function listProjects() {
  return request<ProjectResponse[]>("/api/projects");
}

export async function listTemplates() {
  return request<TemplateResponse[]>("/api/templates");
}

export async function getUsageSummary() {
  return request<UsageSummaryResponse>("/api/usage/summary");
}

export async function deleteProject(projectId: string) {
  const response = await fetch(`/api/projects/${projectId}`, {
    method: "DELETE"
  });

  if (!response.ok) {
    throw new Error(await readApiError(response));
  }
}

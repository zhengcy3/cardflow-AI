package ai.cardflow.api.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * API 层请求和响应 DTO 集合。
 *
 * <p>MVP 阶段 DTO 数量较少，统一放在一个文件中，便于控制接口契约和前端对接。</p>
 */
public final class ApiModels {
  /** 阻止工具类被实例化。 */
  private ApiModels() {}

  /**
   * 当前默认本地用户信息。
   *
   * @param id 用户 ID
   * @param nickname 昵称
   * @param plan 当前套餐
   * @param dailyQuota 每日生成额度
   */
  public record MeResponse(String id, String nickname, String plan, int dailyQuota) {}

  /**
   * 模板列表和详情响应。
   *
   * @param id 模板 ID
   * @param name 展示名称
   * @param description 简短描述
   * @param styleKey 模板风格标识
   * @param supportedTypes 支持的作品类型，逗号分隔
   * @param supportedRatios 支持的输出比例，逗号分隔
   */
  public record TemplateResponse(
    String id,
    String name,
    String description,
    String styleKey,
    String supportedTypes,
    String supportedRatios
  ) {}

  /**
   * 请求 LLM 生成结构化内容。
   *
   * @param generationMode 生成模式，当前支持 topic 或 article
   * @param outputFormat 输出平台比例标识
   * @param renderMode 渲染模式，MVP 当前主要使用 precise_card
   * @param templateId 选中的模板 ID
   * @param topicInput 主题生成输入
   * @param articleInput 长文拆解输入
   */
  public record GenerateContentRequest(
    @NotBlank String generationMode,
    @NotBlank String outputFormat,
    @NotBlank String renderMode,
    @NotBlank String templateId,
    TopicInput topicInput,
    ArticleInput articleInput
  ) {}

  /**
   * 主题生成输入。
   *
   * @param title 主题标题
   * @param subtitle 副标题或观点补充
   * @param instructions 用户补充要求
   */
  public record TopicInput(String title, String subtitle, String instructions) {}

  /**
   * 长文拆解输入。
   *
   * @param body 原始文章正文
   * @param extractionGoal 拆解目标，例如 summarize、carousel 或 cover_copy
   * @param pageCount 期望页数
   * @param style 内容风格
   */
  public record ArticleInput(String body, String extractionGoal, String pageCount, String style) {}

  /**
   * 内容生成响应。
   *
   * @param taskId 生成任务 ID
   * @param contentJson LLM 返回的结构化卡片 JSON 字符串
   */
  public record GenerateContentResponse(String taskId, String contentJson) {}

  /**
   * 创建作品请求。
   *
   * @param title 作品标题
   * @param type 作品类型，例如 single_card 或 carousel_card
   * @param ratio 输出比例标识
   * @param renderMode 渲染模式
   * @param templateId 模板 ID
   * @param contentJson 可编辑的结构化内容 JSON
   */
  public record CreateProjectRequest(
    @NotBlank String title,
    @NotBlank String type,
    @NotBlank String ratio,
    @NotBlank String renderMode,
    @NotBlank String templateId,
    @NotBlank String contentJson
  ) {}

  /**
   * 更新作品内容请求。
   *
   * @param contentJson 前端编辑后的结构化内容 JSON
   */
  public record UpdateProjectContentRequest(@NotBlank String contentJson) {}

  /**
   * 作品详情响应。
   *
   * @param id 作品 ID
   * @param title 作品标题
   * @param type 作品类型
   * @param ratio 输出比例标识
   * @param renderMode 渲染模式
   * @param templateId 模板 ID
   * @param contentJson 当前结构化内容 JSON
   * @param coverUrl 封面图片 URL
   * @param status 作品状态
   * @param createdAt 创建时间
   * @param updatedAt 更新时间
   * @param pages 已渲染页面列表
   */
  public record ProjectResponse(
    String id,
    String title,
    String type,
    String ratio,
    String renderMode,
    String templateId,
    String contentJson,
    String coverUrl,
    String status,
    String createdAt,
    String updatedAt,
    List<CardPageResponse> pages
  ) {}

  /**
   * 已渲染单页响应。
   *
   * @param id 页面 ID
   * @param pageIndex 页面序号，从 0 开始
   * @param role 页面角色，例如 cover、point、summary
   * @param contentJson 该页参与渲染的内容 JSON
   * @param imageUrl PNG 静态资源 URL
   * @param createdAt 创建时间
   */
  public record CardPageResponse(
    String id,
    int pageIndex,
    String role,
    String contentJson,
    String imageUrl,
    String createdAt
  ) {}

  /**
   * 渲染作品响应。
   *
   * @param taskId 图片渲染任务 ID
   * @param imageUrls 本次渲染生成的图片 URL 列表
   */
  public record RenderProjectResponse(String taskId, List<String> imageUrls) {}

  /**
   * 任务状态响应。
   *
   * @param id 任务 ID
   * @param status 任务状态
   * @param errorMessage 失败原因，成功时为空
   */
  public record TaskResponse(String id, String status, String errorMessage) {}

  /**
   * 今日额度汇总。
   *
   * @param dailyQuota 每日额度
   * @param usedToday 今日已使用次数
   * @param remainingToday 今日剩余次数
   */
  public record UsageSummaryResponse(int dailyQuota, int usedToday, int remainingToday) {}

  /**
   * 单条用量记录。
   *
   * @param id 记录 ID
   * @param taskId 对应任务 ID
   * @param usageType 用量类型
   * @param amount 消耗数量
   * @param modelName Provider 或模型名称
   * @param createdAt 创建时间
   */
  public record UsageRecordResponse(
    String id,
    String taskId,
    String usageType,
    int amount,
    String modelName,
    String createdAt
  ) {}
}

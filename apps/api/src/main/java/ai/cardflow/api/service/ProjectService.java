package ai.cardflow.api.service;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.exception.ApiErrorMessages;
import ai.cardflow.api.image.AspectRatioMapper;
import ai.cardflow.api.image.CreativeImagePromptComposer;
import ai.cardflow.api.poster.KnowledgePosterPromptComposer;
import ai.cardflow.api.image.ImageGenerationRequest;
import ai.cardflow.api.image.ImageProvider;
import ai.cardflow.api.model.ApiModels.CardPageResponse;
import ai.cardflow.api.model.ApiModels.CreateProjectRequest;
import ai.cardflow.api.model.ApiModels.ProjectResponse;
import ai.cardflow.api.model.ApiModels.RenderProjectResponse;
import ai.cardflow.api.model.ApiModels.UpdateProjectContentRequest;
import ai.cardflow.api.model.RenderModels.RenderPage;
import ai.cardflow.api.render.HtmlTemplateRenderer;
import ai.cardflow.api.render.PlaywrightScreenshotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 作品管理和渲染服务。
 *
 * <p>负责作品 CRUD、结构化内容保存、HTML 渲染、Playwright 截图以及渲染任务记录。</p>
 */
@Service
public class ProjectService {
  private final JdbcTemplate jdbc;
  private final AppProperties properties;
  private final HtmlTemplateRenderer htmlTemplateRenderer;
  private final PlaywrightScreenshotService playwrightScreenshotService;
  private final ImageProvider imageProvider;
  private final ObjectMapper objectMapper;

  /**
   * 注入作品服务依赖。
   *
   * @param jdbc SQLite 访问入口
   * @param properties 应用配置
   * @param htmlTemplateRenderer HTML 模板渲染器
   * @param playwrightScreenshotService PNG 截图服务
   * @param imageProvider AI 生图 Provider
   * @param objectMapper JSON 解析器
   */
  public ProjectService(
    JdbcTemplate jdbc,
    AppProperties properties,
    HtmlTemplateRenderer htmlTemplateRenderer,
    PlaywrightScreenshotService playwrightScreenshotService,
    ImageProvider imageProvider,
    ObjectMapper objectMapper
  ) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.htmlTemplateRenderer = htmlTemplateRenderer;
    this.playwrightScreenshotService = playwrightScreenshotService;
    this.imageProvider = imageProvider;
    this.objectMapper = objectMapper;
  }

  /**
   * 创建作品草稿。
   *
   * @param request 创建作品请求
   * @return 创建后的作品详情
   */
  public ProjectResponse create(CreateProjectRequest request) {
    String id = UUID.randomUUID().toString();
    String now = Instant.now().toString();
    jdbc.update("""
      insert into card_project
      (id, user_id, title, type, ratio, render_mode, template_id, content_json, cover_url, status, created_at, updated_at)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """,
      id,
      properties.app().defaultUserId(),
      request.title(),
      request.type(),
      request.ratio(),
      request.renderMode(),
      request.templateId(),
      request.contentJson(),
      null,
      "draft",
      now,
      now
    );
    return get(id);
  }

  /**
   * 获取默认用户的作品列表。
   *
   * @return 按创建时间倒序排列的作品列表
   */
  public List<ProjectResponse> list() {
    return jdbc.query("""
      select id, title, type, ratio, render_mode, template_id, content_json, cover_url, status, created_at, updated_at
      from card_project
      where user_id = ?
      order by created_at desc
      """, (rs, rowNum) -> mapProject(rs), properties.app().defaultUserId());
  }

  /**
   * 获取单个作品详情。
   *
   * @param id 作品 ID
   * @return 作品详情
   */
  public ProjectResponse get(String id) {
    return jdbc.queryForObject("""
      select id, title, type, ratio, render_mode, template_id, content_json, cover_url, status, created_at, updated_at
      from card_project
      where id = ? and user_id = ?
      """, (rs, rowNum) -> mapProject(rs), id, properties.app().defaultUserId());
  }

  /**
   * 保存作品内容 JSON，并把状态恢复为 draft。
   *
   * @param id 作品 ID
   * @param request 更新内容请求
   * @return 更新后的作品详情
   */
  public ProjectResponse updateContent(String id, UpdateProjectContentRequest request) {
    String now = Instant.now().toString();
    jdbc.update("""
      update card_project
      set content_json = ?, status = ?, updated_at = ?
      where id = ? and user_id = ?
      """, request.contentJson(), "draft", now, id, properties.app().defaultUserId());
    return get(id);
  }

  /**
   * 删除作品及其从属页面和任务。
   *
   * @param id 作品 ID
   */
  public void delete(String id) {
    // SQLite 当前没有声明级联外键，业务层显式清理从属记录。
    jdbc.update("delete from card_page where project_id = ?", id);
    jdbc.update("delete from generate_task where project_id = ?", id);
    jdbc.update("delete from card_project where id = ? and user_id = ?", id, properties.app().defaultUserId());
  }

  /**
   * 将作品渲染为一组 PNG 图片。
   *
   * <p>每次渲染都会清空旧页面记录并重新截图，确保历史页面和当前 JSON 一致。</p>
   *
   * @param id 作品 ID
   * @return 渲染任务 ID 和图片 URL 列表
   */
  public RenderProjectResponse render(String id) {
    ProjectResponse project = get(id);
    String taskId = UUID.randomUUID().toString();
    String now = Instant.now().toString();
    // 输出目录按 userId/projectId 分组，避免不同作品的页面文件互相覆盖。
    Path outputDir = Path.of(properties.storage().outputDir(), properties.app().defaultUserId(), id).normalize();
    List<String> imageUrls = new ArrayList<>();

    try {
      Files.createDirectories(outputDir);
      // 重新渲染时先删除旧页面记录，随后按最新 JSON 重新写入。
      jdbc.update("delete from card_page where project_id = ?", id);
      if (isAiImageRenderMode(project.renderMode())) {
        imageUrls.add(renderAiImage(project, id, outputDir, now));
      } else {
        for (RenderPage page : htmlTemplateRenderer.pages(project)) {
          String fileName = "page-%02d.png".formatted(page.pageIndex() + 1);
          Path outputFile = outputDir.resolve(fileName);
          playwrightScreenshotService.screenshot(
            htmlTemplateRenderer.render(project, page),
            htmlTemplateRenderer.canvas(project.ratio()),
            outputFile
          );
          // URL 路径与 WebConfig 中的 /outputs/** 静态映射保持一致。
          String imageUrl = "/outputs/" + properties.app().defaultUserId() + "/" + id + "/" + fileName;
          imageUrls.add(imageUrl);
          jdbc.update("""
            insert into card_page (id, project_id, page_index, role, content_json, image_url, created_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID().toString(),
            id,
            page.pageIndex(),
            page.role(),
            page.contentJson(),
            imageUrl,
            now
          );
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(ApiErrorMessages.toUserMessage(e));
    } catch (RuntimeException e) {
      String userMessage = ApiErrorMessages.toUserMessage(e);
      markRenderFailed(taskId, id, project.contentJson(), now, userMessage);
      throw new IllegalStateException(userMessage);
    }

    // 列表页使用第一页作为封面；无页面时保持为空，避免返回不存在的图片地址。
    String coverUrl = imageUrls.isEmpty() ? null : imageUrls.get(0);
    jdbc.update("""
      insert into generate_task
      (id, user_id, project_id, task_type, input_text, input_params_json, status, error_message, started_at, finished_at, created_at)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """,
      taskId,
      properties.app().defaultUserId(),
      id,
      "image_render",
      project.contentJson(),
      "{}",
      "succeeded",
      null,
      now,
      now,
      now
    );
    jdbc.update("update card_project set cover_url = ?, status = ?, updated_at = ? where id = ?", coverUrl, "rendered", now, id);
    return new RenderProjectResponse(taskId, imageUrls);
  }

  private static boolean isAiImageRenderMode(String renderMode) {
    return "ai_creative_image".equals(renderMode) || "ai_knowledge_poster".equals(renderMode);
  }

  private String renderAiImage(ProjectResponse project, String projectId, Path outputDir, String now) throws IOException {
    JsonNode content = objectMapper.readTree(project.contentJson());
    String prompt = toAiImagePrompt(project.renderMode(), content, project.ratio());
    if (prompt.isBlank()) {
      throw new IllegalStateException("作品缺少生图 prompt，请重新生成内容。");
    }

    byte[] imageBytes = imageProvider.generate(toImageRequest(project.renderMode(), prompt, project.ratio()));

    String fileName = "page-01.png";
    Path outputFile = outputDir.resolve(fileName);
    Files.write(outputFile, imageBytes);

    String imageUrl = "/outputs/" + properties.app().defaultUserId() + "/" + projectId + "/" + fileName;
    jdbc.update("""
      insert into card_page (id, project_id, page_index, role, content_json, image_url, created_at)
      values (?, ?, ?, ?, ?, ?, ?)
      """,
      UUID.randomUUID().toString(),
      projectId,
      0,
      "cover",
      project.contentJson(),
      imageUrl,
      now
    );
    return imageUrl;
  }

  private ImageGenerationRequest toImageRequest(String renderMode, String prompt, String ratio) {
    String aspectRatio = AspectRatioMapper.fromOutputFormat(ratio);
    if ("ai_knowledge_poster".equals(renderMode)) {
      AppProperties.MiniMaxImage minimax = properties.image().minimax();
      return new ImageGenerationRequest(
        prompt,
        aspectRatio,
        minimax.posterPromptOptimizer(),
        minimax.posterModel()
      );
    }
    return new ImageGenerationRequest(prompt, aspectRatio);
  }

  private String toAiImagePrompt(String renderMode, JsonNode content, String ratio) {
    if ("ai_knowledge_poster".equals(renderMode)) {
      return KnowledgePosterPromptComposer.toMiniMaxPrompt(enrichPosterContent(content, ratio));
    }
    return CreativeImagePromptComposer.toMiniMaxPrompt(content);
  }

  private JsonNode enrichPosterContent(JsonNode content, String ratio) {
    if (!content.path("aspectRatio").asText("").isBlank()) {
      return content;
    }
    var enriched = ((com.fasterxml.jackson.databind.node.ObjectNode) content.deepCopy());
    enriched.put("aspectRatio", AspectRatioMapper.fromOutputFormat(ratio));
    return enriched;
  }

  /**
   * 记录渲染失败任务并更新作品状态。
   *
   * @param taskId 渲染任务 ID
   * @param projectId 作品 ID
   * @param contentJson 参与渲染的内容 JSON
   * @param now 任务开始时间
   * @param errorMessage 失败原因
   */
  private void markRenderFailed(String taskId, String projectId, String contentJson, String now, String errorMessage) {
    jdbc.update("""
      insert into generate_task
      (id, user_id, project_id, task_type, input_text, input_params_json, status, error_message, started_at, finished_at, created_at)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """,
      taskId,
      properties.app().defaultUserId(),
      projectId,
      "image_render",
      contentJson,
      "{}",
      "failed",
      errorMessage,
      now,
      now,
      now
    );
    jdbc.update("update card_project set status = ?, updated_at = ? where id = ?", "render_failed", now, projectId);
  }

  /**
   * 将 card_project 查询结果映射为 API 响应。
   *
   * @param rs 当前行结果集
   * @return 作品响应 DTO
   * @throws java.sql.SQLException 读取结果集失败时抛出
   */
  private ProjectResponse mapProject(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new ProjectResponse(
      rs.getString("id"),
      rs.getString("title"),
      rs.getString("type"),
      rs.getString("ratio"),
      rs.getString("render_mode"),
      rs.getString("template_id"),
      rs.getString("content_json"),
      rs.getString("cover_url"),
      rs.getString("status"),
      rs.getString("created_at"),
      rs.getString("updated_at"),
      pages(rs.getString("id"))
    );
  }

  /**
   * 查询作品已渲染页面。
   *
   * @param projectId 作品 ID
   * @return 按 page_index 升序排列的页面列表
   */
  private List<CardPageResponse> pages(String projectId) {
    return jdbc.query("""
      select id, page_index, role, content_json, image_url, created_at
      from card_page
      where project_id = ?
      order by page_index asc
      """, (rs, rowNum) -> new CardPageResponse(
      rs.getString("id"),
      rs.getInt("page_index"),
      rs.getString("role"),
      rs.getString("content_json"),
      rs.getString("image_url"),
      rs.getString("created_at")
    ), projectId);
  }

}

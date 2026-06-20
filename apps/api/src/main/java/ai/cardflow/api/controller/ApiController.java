package ai.cardflow.api.controller;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.model.ApiModels.CreateProjectRequest;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.GenerateContentResponse;
import ai.cardflow.api.model.ApiModels.MeResponse;
import ai.cardflow.api.model.ApiModels.ProjectResponse;
import ai.cardflow.api.model.ApiModels.RenderProjectResponse;
import ai.cardflow.api.model.ApiModels.TaskResponse;
import ai.cardflow.api.model.ApiModels.TemplateResponse;
import ai.cardflow.api.model.ApiModels.UpdateProjectContentRequest;
import ai.cardflow.api.model.ApiModels.UsageRecordResponse;
import ai.cardflow.api.model.ApiModels.UsageSummaryResponse;
import ai.cardflow.api.service.GenerationService;
import ai.cardflow.api.service.ProjectService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CardFlow AI MVP 的 HTTP API 入口。
 *
 * <p>控制器只做请求编排和轻量查询；内容生成、作品管理和渲染逻辑下沉到 service。</p>
 */
@RestController
@RequestMapping("/api")
public class ApiController {
  private static final Logger log = LoggerFactory.getLogger(ApiController.class);

  private final JdbcTemplate jdbc;
  private final AppProperties properties;
  private final GenerationService generationService;
  private final ProjectService projectService;

  /**
   * 组装控制器依赖。
   *
   * @param jdbc SQLite 访问入口
   * @param properties 应用配置
   * @param generationService 内容生成服务
   * @param projectService 作品管理服务
   */
  public ApiController(
    JdbcTemplate jdbc,
    AppProperties properties,
    GenerationService generationService,
    ProjectService projectService
  ) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.generationService = generationService;
    this.projectService = projectService;
  }

  /**
   * 健康检查接口。
   *
   * @return 固定 ok，表示 Spring Boot 应用已响应
   */
  @GetMapping("/health")
  public String health() {
    return "ok";
  }

  /**
   * 获取 MVP 阶段的默认本地用户。
   *
   * @return 当前默认用户资料和每日额度
   */
  @GetMapping("/me")
  public MeResponse me() {
    return jdbc.queryForObject(
      "select id, nickname, plan, daily_quota from user where id = ?",
      (rs, rowNum) -> new MeResponse(
        rs.getString("id"),
        rs.getString("nickname"),
        rs.getString("plan"),
        rs.getInt("daily_quota")
      ),
      properties.app().defaultUserId()
    );
  }

  /**
   * 获取所有启用状态的系统模板。
   *
   * @return 模板列表，按种子数据创建顺序返回
   */
  @GetMapping("/templates")
  public List<TemplateResponse> templates() {
    return jdbc.query("""
      select id, name, description, style_key, supported_types, supported_ratios
      from template
      where status = 'active'
      order by created_at asc
      """, (rs, rowNum) -> new TemplateResponse(
      rs.getString("id"),
      rs.getString("name"),
      rs.getString("description"),
      rs.getString("style_key"),
      rs.getString("supported_types"),
      rs.getString("supported_ratios")
    ));
  }

  /**
   * 获取单个模板详情。
   *
   * @param id 模板 ID
   * @return 模板详情
   */
  @GetMapping("/templates/{id}")
  public TemplateResponse template(@PathVariable String id) {
    return jdbc.queryForObject("""
      select id, name, description, style_key, supported_types, supported_ratios
      from template
      where id = ?
      """, (rs, rowNum) -> new TemplateResponse(
      rs.getString("id"),
      rs.getString("name"),
      rs.getString("description"),
      rs.getString("style_key"),
      rs.getString("supported_types"),
      rs.getString("supported_ratios")
    ), id);
  }

  /**
   * 生成结构化卡片内容。
   *
   * @param request 主题或长文生成参数
   * @return 任务 ID 和结构化 JSON
   */
  @PostMapping("/generations/content")
  public GenerateContentResponse generateContent(@Valid @RequestBody GenerateContentRequest request) {
    String topic = request.topicInput() == null ? "-" : request.topicInput().title();
    log.info("POST /api/generations/content mode={} outputFormat={} topic={}", request.generationMode(), request.outputFormat(), topic);
    return generationService.generate(request);
  }

  /**
   * 创建一个作品草稿。
   *
   * @param request 作品基础信息和内容 JSON
   * @return 创建后的作品详情
   */
  @PostMapping("/projects")
  public ProjectResponse createProject(@Valid @RequestBody CreateProjectRequest request) {
    return projectService.create(request);
  }

  /**
   * 获取当前默认用户的作品列表。
   *
   * @return 作品列表，最近创建的排在前面
   */
  @GetMapping("/projects")
  public List<ProjectResponse> projects() {
    return projectService.list();
  }

  /**
   * 获取作品详情。
   *
   * @param id 作品 ID
   * @return 作品详情及已渲染页面
   */
  @GetMapping("/projects/{id}")
  public ProjectResponse project(@PathVariable String id) {
    return projectService.get(id);
  }

  /**
   * 保存前端编辑后的结构化 JSON，并把作品状态重置为草稿。
   *
   * @param id 作品 ID
   * @param request 新的内容 JSON
   * @return 更新后的作品详情
   */
  @PutMapping("/projects/{id}/content")
  public ProjectResponse updateProjectContent(
    @PathVariable String id,
    @Valid @RequestBody UpdateProjectContentRequest request
  ) {
    return projectService.updateContent(id, request);
  }

  /**
   * 删除作品及其页面和关联任务记录。
   *
   * @param id 作品 ID
   */
  @DeleteMapping("/projects/{id}")
  public void deleteProject(@PathVariable String id) {
    projectService.delete(id);
  }

  /**
   * 渲染作品为 PNG 图片。
   *
   * @param id 作品 ID
   * @return 渲染任务 ID 和输出图片 URL
   */
  @PostMapping("/projects/{id}/render")
  public RenderProjectResponse renderProject(@PathVariable String id) {
    return projectService.render(id);
  }

  /**
   * 查询生成或渲染任务状态。
   *
   * @param id 任务 ID
   * @return 任务状态和失败原因
   */
  @GetMapping("/tasks/{id}")
  public TaskResponse task(@PathVariable String id) {
    return jdbc.queryForObject("""
      select id, status, error_message
      from generate_task
      where id = ? and user_id = ?
      """, (rs, rowNum) -> new TaskResponse(
      rs.getString("id"),
      rs.getString("status"),
      rs.getString("error_message")
    ), id, properties.app().defaultUserId());
  }

  /**
   * 获取今日额度使用情况。
   *
   * @return 每日额度、今日已用和剩余次数
   */
  @GetMapping("/usage/summary")
  public UsageSummaryResponse usageSummary() {
    int quota = me().dailyQuota();
    // 使用 UTC 日期前缀匹配 ISO 时间字符串，避免引入额外日期列。
    String todayPrefix = LocalDate.now(ZoneOffset.UTC).toString();
    Integer used = jdbc.queryForObject("""
      select coalesce(sum(amount), 0) from usage_record
      where user_id = ? and created_at like ?
      """, Integer.class, properties.app().defaultUserId(), todayPrefix + "%");
    int usedValue = used == null ? 0 : used;
    return new UsageSummaryResponse(quota, usedValue, Math.max(0, quota - usedValue));
  }

  /**
   * 获取最近用量记录。
   *
   * @return 最近 100 条用量记录
   */
  @GetMapping("/usage/records")
  public List<UsageRecordResponse> usageRecords() {
    return jdbc.query("""
      select id, task_id, usage_type, amount, model_name, created_at
      from usage_record
      where user_id = ?
      order by created_at desc
      limit 100
      """, (rs, rowNum) -> new UsageRecordResponse(
      rs.getString("id"),
      rs.getString("task_id"),
      rs.getString("usage_type"),
      rs.getInt("amount"),
      rs.getString("model_name"),
      rs.getString("created_at")
    ), properties.app().defaultUserId());
  }
}

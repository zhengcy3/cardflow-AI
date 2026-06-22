package ai.cardflow.api.database;

import ai.cardflow.api.config.AppProperties;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite 启动初始化器。
 *
 * <p>应用启动时创建 MVP 所需表结构，并写入默认用户和系统模板种子数据。</p>
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final AppProperties properties;

  /**
   * 注入数据库初始化依赖。
   *
   * @param jdbc SQLite 访问入口
   * @param properties 应用配置
   */
  public DatabaseInitializer(JdbcTemplate jdbc, AppProperties properties) {
    this.jdbc = jdbc;
    this.properties = properties;
  }

  /**
   * Spring Boot 启动完成后执行数据库初始化。
   *
   * @param args 应用启动参数
   */
  @Override
  public void run(ApplicationArguments args) {
    createTables();
    seedDefaultUser();
    seedTemplates();
  }

  /**
   * 创建 MVP 所需的所有 SQLite 表。
   *
   * <p>使用 create table if not exists 保证本地重复启动不会破坏已有数据。</p>
   */
  private void createTables() {
    jdbc.execute("""
      create table if not exists user (
        id text primary key,
        email text,
        nickname text not null,
        avatar_url text,
        plan text not null,
        daily_quota integer not null,
        created_at text not null,
        updated_at text not null
      )
      """);

    // 模板只保存元信息；具体 HTML 渲染逻辑当前由 HtmlTemplateRenderer 统一实现。
    jdbc.execute("""
      create table if not exists template (
        id text primary key,
        name text not null,
        description text not null,
        style_key text not null,
        cover_url text,
        supported_types text not null,
        supported_ratios text not null,
        field_limits_json text not null,
        status text not null,
        created_at text not null
      )
      """);

    // card_project 保存用户可编辑的完整 content_json，是重新渲染的事实来源。
    jdbc.execute("""
      create table if not exists card_project (
        id text primary key,
        user_id text not null,
        title text not null,
        type text not null,
        ratio text not null,
        render_mode text not null,
        template_id text not null,
        content_json text not null,
        cover_url text,
        status text not null,
        created_at text not null,
        updated_at text not null
      )
      """);

    // card_page 保存每次渲染后的页面级图片结果，支持轮播作品多页输出。
    jdbc.execute("""
      create table if not exists card_page (
        id text primary key,
        project_id text not null,
        page_index integer not null,
        role text not null,
        content_json text not null,
        image_url text,
        created_at text not null
      )
      """);

    // generate_task 统一记录内容生成和图片渲染任务，便于前端查询状态。
    jdbc.execute("""
      create table if not exists generate_task (
        id text primary key,
        user_id text not null,
        project_id text,
        task_type text not null,
        input_text text not null,
        input_params_json text not null,
        status text not null,
        error_message text,
        started_at text,
        finished_at text,
        created_at text not null
      )
      """);

    // usage_record.amount 记录单次生成实际打到 DeepSeek 的 API 轮次（含 tool calling）。
    jdbc.execute("""
      create table if not exists usage_record (
        id text primary key,
        user_id text not null,
        task_id text,
        usage_type text not null,
        amount integer not null,
        model_name text not null,
        created_at text not null
      )
      """);
  }

  /**
   * 写入 MVP 默认本地用户。
   */
  private void seedDefaultUser() {
    String now = Instant.now().toString();
    jdbc.update("""
      insert or ignore into user (id, email, nickname, avatar_url, plan, daily_quota, created_at, updated_at)
      values (?, ?, ?, ?, ?, ?, ?, ?)
      """,
      properties.app().defaultUserId(),
      "local@cardflow.ai",
      "本地用户",
      null,
      "free",
      10,
      now,
      now
    );
  }

  /**
   * 写入内置模板种子数据。
   */
  private void seedTemplates() {
    String now = Instant.now().toString();
    seedTemplate("xiaohongshu-highlight", "小红书高亮", "强标题 / 色块高亮", "xiaohongshu_highlight", now);
    seedTemplate("minimal-apple", "极简苹果风", "大留白 / 高级感", "minimal_apple", now);
    seedTemplate("business-briefing", "商业简报", "数据化 / 理性", "business_briefing", now);
    seedTemplate("editorial-ink", "电子杂志墨水", "Monocle 风 / 低饱和", "editorial_ink", now);
    seedTemplate("swiss-grid", "瑞士网格", "网格 / 单一 accent", "swiss_grid", now);
    seedTemplate("hand-sketch", "手绘笔记", "马克笔 / 便签感", "hand_sketch", now);
    seedTemplate("flat-infographic", "扁平信息图", "粉彩 / 几何图标", "flat_infographic", now);
    seedTemplate("warm-documentary", "人文纪实", "自然光 / 真实场景", "warm_documentary", now);
    seedTemplate("bold-cover", "高冲击封面", "高对比 / 电影感", "bold_cover", now);
  }

  /**
   * 写入单个模板种子。
   *
   * @param id 模板 ID
   * @param name 模板名称
   * @param description 模板描述
   * @param styleKey 风格标识
   * @param now 创建时间
   */
  private void seedTemplate(String id, String name, String description, String styleKey, String now) {
    jdbc.update("""
      insert or ignore into template
      (id, name, description, style_key, cover_url, supported_types, supported_ratios, field_limits_json, status, created_at)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """,
      id,
      name,
      description,
      styleKey,
      null,
      // 使用逗号分隔字符串保存 MVP 支持范围，后续模板中心完善时可替换为规范化表。
      "single_card,carousel_card",
      "xhs_3_4,youtube_16_9,bilibili_16_9,douyin_9_16",
      "{\"title\":24,\"subtitle\":24,\"point\":22,\"body\":80}",
      "active",
      now
    );
  }
}

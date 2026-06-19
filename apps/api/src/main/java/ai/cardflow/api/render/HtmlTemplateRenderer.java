package ai.cardflow.api.render;

import ai.cardflow.api.model.ApiModels.ProjectResponse;
import ai.cardflow.api.model.RenderModels.Canvas;
import ai.cardflow.api.model.RenderModels.RenderCard;
import ai.cardflow.api.model.RenderModels.RenderPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * HTML 模板渲染器。
 *
 * <p>负责把作品内容 JSON 归一成渲染模型，并输出可被 Playwright 截图的完整 HTML。</p>
 */
@Service
public class HtmlTemplateRenderer {
  private final ObjectMapper objectMapper;

  /**
   * 注入 JSON 解析工具。
   *
   * @param objectMapper Jackson 对象映射器
   */
  public HtmlTemplateRenderer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * 根据输出比例计算截图画布尺寸。
   *
   * @param ratio 输出比例标识
   * @return 像素级画布尺寸
   */
  public Canvas canvas(String ratio) {
    return switch (ratio) {
      case "youtube_16_9", "bilibili_16_9" -> new Canvas(1280, 720, 2);
      case "douyin_9_16" -> new Canvas(900, 1600, 2);
      default -> new Canvas(900, 1200, 2);
    };
  }

  /**
   * 渲染作品第一页 HTML。
   *
   * @param project 作品详情
   * @return 完整 HTML 字符串
   */
  public String render(ProjectResponse project) {
    return render(project, pages(project).get(0));
  }

  /**
   * 渲染指定页面 HTML。
   *
   * @param project 作品详情
   * @param page 已归一化的页面数据
   * @return 完整 HTML 字符串
   */
  public String render(ProjectResponse project, RenderPage page) {
    String dynamicHtml = dynamicHtml(page);
    if (dynamicHtml != null) {
      return dynamicHtml;
    }

    Canvas canvas = canvas(project.ratio());
    RenderCard card = page.card();
    String platform = platformLabel(project.ratio());
    String points = pointItems(card.points());

    return """
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <style>
            * { box-sizing: border-box; }
            body {
              margin: 0;
              width: %dpx;
              height: %dpx;
              overflow: hidden;
              font-family: Inter, -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
              background: #111;
            }
            .card {
              position: relative;
              width: %dpx;
              height: %dpx;
              padding: %dpx;
              overflow: hidden;
              background:
                radial-gradient(circle at 82%% 14%%, rgba(255, 200, 87, 0.45), transparent 22%%),
                linear-gradient(145deg, #fff8eb 0%%, #f4eadc 58%%, #e8dac8 100%%);
              color: #181411;
            }
            .frame {
              position: absolute;
              inset: %dpx;
              border: 2px solid rgba(24, 20, 17, 0.1);
              border-radius: %dpx;
            }
            .meta {
              display: flex;
              justify-content: space-between;
              align-items: center;
              color: #c83232;
              font-size: %dpx;
              font-weight: 800;
              letter-spacing: 0;
            }
            h1 {
              max-width: %dpx;
              margin: %dpx 0 %dpx;
              font-size: %dpx;
              line-height: 1.05;
              letter-spacing: 0;
            }
            .summary {
              max-width: %dpx;
              margin: 0 0 %dpx;
              color: #5f574e;
              font-size: %dpx;
              line-height: 1.62;
              font-weight: 550;
            }
            .divider {
              width: 100%%;
              height: 1px;
              margin: %dpx 0;
              background: rgba(24, 20, 17, 0.12);
            }
            .points {
              display: grid;
              gap: %dpx;
              margin: 0;
              padding: 0;
              list-style: none;
              color: #2c2620;
              font-size: %dpx;
              font-weight: 760;
            }
            .points li {
              display: flex;
              align-items: center;
              gap: %dpx;
            }
            .points i {
              width: %dpx;
              height: %dpx;
              flex: 0 0 auto;
              border-radius: 50%%;
              background: #d44747;
              box-shadow: 0 0 0 %dpx rgba(212, 71, 71, 0.12);
            }
            .footer {
              position: absolute;
              left: %dpx;
              right: %dpx;
              bottom: %dpx;
              display: flex;
              justify-content: space-between;
              color: #8b8176;
              font-size: %dpx;
              font-weight: 800;
              text-transform: uppercase;
            }
          </style>
        </head>
        <body>
          <article class="card">
            <div class="frame"></div>
            <div class="meta"><span>知识卡片 %02d</span><span>%s</span></div>
            <h1>%s</h1>
            <p class="summary">%s</p>
            <div class="divider"></div>
            <ul class="points">%s</ul>
            <div class="footer"><span>CardFlow AI</span><span>%s</span></div>
          </article>
        </body>
      </html>
      """.formatted(
        canvas.width(), canvas.height(),
        canvas.width(), canvas.height(),
        padding(canvas),
        Math.max(28, canvas.width() / 30),
        Math.max(28, canvas.width() / 28),
        Math.max(18, canvas.width() / 46),
        Math.max(420, canvas.width() - padding(canvas) * 2),
        Math.max(70, canvas.height() / 10),
        Math.max(28, canvas.height() / 40),
        titleSize(canvas),
        Math.max(360, canvas.width() - padding(canvas) * 2),
        Math.max(34, canvas.height() / 24),
        Math.max(22, canvas.width() / 42),
        Math.max(24, canvas.height() / 36),
        Math.max(14, canvas.height() / 80),
        Math.max(24, canvas.width() / 38),
        Math.max(10, canvas.width() / 92),
        Math.max(7, canvas.width() / 150),
        Math.max(7, canvas.width() / 150),
        Math.max(4, canvas.width() / 240),
        padding(canvas),
        padding(canvas),
        padding(canvas),
        Math.max(16, canvas.width() / 54),
        page.pageIndex() + 1,
        escape(platform),
        escape(card.title()),
        escape(card.summary()),
        points,
        escape(card.pageLabel())
      );
  }

  /**
   * 将作品 contentJson 转换为页面列表。
   *
   * <p>优先识别轮播结构 pages；没有 pages 时降级为单卡结构；JSON 无法解析时生成 fallback 页面。</p>
   *
   * @param project 作品详情
   * @return 可渲染页面列表
   */
  public List<RenderPage> pages(ProjectResponse project) {
    try {
      JsonNode root = objectMapper.readTree(project.contentJson());
      if ("html_card".equals(root.path("kind").asText()) && !root.path("html").asText("").isBlank()) {
        RenderCard card = new RenderCard(
          root.path("title").asText(project.title()),
          root.path("designNotes").asText("AI 动态生成 HTML 卡片"),
          List.of("动态 HTML", "实时版式", "Playwright 截图"),
          "01 / 01"
        );
        return List.of(new RenderPage(0, "dynamic_html", root.toString(), card));
      }

      if (root.has("pages") && root.get("pages").isArray() && root.get("pages").size() > 0) {
        List<RenderPage> pages = new ArrayList<>();
        int pageCount = root.get("pages").size();
        for (int i = 0; i < pageCount; i++) {
          JsonNode page = root.get("pages").get(i);
          // 缺省角色按位置推断，第一页默认封面，后续默认观点页。
          String role = page.path("role").asText(i == 0 ? "cover" : "point");
          RenderCard card = new RenderCard(
            page.path("title").asText(project.title()),
            page.path("body").asText(page.path("subtitle").asText(root.path("title").asText("AI 已提炼核心表达"))),
            pagePoints(root, page, role),
            "%02d / %02d".formatted(i + 1, pageCount)
          );
          pages.add(new RenderPage(i, role, page.toString(), card));
        }
        return pages;
      }

      List<String> points = new ArrayList<>();
      if (root.has("points") && root.get("points").isArray()) {
        root.get("points").forEach(point -> points.add(point.asText()));
      }
      RenderCard card = new RenderCard(
        root.path("title").asText(project.title()),
        root.path("summary").asText(root.path("subtitle").asText("消费知识不等于成长")),
        points.isEmpty() ? List.of("收藏不等于行动", "知道不等于做到", "输入不等于输出") : points,
        "01 / 01"
      );
      return List.of(new RenderPage(0, "single", root.toString(), card));
    } catch (IOException e) {
      // 内容 JSON 可被用户直接编辑，解析失败时仍给前端一个可预览的兜底页。
      RenderCard fallback = new RenderCard(
        project.title(),
        "内容解析失败，使用项目标题生成预览。",
        List.of("检查内容结构", "重新生成内容", "再次渲染图片"),
        "01 / 01"
      );
      return List.of(new RenderPage(0, "fallback", project.contentJson(), fallback));
    }
  }

  /**
   * 从动态 HTML 页面中提取完整 HTML。
   *
   * @param page 渲染页面
   * @return 安全 HTML，非动态页面返回 null
   */
  private String dynamicHtml(RenderPage page) {
    try {
      JsonNode root = objectMapper.readTree(page.contentJson());
      if (!"html_card".equals(root.path("kind").asText())) {
        return null;
      }
      String html = root.path("html").asText("");
      if (html.isBlank()) {
        return null;
      }
      assertSafeDynamicHtml(html);
      return html;
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * 动态 HTML 渲染前的基础安全校验。
   *
   * @param html AI 返回 HTML
   */
  private void assertSafeDynamicHtml(String html) {
    String lower = html.toLowerCase();
    if (lower.contains("<script") || lower.contains("</script")) {
      throw new IllegalStateException("Dynamic HTML cannot contain script.");
    }
    if (lower.contains("http://") || lower.contains("https://") || lower.contains("src=") || lower.contains("href=")) {
      throw new IllegalStateException("Dynamic HTML cannot contain external resources.");
    }
    if (lower.contains("@import") || lower.contains("url(")) {
      throw new IllegalStateException("Dynamic HTML cannot contain external style resources.");
    }
  }

  /**
   * 为不同页面角色提取重点列表。
   *
   * @param root 完整内容 JSON
   * @param page 当前页面 JSON
   * @param role 当前页面角色
   * @return 页面重点列表
   */
  private List<String> pagePoints(JsonNode root, JsonNode page, String role) {
    if ("cover".equals(role) && root.has("pages")) {
      List<String> points = new ArrayList<>();
      for (JsonNode item : root.get("pages")) {
        if ("point".equals(item.path("role").asText())) {
          points.add(item.path("title").asText());
        }
      }
      return points.isEmpty() ? List.of("先提炼主张", "再拆成结构", "最后生成卡片") : points;
    }
    if ("summary".equals(role)) {
      return List.of("保留核心结论", "适合收藏转发", "形成内容闭环");
    }
    return List.of(page.path("body").asText("一个清晰观点可以独立成页。"));
  }

  /**
   * 将重点列表渲染为 HTML li 片段。
   *
   * @param points 重点文案列表
   * @return 已转义的 HTML 片段
   */
  private String pointItems(List<String> points) {
    StringBuilder builder = new StringBuilder();
    // 当前模板最多展示 4 条，避免长列表挤压卡片主标题和摘要。
    points.stream().limit(4).forEach(point ->
      builder.append("<li><i></i><span>").append(escape(point)).append("</span></li>")
    );
    return builder.toString();
  }

  /**
   * 根据画布宽度计算统一内边距。
   *
   * @param canvas 当前画布
   * @return 内边距像素值
   */
  private int padding(Canvas canvas) {
    return Math.max(48, canvas.width() / 14);
  }

  /**
   * 根据横版或竖版画布计算标题字号。
   *
   * @param canvas 当前画布
   * @return 标题字号
   */
  private int titleSize(Canvas canvas) {
    if (canvas.width() > canvas.height()) {
      return 64;
    }
    return Math.max(46, canvas.width() / 12);
  }

  /**
   * 将输出比例标识转换为卡片上的平台标签。
   *
   * @param ratio 输出比例标识
   * @return 平台展示文案
   */
  private String platformLabel(String ratio) {
    return switch (ratio) {
      case "youtube_16_9" -> "YouTube 16:9";
      case "bilibili_16_9" -> "B站 16:9";
      case "douyin_9_16" -> "抖音 9:16";
      default -> "小红书 3:4";
    };
  }

  /**
   * 转义用户可编辑内容，防止内容 JSON 中的特殊字符破坏 HTML 结构。
   *
   * @param value 原始文本
   * @return HTML 安全文本
   */
  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }
}

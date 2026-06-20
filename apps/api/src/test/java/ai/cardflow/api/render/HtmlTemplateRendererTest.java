package ai.cardflow.api.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.cardflow.api.model.ApiModels.ProjectResponse;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HtmlTemplateRendererTest {
  private final HtmlTemplateRenderer renderer = new HtmlTemplateRenderer(new ObjectMapper());

  @Test
  void keepsXiaohongshuLayoutSizeButExportsAtHighPixelRatio() {
    var canvas = renderer.canvas("xhs_3_4");

    assertThat(canvas.width()).isEqualTo(900);
    assertThat(canvas.height()).isEqualTo(1200);
    assertThat(canvas.pixelRatio()).isEqualTo(2);
  }

  @Test
  void keepsWideLayoutSizeButExportsAtHighPixelRatio() {
    var canvas = renderer.canvas("youtube_16_9");

    assertThat(canvas.width()).isEqualTo(1280);
    assertThat(canvas.height()).isEqualTo(720);
    assertThat(canvas.pixelRatio()).isEqualTo(2);
  }

  @Test
  void rendersDynamicHtmlCardFromModelOutput() {
    String html = "<!doctype html><html><head><style>body{margin:0}.card{width:900px;height:1200px}</style></head><body><main class='card'>动态卡片</main></body></html>";
    ProjectResponse project = project("""
      {"kind":"html_card","title":"动态卡片","html":"%s","designNotes":"dynamic","warnings":[]}
      """.formatted(escapeJson(html)));

    var pages = renderer.pages(project);
    String rendered = renderer.render(project, pages.get(0));

    assertThat(pages).hasSize(1);
    assertThat(pages.get(0).role()).isEqualTo("dynamic_html");
    assertThat(rendered).isEqualTo(html);
  }

  private ProjectResponse project(String contentJson) {
    return new ProjectResponse(
      "project-1",
      "动态卡片",
      "single_card",
      "xhs_3_4",
      "precise_card",
      "xiaohongshu-highlight",
      contentJson,
      null,
      "draft",
      "2026-06-05T00:00:00Z",
      "2026-06-05T00:00:00Z",
      List.of()
    );
  }

  private String escapeJson(String value) {
    return value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"");
  }
}

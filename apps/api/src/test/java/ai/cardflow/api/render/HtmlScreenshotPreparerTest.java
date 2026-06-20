package ai.cardflow.api.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlScreenshotPreparerTest {
  private final HtmlScreenshotPreparer preparer = new HtmlScreenshotPreparer();

  @Test
  void injectsNormalizeStylesBeforeHeadClose() {
    String html = "<!doctype html><html><head><title>t</title></head><body><main>卡片</main></body></html>";
    String prepared = preparer.prepare(html, 900, 1200);

    assertThat(prepared).contains("data-cardflow=\"screenshot-normalize\"");
    assertThat(prepared).contains("width: 900px !important");
    assertThat(prepared).contains("height: 1200px !important");
    assertThat(prepared).contains("overflow: hidden !important");
    assertThat(prepared.indexOf("screenshot-normalize")).isLessThan(prepared.indexOf("</head>"));
  }
}

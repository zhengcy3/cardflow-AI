package ai.cardflow.api.render;

import org.springframework.stereotype.Component;

/**
 * 截图前规范化 AI 动态 HTML，避免视口边缘裁切。
 */
@Component
public class HtmlScreenshotPreparer {

  /**
   * 向 HTML 注入截图规范化样式。
   *
   * @param html 原始 HTML 文档
   * @param width 目标画布宽
   * @param height 目标画布高
   * @return 注入样式后的 HTML
   */
  public String prepare(String html, int width, int height) {
    if (html == null || html.isBlank()) {
      return html;
    }

    String css = buildNormalizeCss(width, height);
    String lower = html.toLowerCase();

    if (lower.contains("</head>")) {
      return html.replaceFirst("(?i)</head>", css + "</head>");
    }
    if (lower.contains("<head>")) {
      return html.replaceFirst("(?i)<head>", "<head>" + css);
    }
    if (lower.contains("<html")) {
      return html.replaceFirst("(?i)<html[^>]*>", "$0<head>" + css + "</head>");
    }
    return css + html;
  }

  private String buildNormalizeCss(int width, int height) {
    return """
        <style data-cardflow="screenshot-normalize">
          html, body {
            margin: 0 !important;
            padding: 0 !important;
            width: %dpx !important;
            height: %dpx !important;
            min-width: %dpx !important;
            min-height: %dpx !important;
            max-width: %dpx !important;
            max-height: %dpx !important;
            overflow: hidden !important;
            box-sizing: border-box !important;
            -webkit-print-color-adjust: exact !important;
            print-color-adjust: exact !important;
          }
          html {
            overflow: hidden !important;
          }
          body * {
            box-sizing: border-box;
          }
        </style>
        """.formatted(width, height, width, height, width, height, width, height);
  }
}

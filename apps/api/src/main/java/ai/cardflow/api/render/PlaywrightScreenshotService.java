package ai.cardflow.api.render;

import ai.cardflow.api.model.RenderModels.Canvas;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/**
 * Playwright 截图服务。
 *
 * <p>负责启动无头 Chromium，把 HTML 内容按指定画布尺寸截图为 PNG。</p>
 */
@Service
public class PlaywrightScreenshotService {
  /**
   * 对 HTML 字符串进行截图。
   *
   * @param html 完整 HTML 内容
   * @param canvas 截图画布尺寸
   * @param outputFile PNG 输出文件路径
   */
  public void screenshot(String html, Canvas canvas, Path outputFile) {
    try (Playwright playwright = Playwright.create();
         Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
      Page page = browser.newPage();
      page.setViewportSize(canvas.width(), canvas.height());
      // setContent 直接加载内联 HTML，避免额外写临时 HTML 文件。
      page.setContent(html);
      page.screenshot(new Page.ScreenshotOptions()
        .setPath(outputFile)
        .setFullPage(false)
      );
    }
  }
}

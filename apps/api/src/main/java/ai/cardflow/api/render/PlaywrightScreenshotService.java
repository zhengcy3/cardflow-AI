package ai.cardflow.api.render;

import ai.cardflow.api.model.RenderModels.Canvas;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ScreenshotScale;
import com.microsoft.playwright.options.WaitUntilState;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/**
 * Playwright 截图服务。
 *
 * <p>负责启动无头 Chromium，把 HTML 内容按指定画布尺寸截图为 PNG。</p>
 */
@Service
public class PlaywrightScreenshotService {
  private final HtmlScreenshotPreparer screenshotPreparer;

  public PlaywrightScreenshotService(HtmlScreenshotPreparer screenshotPreparer) {
    this.screenshotPreparer = screenshotPreparer;
  }

  /**
   * 对 HTML 字符串进行截图。
   *
   * @param html 完整 HTML 内容
   * @param canvas 截图画布尺寸
   * @param outputFile PNG 输出文件路径
   */
  public void screenshot(String html, Canvas canvas, Path outputFile) {
    String preparedHtml = screenshotPreparer.prepare(html, canvas.width(), canvas.height());

    try (Playwright playwright = Playwright.create();
         Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
      Page page = browser.newPage(new Browser.NewPageOptions()
        .setViewportSize(canvas.width(), canvas.height())
        .setDeviceScaleFactor(canvas.pixelRatio())
      );
      page.setContent(
        preparedHtml,
        new Page.SetContentOptions().setWaitUntil(WaitUntilState.NETWORKIDLE)
      );
      fitContentToViewport(page, canvas.width(), canvas.height());
      page.screenshot(new Page.ScreenshotOptions()
        .setPath(outputFile)
        .setFullPage(false)
        .setScale(ScreenshotScale.DEVICE)
      );
    }
  }

  /**
   * 内容超出画布时整体等比缩小，避免底部或边缘被硬裁切。
   */
  private void fitContentToViewport(Page page, int width, int height) {
    page.evaluate("""
      ([viewportWidth, viewportHeight]) => {
        const root = document.documentElement;
        const body = document.body;
        root.style.overflow = 'hidden';
        body.style.overflow = 'hidden';
        body.style.margin = '0';
        body.style.transformOrigin = 'top left';

        const scrollWidth = Math.max(root.scrollWidth, body.scrollWidth, root.clientWidth);
        const scrollHeight = Math.max(root.scrollHeight, body.scrollHeight, root.clientHeight);
        const fitScale = Math.min(
          viewportWidth / scrollWidth,
          viewportHeight / scrollHeight,
          1
        );

        if (fitScale < 1) {
          body.style.transform = `scale(${fitScale})`;
          body.style.width = `${viewportWidth / fitScale}px`;
          body.style.height = `${viewportHeight / fitScale}px`;
        }
      }
      """, new Object[] { width, height });
  }
}

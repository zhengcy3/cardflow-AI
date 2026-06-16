package ai.cardflow.api.model;

import java.util.List;

/**
 * 渲染层内部模型。
 *
 * <p>这些对象不直接暴露给前端，主要在 HTML 模板渲染和 Playwright 截图之间传递。</p>
 */
public final class RenderModels {
  /** 阻止工具类被实例化。 */
  private RenderModels() {}

  /**
   * 截图画布尺寸。
   *
   * @param width 画布宽度，单位 px
   * @param height 画布高度，单位 CSS px
   * @param pixelRatio PNG 输出倍率
   */
  public record Canvas(int width, int height, int pixelRatio) {}

  /**
   * 单张卡片渲染内容。
   *
   * @param title 卡片主标题
   * @param summary 卡片摘要或正文
   * @param points 重点列表
   * @param pageLabel 页码展示文案
   */
  public record RenderCard(String title, String summary, List<String> points, String pageLabel) {}

  /**
   * 作品中的一个渲染页面。
   *
   * @param pageIndex 页面序号，从 0 开始
   * @param role 页面角色，例如 cover、point、summary 或 fallback
   * @param contentJson 该页原始内容 JSON
   * @param card 已归一化的卡片渲染数据
   */
  public record RenderPage(int pageIndex, String role, String contentJson, RenderCard card) {}
}

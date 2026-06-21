package ai.cardflow.api.image;

/**
 * 三方 AI 生图供应商抽象。
 *
 * <p>业务层通过此接口调用文生图能力，便于后续切换或增加其他 Provider。</p>
 */
public interface ImageProvider {

  /**
   * 根据提示词生成一张图片。
   *
   * @param request 生图参数
   * @return PNG/JPEG 二进制内容
   */
  byte[] generate(ImageGenerationRequest request);
}

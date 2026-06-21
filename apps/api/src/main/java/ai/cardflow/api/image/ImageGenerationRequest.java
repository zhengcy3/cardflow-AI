package ai.cardflow.api.image;

/**
 * 文生图请求参数。
 *
 * @param prompt 图片描述提示词
 * @param aspectRatio MiniMax 支持的宽高比，例如 3:4、16:9
 * @param promptOptimizer 覆盖配置中的 prompt_optimizer；null 表示使用默认值
 * @param model 覆盖配置中的 model；null 或空表示使用默认值
 */
public record ImageGenerationRequest(
  String prompt,
  String aspectRatio,
  Boolean promptOptimizer,
  String model
) {
  public ImageGenerationRequest(String prompt, String aspectRatio) {
    this(prompt, aspectRatio, null, null);
  }
}

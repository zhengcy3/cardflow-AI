package ai.cardflow.api.image;

/**
 * 文生图请求参数。
 *
 * @param prompt 图片描述提示词
 * @param aspectRatio MiniMax 支持的宽高比，例如 3:4、16:9
 */
public record ImageGenerationRequest(String prompt, String aspectRatio) {}

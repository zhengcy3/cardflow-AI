package ai.cardflow.api.image;

/**
 * 将 CardFlow 输出比例映射为 MiniMax aspect_ratio。
 */
public final class AspectRatioMapper {
  private AspectRatioMapper() {}

  /**
   * 根据作品输出比例标识返回 MiniMax aspect_ratio。
   *
   * @param outputFormat CardFlow 比例标识
   * @return MiniMax aspect_ratio 字符串
   */
  public static String fromOutputFormat(String outputFormat) {
    if (outputFormat == null || outputFormat.isBlank()) {
      return "3:4";
    }
    return switch (outputFormat) {
      case "youtube_16_9", "bilibili_16_9" -> "16:9";
      case "douyin_9_16" -> "9:16";
      default -> "3:4";
    };
  }
}

package ai.cardflow.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cardflow.* 配置映射。
 *
 * @param storage 本地文件存储配置
 * @param app 应用运行配置
 */
@ConfigurationProperties(prefix = "cardflow")
public record AppProperties(Storage storage, App app, Image image) {
  /**
   * 本地输出目录配置。
   *
   * @param outputDir PNG 渲染结果的根目录
   */
  public record Storage(String outputDir) {}

  /**
   * MVP 阶段的应用级默认值。
   *
   * @param defaultUserId 默认本地用户 ID,替代完整登录体系
   */
  public record App(String defaultUserId) {}

  /**
   * AI 生图配置。
   *
   * @param provider 当前启用的生图 Provider 标识
   * @param minimax MiniMax 文生图配置
   */
  public record Image(String provider, MiniMaxImage minimax) {}

  /**
   * MiniMax image-01 接入参数。
   *
   * @param baseUrl API 根地址
   * @param apiKey Bearer Token
   * @param model 模型名称，默认 image-01
   * @param promptOptimizer 是否启用提示词自动优化
   */
  public record MiniMaxImage(String baseUrl, String apiKey, String model, boolean promptOptimizer) {}
}
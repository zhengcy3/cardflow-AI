package ai.cardflow.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cardflow.* 配置映射。
 *
 * @param storage 本地文件存储配置
 * @param app 应用运行配置
 */
@ConfigurationProperties(prefix = "cardflow")
public record AppProperties(Storage storage, App app) {
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
}
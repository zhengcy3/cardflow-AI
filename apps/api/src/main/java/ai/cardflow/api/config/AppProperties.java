package ai.cardflow.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cardflow.* 配置映射。
 *
 * @param storage 本地文件存储配置
 * @param app 应用运行配置
 * @param llm 大模型调用配置(已废弃)
 */
@ConfigurationProperties(prefix = "cardflow")
public record AppProperties(Storage storage, App app, Llm llm) {
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
   * 大模型调用配置。
   *
   * <p><b>已废弃</b>:当前 ChatClient 配置改为从 {@code spring.ai.openai.*} 读取,
   * 此 record 不再被业务代码使用。保留是为了不破坏现有测试 fixture,
   * 未来删除前需先清理 {@code GenerationServiceTest} 等测试里的引用。</p>
   *
   * @param provider 当前启用的供应商标识
   * @param baseUrl DeepSeek API 基础地址
   * @param model DeepSeek 模型 ID
   * @param apiKey DeepSeek API Key,建议通过环境变量注入
   * @param maxTokens 单次生成最大输出 token 数
   */
  @Deprecated
  public record Llm(String provider, String baseUrl, String model, String apiKey, int maxTokens) {}
}
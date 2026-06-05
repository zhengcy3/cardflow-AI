package ai.cardflow.api.llm;

import ai.cardflow.api.model.ApiModels.GenerateContentRequest;

/**
 * LLM Provider 抽象边界。
 *
 * <p>真实模型、Mock Provider 或第三方供应商都必须通过该接口接入，避免业务层绑定具体厂商。</p>
 */
public interface LlmProvider {
  /**
   * Provider 名称。
   *
   * @return 用于 usage_record.model_name 的供应商或模型标识
   */
  String name();

  /**
   * 根据请求生成结构化卡片内容 JSON。
   *
   * @param request 内容生成参数
   * @return 可被前端编辑和渲染器消费的 JSON 字符串
   */
  String generateContent(GenerateContentRequest request);
}

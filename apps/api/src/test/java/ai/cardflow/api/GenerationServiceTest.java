package ai.cardflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.TopicInput;
import ai.cardflow.api.service.GenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 内容生成服务集成测试。
 *
 * <p>通过 Spring 上下文验证 Mock Provider、数据库初始化和生成任务记录可以协同工作。</p>
 */
@SpringBootTest
class GenerationServiceTest {
  @Autowired
  private GenerationService generationService;

  /**
   * 验证主题模式可以生成单卡结构 JSON。
   */
  @Test
  void generatesSingleCardJsonForTopicMode() {
    var response = generationService.generate(new GenerateContentRequest(
      "topic",
      "xhs_3_4",
      "precise_card",
      "xiaohongshu-highlight",
      new TopicInput("为什么学了很多却还是没成长？", "消费知识不等于成长", "短句化"),
      null
    ));

    assertThat(response.taskId()).isNotBlank();
    assertThat(response.contentJson()).contains("\"type\":\"single_card\"");
    assertThat(response.contentJson()).contains("为什么学了很多却还是没成长");
  }
}

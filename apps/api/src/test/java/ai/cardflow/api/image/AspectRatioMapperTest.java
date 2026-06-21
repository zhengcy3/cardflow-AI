package ai.cardflow.api.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AspectRatioMapperTest {

  @Test
  void mapsCardflowRatiosToMiniMaxAspectRatios() {
    assertThat(AspectRatioMapper.fromOutputFormat("xhs_3_4")).isEqualTo("3:4");
    assertThat(AspectRatioMapper.fromOutputFormat("youtube_16_9")).isEqualTo("16:9");
    assertThat(AspectRatioMapper.fromOutputFormat("bilibili_16_9")).isEqualTo("16:9");
    assertThat(AspectRatioMapper.fromOutputFormat("douyin_9_16")).isEqualTo("9:16");
    assertThat(AspectRatioMapper.fromOutputFormat(null)).isEqualTo("3:4");
  }
}

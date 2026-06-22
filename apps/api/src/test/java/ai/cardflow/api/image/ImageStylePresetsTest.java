package ai.cardflow.api.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageStylePresetsTest {

  @Test
  void resolvesKnownStyleKey() {
    ImageStylePresets.ImageStyle style = ImageStylePresets.resolve("swiss_grid");
    assertThat(style.key()).isEqualTo("swiss_grid");
    assertThat(style.palette()).isEqualTo("cool");
    assertThat(style.rendering()).isEqualTo("flat-vector");
  }

  @Test
  void normalizesHyphenatedKeys() {
    assertThat(ImageStylePresets.resolve("hand-sketch").key()).isEqualTo("hand_sketch");
  }

  @Test
  void fallsBackToDefaultForUnknownKey() {
    assertThat(ImageStylePresets.resolve("unknown").key()).isEqualTo("xiaohongshu_highlight");
  }

  @Test
  void knowledgePosterDowngradesWarmDocumentary() {
    assertThat(ImageStylePresets.resolve("warm_documentary").forKnowledgePoster().key())
      .isEqualTo("editorial_ink");
  }
}

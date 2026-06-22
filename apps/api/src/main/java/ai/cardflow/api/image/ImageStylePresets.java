package ai.cardflow.api.image;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * CardFlow 九套固定图片风格预设。
 *
 * <p>Palette × Rendering 组合，借鉴 baoyu-cover-image 与 guizang-ppt-skill 美学体系。
 * 用户通过 templateId 选择，后端解析 styleKey 后注入生成 prompt。</p>
 */
public final class ImageStylePresets {
  private static final String DEFAULT_KEY = "xiaohongshu_highlight";

  private static final Map<String, ImageStyle> BY_KEY = Map.ofEntries(
    entry("xiaohongshu_highlight", "小红书高亮", "warm", "flat-vector",
      "暖橙奶油底、色块高亮、关键词标签与下划线，扁平矢量小红书知识卡。",
      "暖色社交封面，主体居中，单一清晰隐喻，少文字。",
      "暖米色底、左文右图、便签贴纸、手写装饰、对勾清单。"),
    entry("minimal_apple", "极简苹果风", "mono", "digital",
      "深灰与白底、大留白、细线分割、sparse/balanced 布局、极少元素。",
      "单一主体 + 60% 负空间，高级灰，禁止 clutter。",
      "极大标题、极少要点、大量留白、mono 灰白。"),
    entry("business_briefing", "商业简报", "cool", "digital",
      "冷蓝浅灰、数据块、编号模块、list/comparison 理性层级。",
      "冷色理性封面，信息感，细线图表元素。",
      "冷蓝灰数据块、短标签、理性排版。"),
    entry("editorial_ink", "电子杂志墨水", "mono", "digital",
      "墨黑 + 暖米纸、细线网格、低饱和、杂志衬线气质。",
      "纪实摄影感、自然光、低饱和、Fujifilm editorial，留标题空间。",
      "黑白灰 + 一个低饱和 accent、细线、纸张质感。"),
    entry("swiss_grid", "瑞士网格", "cool", "flat-vector",
      "12 列网格、黑白银 + 单一 IKB 蓝 accent、直角模块、发丝线、无圆角无阴影。",
      "网格构图、非对称留白、单一 accent、极简 dashboard 结构。",
      "Helvetica 气质、短标签 ≤8 字、直角色块、极大字号对比。"),
    entry("hand_sketch", "手绘笔记", "macaron", "hand-drawn",
      "马卡龙色、手绘线条、马克笔填充、便签与波浪箭头。",
      "手绘插画隐喻、纸纹底、教育感。",
      "手绘装饰语、涂鸦图标、对勾清单、便签贴纸。"),
    entry("flat_infographic", "扁平信息图", "pastel", "flat-vector",
      "粉彩扁平、几何图标、flow/mindmap/quadrant 信息结构。",
      "扁平几何隐喻、粉彩、无渐变。",
      "粉彩扁平、单列竖排 3 张独立卡片、等间距、对勾清单；禁止 mindmap/quadrant/卡片重叠。"),
    entry("warm_documentary", "人文纪实", "earth", "painterly",
      "大地色、自然光、降级为 editorial 杂志排版。",
      "Leica/Fujifilm 纪实、真实场景、人文温度，禁止 AI 机器人。",
      "降级 editorial_ink：黑白灰纪实排版。"),
    entry("bold_cover", "高冲击封面", "vivid", "digital",
      "降级为高对比小红书排版。",
      "电影海报感、duotone 高饱和、单一强隐喻、动态能量。",
      "降级 xiaohongshu_highlight：高对比暖色海报。")
  );

  private ImageStylePresets() {}

  /**
   * 按 styleKey 查找风格，未知 key 回退默认小红书高亮。
   */
  public static ImageStyle resolve(String styleKey) {
    if (styleKey == null || styleKey.isBlank()) {
      return BY_KEY.get(DEFAULT_KEY);
    }
    return byKey(styleKey.trim()).orElse(BY_KEY.get(DEFAULT_KEY));
  }

  public static Optional<ImageStyle> byKey(String styleKey) {
    return Optional.ofNullable(BY_KEY.get(normalize(styleKey)));
  }

  private static String normalize(String styleKey) {
    return styleKey.trim().toLowerCase(Locale.ROOT).replace('-', '_');
  }

  private static Map.Entry<String, ImageStyle> entry(
    String key,
    String displayName,
    String palette,
    String rendering,
    String htmlCardGuidance,
    String creativeImageGuidance,
    String knowledgePosterGuidance
  ) {
    return Map.entry(key, new ImageStyle(
      key, displayName, palette, rendering,
      htmlCardGuidance, creativeImageGuidance, knowledgePosterGuidance
    ));
  }

  /**
   * 单套风格定义。
   */
  public record ImageStyle(
    String key,
    String displayName,
    String palette,
    String rendering,
    String htmlCardGuidance,
    String creativeImageGuidance,
    String knowledgePosterGuidance
  ) {
    /** 知识海报模式下对不适配风格的降级处理。 */
    public ImageStyle forKnowledgePoster() {
      return switch (key) {
        case "warm_documentary" -> ImageStylePresets.byKey("editorial_ink").orElse(this);
        case "bold_cover" -> ImageStylePresets.byKey("xiaohongshu_highlight").orElse(this);
        default -> this;
      };
    }
  }
}

package ai.cardflow.api.skill;

import java.util.List;
import org.springframework.core.io.Resource;

/**
 * 单个 Skill 的元数据。
 *
 * <p>对应 classpath:skills/&lt;name&gt;/meta.yaml 加同一目录下的 SKILL.md。</p>
 */
public record SkillMeta(
  String name,
  String description,
  String whenToApply,
  List<String> tags,
  String version,
  Resource contentResource
) {}
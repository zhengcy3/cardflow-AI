package ai.cardflow.api.skill;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 把 {@link SkillMeta} 列表格式化为 LLM 可读的 Skill 摘要。
 *
 * <p>输出每行一个 Skill,形如 {@code - <name>: <description>}。
 * 列表按 name 字典序排序,保证多次注入顺序稳定。</p>
 */
@Component
public class SkillSummaryFormatter {

  /**
   * 把 Skill 列表格式化为多行 bullet 文本。
   *
   * @param skills Skill 元数据列表
   * @return 多行 bullet 文本(无尾部换行)
   */
  public String format(List<SkillMeta> skills) {
    return skills.stream()
      .sorted(Comparator.comparing(SkillMeta::name))
      .map(meta -> "- " + meta.name() + ": " + meta.description())
      .collect(Collectors.joining("\n"));
  }
}
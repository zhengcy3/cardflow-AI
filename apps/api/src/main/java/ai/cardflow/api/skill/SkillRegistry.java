package ai.cardflow.api.skill;

import java.util.List;
import java.util.Optional;

/**
 * Skill 注册表。
 *
 * <p>集中提供 Skill 元数据查询、摘要文本生成和完整内容读取。
 * 启动时从 classpath 扫描所有 Skill,加载失败应 fail-fast。</p>
 */
public interface SkillRegistry {
  /**
   * 返回所有已加载的 Skill 元数据。
   *
   * @return 不可变列表
   */
  List<SkillMeta> all();

  /**
   * 按名称查找 Skill。
   *
   * @param name Skill 名称
   * @return 找到的 Skill 元数据,否则 {@code Optional.empty()}
   */
  Optional<SkillMeta> find(String name);

  /**
   * 返回所有 Skill 的摘要文本,用于拼到 system prompt 末尾。
   *
   * @return 多行 bullet 列表
   */
  String summary();

  /**
   * 读取某个 Skill 的 SKILL.md 完整内容。
   *
   * @param name Skill 名称
   * @return 完整 Markdown 内容
   * @throws IllegalArgumentException Skill 不存在时
   */
  String readFullContent(String name);
}
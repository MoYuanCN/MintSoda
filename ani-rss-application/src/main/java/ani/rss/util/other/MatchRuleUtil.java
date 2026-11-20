package ani.rss.util.other;

import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.entity.Item;
import ani.rss.entity.ReverseMatchAssignment;
import ani.rss.entity.ReverseMatchRule;
import ani.rss.enums.StringEnum;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/** Shared item filtering for RSS downloads and collection previews. */
@Slf4j
public final class MatchRuleUtil {
    private static final Config CONFIG = ConfigUtil.CONFIG;

    /**
     * 把订阅字幕组写成该值时，视为“反匹配订阅”：条目按反匹配规则路由到目标字幕组。
     * <p>
     * 英文标识 {@code REVERSE_MATCH} 同样接受，避免导入的配置因大小写或本地化失效。
     */
    public static final String REVERSE_MATCH_SUBGROUP = "反匹配";
    private static final String REVERSE_MATCH_ALIAS = "REVERSE_MATCH";
    /**
     * 单条订阅保留的路由记录上限。
     */
    private static final int MAX_ASSIGNMENTS = 500;

    private MatchRuleUtil() {
    }

    /**
     * Applies the configured rule priority: reverse match, global match, then exclusions.
     * Local inclusion rules remain a required condition for the normal matching path.
     */
    public static Evaluation evaluate(Ani ani, String title, String subgroupName) {
        boolean reverseEnabled = isReverseEnabled(ani);
        // 反匹配标记本身不是字幕组，后续判定统一使用占位名。
        String currentSubgroup = plainSubgroup(subgroupName);
        if (reverseEnabled) {
            ReverseMatchRule matched = firstReverseMatch(
                    Optional.ofNullable(ani.getReverseMatchRules()).orElseGet(List::of), title);
            // 只有显式启用全局反匹配，或用「反匹配」标记订阅时才回落到设置中的全局规则。
            if (matched == null && (Boolean.TRUE.equals(ani.getGlobalReverseMatch())
                    || isReverseMarker(ani.getSubgroup()))) {
                matched = firstReverseMatch(
                        Optional.ofNullable(CONFIG.getGlobalReverseMatchRules()).orElseGet(List::of), title);
            }
            if (matched != null) {
                String target = targetSubgroup(matched, currentSubgroup);
                // 字幕组标记只表达“按反匹配路由”，不提供原始字幕组，因此未配置目标时直接跳过。
                if ("未知字幕组".equals(target) && isReverseMarker(subgroupName)) {
                    return Evaluation.reject(target);
                }
                return Evaluation.accept(target, true);
            }
        }

        if (reverseEnabled) {
            // 订阅未单独指定时跟随设置中的全局默认。
            String missBehavior = StrUtil.blankToDefault(
                    ani.getReverseMatchMissBehavior(),
                    StrUtil.blankToDefault(CONFIG.getReverseMatchMissBehavior(), "SKIP"));
            if ("ALLOW".equalsIgnoreCase(missBehavior)) {
                return Evaluation.accept(currentSubgroup, false);
            }
            if (!"NORMAL".equalsIgnoreCase(missBehavior)) {
                return Evaluation.reject(currentSubgroup);
            }
        }

        List<String> localMatch = applicableRules(ani.getMatch(), currentSubgroup);
        if (!localMatch.isEmpty() && !matchesAll(localMatch, title)) {
            return Evaluation.reject(currentSubgroup);
        }

        if (Boolean.TRUE.equals(ani.getGlobalMatch())) {
            List<String> globalMatch = applicableRules(CONFIG.getGlobalMatch(), currentSubgroup);
            if (!globalMatch.isEmpty()) {
                // A positive global match has higher priority than exclusion rules.
                return matchesAll(globalMatch, title)
                        ? Evaluation.accept(currentSubgroup, false)
                        : Evaluation.reject(currentSubgroup);
            }
        }

        if (matchesAny(ani.getExclude(), title, currentSubgroup)) {
            return Evaluation.reject(currentSubgroup);
        }
        if (Boolean.TRUE.equals(ani.getGlobalExclude())
                && matchesAny(CONFIG.getExclude(), title, currentSubgroup)) {
            return Evaluation.reject(currentSubgroup);
        }
        return Evaluation.accept(currentSubgroup, false);
    }

    /**
     * 订阅是否按反匹配规则路由条目：显式开关、全局开关或字幕组标记三者之一。
     *
     * @param ani 订阅
     * @return 是否启用反匹配
     */
    public static boolean isReverseEnabled(Ani ani) {
        if (ani == null) {
            return false;
        }
        return Boolean.TRUE.equals(ani.getReverseMatchEnabled())
                || Boolean.TRUE.equals(ani.getGlobalReverseMatch())
                || isReverseMarker(ani.getSubgroup());
    }

    /**
     * 字幕组是否为反匹配标记。
     *
     * @param subgroup 字幕组
     * @return 是否为标记
     */
    public static boolean isReverseMarker(String subgroup) {
        if (StrUtil.isBlank(subgroup)) {
            return false;
        }
        String value = subgroup.trim();
        return REVERSE_MATCH_SUBGROUP.equals(value) || REVERSE_MATCH_ALIAS.equalsIgnoreCase(value);
    }

    /**
     * 记录反匹配命中结果，按集数长效保存。
     * <p>
     * 同集数再次命中时覆盖为最新目标字幕组；超过上限时丢弃最旧的记录，
     * 避免长期运行后订阅文件无限增长。
     *
     * @param ani     订阅
     * @param item    资源项
     * @param subgroup 命中的目标字幕组
     * @return 是否新增或更新了记录
     */
    public static boolean rememberAssignment(Ani ani, Item item, String subgroup) {
        if (ani == null || item == null || item.getEpisode() == null || StrUtil.isBlank(subgroup)) {
            return false;
        }
        List<ReverseMatchAssignment> assignments = ani.getReverseMatchAssignments();
        if (assignments == null) {
            assignments = new java.util.ArrayList<>();
            ani.setReverseMatchAssignments(assignments);
        }
        double episode = item.getEpisode();
        ReverseMatchAssignment existing = assignments.stream()
                .filter(it -> it != null && it.getEpisode() != null && it.getEpisode() == episode)
                .findFirst()
                .orElse(null);
        if (existing != null && subgroup.equals(existing.getSubgroup())) {
            return false;
        }
        if (existing == null) {
            existing = new ReverseMatchAssignment().setEpisode(episode);
            assignments.add(existing);
        }
        existing.setSubgroup(subgroup)
                .setTitle(StrUtil.nullToEmpty(item.getTitle()))
                .setUpdatedAt(System.currentTimeMillis());
        if (assignments.size() > MAX_ASSIGNMENTS) {
            assignments.sort(java.util.Comparator.comparingLong(
                    it -> it.getUpdatedAt() == null ? 0L : it.getUpdatedAt()));
            while (assignments.size() > MAX_ASSIGNMENTS) {
                assignments.remove(0);
            }
        }
        return true;
    }

    /**
     * 查询某集数已保存的目标字幕组。
     *
     * @param ani     订阅
     * @param episode 集数
     * @return 已保存的字幕组，没有记录时返回空串
     */
    public static String storedSubgroup(Ani ani, Double episode) {
        if (ani == null || episode == null || ani.getReverseMatchAssignments() == null) {
            return "";
        }
        return ani.getReverseMatchAssignments().stream()
                .filter(it -> it != null && it.getEpisode() != null && it.getEpisode().equals(episode))
                .map(ReverseMatchAssignment::getSubgroup)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse("");
    }

    public static boolean accepts(Ani ani, String title, String subgroupName) {
        return evaluate(ani, title, subgroupName).accepted();
    }

    public static ReverseMatchRule firstReverseMatch(List<ReverseMatchRule> rules, String title) {
        if (rules == null) return null;
        for (ReverseMatchRule rule : rules) {
            if (rule != null && StrUtil.isNotBlank(rule.getPattern()) && contains(rule.getPattern(), title)) {
                return rule;
            }
        }
        return null;
    }

    private static String targetSubgroup(ReverseMatchRule rule, String fallbackSubgroup) {
        if (StrUtil.isNotBlank(rule.getSubgroup())) {
            return rule.getSubgroup();
        }
        // 反匹配标记只是开关，不能当作字幕组写入条目。
        return StrUtil.blankToDefault(plainSubgroup(fallbackSubgroup), "未知字幕组");
    }

    /**
     * 反匹配标记只是开关，不能当作字幕组写入条目或标签；其余值原样返回。
     */
    private static String plainSubgroup(String subgroup) {
        return isReverseMarker(subgroup) ? "未知字幕组" : StrUtil.nullToEmpty(subgroup);
    }

    private static List<String> applicableRules(List<String> rules, String subgroupName) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .map(rule -> mapSubgroup(rule, subgroupName))
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    private static String mapSubgroup(String rule, String subgroupName) {
        if (StrUtil.isBlank(rule)) {
            return "";
        }
        String scopedSubgroup = ReUtil.get(StringEnum.SUBGROUP_REG_STR, rule, 1);
        if (StrUtil.isBlank(scopedSubgroup)) {
            return rule;
        }
        if (scopedSubgroup.equals(subgroupName)) {
            return ReUtil.get(StringEnum.SUBGROUP_REG_STR, rule, 2);
        }
        return "";
    }

    private static boolean matchesAny(List<String> rules, String title, String subgroupName) {
        for (String rule : applicableRules(rules, subgroupName)) {
            if (contains(rule, title)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAll(List<String> rules, String title) {
        for (String rule : rules) {
            if (!contains(rule, title)) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(String regex, String title) {
        try {
            return ReUtil.contains(regex, title);
        } catch (Exception e) {
            log.warn("匹配规则正则无效: {}", regex);
            return false;
        }
    }

    public record Evaluation(boolean accepted, String subgroup, boolean reverseMatched) {
        private static Evaluation accept(String subgroup, boolean reverseMatched) {
            return new Evaluation(true, subgroup, reverseMatched);
        }

        private static Evaluation reject(String subgroup) {
            return new Evaluation(false, subgroup, false);
        }
    }
}

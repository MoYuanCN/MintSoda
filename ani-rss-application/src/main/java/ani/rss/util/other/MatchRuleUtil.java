package ani.rss.util.other;

import ani.rss.entity.Ani;
import ani.rss.entity.Config;
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

    private MatchRuleUtil() {
    }

    /**
     * Applies the configured rule priority: reverse match, global match, then exclusions.
     * Local inclusion rules remain a required condition for the normal matching path.
     */
    public static Evaluation evaluate(Ani ani, String title, String subgroupName) {
        boolean reverseEnabled = Boolean.TRUE.equals(ani.getReverseMatchEnabled());
        List<ReverseMatchRule> localReverseRules = Optional.ofNullable(ani.getReverseMatchRules()).orElseGet(List::of);
        if (reverseEnabled) {
            ReverseMatchRule matched = firstReverseMatch(localReverseRules, title);
            if (matched != null) return Evaluation.accept(targetSubgroup(matched, subgroupName), true);
        }
        if (Boolean.TRUE.equals(ani.getGlobalReverseMatch())) {
            reverseEnabled = true;
            ReverseMatchRule matched = firstReverseMatch(
                    Optional.ofNullable(CONFIG.getGlobalReverseMatchRules()).orElseGet(List::of), title);
            if (matched != null) return Evaluation.accept(targetSubgroup(matched, subgroupName), true);
        }

        if (reverseEnabled) {
            String missBehavior = StrUtil.blankToDefault(ani.getReverseMatchMissBehavior(), "SKIP");
            if ("ALLOW".equalsIgnoreCase(missBehavior)) {
                return Evaluation.accept(subgroupName, false);
            }
            if (!"NORMAL".equalsIgnoreCase(missBehavior)) {
                return Evaluation.reject(subgroupName);
            }
        }

        List<String> localMatch = applicableRules(ani.getMatch(), subgroupName);
        if (!localMatch.isEmpty() && !matchesAll(localMatch, title)) {
            return Evaluation.reject(subgroupName);
        }

        if (Boolean.TRUE.equals(ani.getGlobalMatch())) {
            List<String> globalMatch = applicableRules(CONFIG.getGlobalMatch(), subgroupName);
            if (!globalMatch.isEmpty()) {
                // A positive global match has higher priority than exclusion rules.
                return matchesAll(globalMatch, title)
                        ? Evaluation.accept(subgroupName, false)
                        : Evaluation.reject(subgroupName);
            }
        }

        if (matchesAny(ani.getExclude(), title, subgroupName)) {
            return Evaluation.reject(subgroupName);
        }
        if (Boolean.TRUE.equals(ani.getGlobalExclude())
                && matchesAny(CONFIG.getExclude(), title, subgroupName)) {
            return Evaluation.reject(subgroupName);
        }
        return Evaluation.accept(subgroupName, false);
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
        return StrUtil.blankToDefault(rule.getSubgroup(), StrUtil.blankToDefault(fallbackSubgroup, "未知字幕组"));
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

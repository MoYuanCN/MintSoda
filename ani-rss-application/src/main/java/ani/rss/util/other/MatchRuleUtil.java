package ani.rss.util.other;

import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.enums.StringEnum;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
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
    public static boolean accepts(Ani ani, String title, String subgroupName) {
        List<String> reverseRules = new ArrayList<>();
        boolean reverseEnabled = Boolean.TRUE.equals(ani.getReverseMatchEnabled());
        if (reverseEnabled) {
            reverseRules.addAll(Optional.ofNullable(ani.getReverseMatchKeywords()).orElseGet(List::of));
        }
        if (Boolean.TRUE.equals(ani.getGlobalReverseMatch())) {
            reverseEnabled = true;
            reverseRules.addAll(Optional.ofNullable(CONFIG.getGlobalReverseMatch()).orElseGet(List::of));
        }

        if (reverseEnabled) {
            if (matchesAny(reverseRules, title, subgroupName)) {
                return true;
            }
            String missBehavior = StrUtil.blankToDefault(ani.getReverseMatchMissBehavior(), "SKIP");
            if ("ALLOW".equalsIgnoreCase(missBehavior)) {
                return true;
            }
            if (!"NORMAL".equalsIgnoreCase(missBehavior)) {
                return false;
            }
        }

        List<String> localMatch = applicableRules(ani.getMatch(), subgroupName);
        if (!localMatch.isEmpty() && !matchesAll(localMatch, title)) {
            return false;
        }

        if (Boolean.TRUE.equals(ani.getGlobalMatch())) {
            List<String> globalMatch = applicableRules(CONFIG.getGlobalMatch(), subgroupName);
            if (!globalMatch.isEmpty()) {
                // A positive global match has higher priority than exclusion rules.
                return matchesAll(globalMatch, title);
            }
        }

        if (matchesAny(ani.getExclude(), title, subgroupName)) {
            return false;
        }
        if (Boolean.TRUE.equals(ani.getGlobalExclude())
                && matchesAny(CONFIG.getExclude(), title, subgroupName)) {
            return false;
        }
        return true;
    }

    public static boolean matchesReverse(Ani ani, String title) {
        List<String> rules = new ArrayList<>();
        if (Boolean.TRUE.equals(ani.getReverseMatchEnabled())) {
            rules.addAll(Optional.ofNullable(ani.getReverseMatchKeywords()).orElseGet(List::of));
        }
        if (Boolean.TRUE.equals(ani.getGlobalReverseMatch())) {
            rules.addAll(Optional.ofNullable(CONFIG.getGlobalReverseMatch()).orElseGet(List::of));
        }
        return matchesAny(rules, title, ani.getSubgroup());
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
}

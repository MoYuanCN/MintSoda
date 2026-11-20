package ani.rss.config;

import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.date.DateTime;
import wushuo.tmdb.api.entity.Tmdb;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class DefaultAniFactory {
    private static final Config CONFIG = ConfigUtil.CONFIG;

    /**
     * 补齐外部来源（API 导入、旧配置）可能缺失的字段。
     * <p>
     * 订阅对象会从 WebUI 与导入接口直接反序列化，字段可能为 {@code null}；
     * 这里统一补默认值，避免各处对布尔与集合字段解引用时抛空指针。
     *
     * @param ani 待补齐的订阅，可为空
     * @return 同一个对象，便于链式调用
     */
    public static Ani normalize(Ani ani) {
        if (ani == null) {
            return null;
        }
        Ani defaults = create();
        if (ani.getId() == null) ani.setId(defaults.getId());
        if (ani.getTitle() == null) ani.setTitle("");
        if (ani.getUrl() == null) ani.setUrl("");
        if (ani.getType() == null) ani.setType("");
        if (ani.getBgmUrl() == null) ani.setBgmUrl("");
        if (ani.getSubgroup() == null) ani.setSubgroup("");
        if (ani.getThemoviedbName() == null) ani.setThemoviedbName("");
        if (ani.getImage() == null) ani.setImage("");
        if (ani.getMikanTitle() == null) ani.setMikanTitle("");
        if (ani.getWeekLabel() == null) ani.setWeekLabel("");
        if (ani.getSeason() == null) ani.setSeason(1);
        if (ani.getOffset() == null) ani.setOffset(0);
        if (ani.getScore() == null) ani.setScore(0.0);
        if (ani.getReleaseDate() == null) ani.setReleaseDate(new DateTime());
        if (ani.getCurrentEpisodeNumber() == null) ani.setCurrentEpisodeNumber(0);
        if (ani.getTotalEpisodeNumber() == null) ani.setTotalEpisodeNumber(0);
        if (ani.getLastDownloadTime() == null) ani.setLastDownloadTime(0L);
        if (ani.getEnable() == null) ani.setEnable(true);
        if (ani.getOva() == null) ani.setOva(false);
        if (ani.getOmit() == null) ani.setOmit(true);
        if (ani.getDownloadNew() == null) ani.setDownloadNew(false);
        if (ani.getMessage() == null) ani.setMessage(true);
        if (ani.getCompleted() == null) ani.setCompleted(true);
        if (ani.getCustomCompleted() == null) ani.setCustomCompleted(false);
        if (ani.getCustomDownloadPath() == null) ani.setCustomDownloadPath(false);
        if (ani.getCustomDownloadPathTemplate() == null) ani.setCustomDownloadPathTemplate("");
        if (ani.getCustomRenameTemplate() == null) ani.setCustomRenameTemplate("");
        if (ani.getCustomRenameTemplateEnable() == null) ani.setCustomRenameTemplateEnable(false);
        if (ani.getGlobalExclude() == null) ani.setGlobalExclude(false);
        if (ani.getGlobalMatch() == null) ani.setGlobalMatch(false);
        if (ani.getGlobalReverseMatch() == null) ani.setGlobalReverseMatch(false);
        if (ani.getReverseMatchEnabled() == null) ani.setReverseMatchEnabled(false);
        if (ani.getReverseMatchMissBehavior() == null) ani.setReverseMatchMissBehavior("");
        if (ani.getCustomPriorityKeywordsEnable() == null) ani.setCustomPriorityKeywordsEnable(false);
        if (ani.getCustomTagsEnable() == null) ani.setCustomTagsEnable(false);
        if (ani.getCustomEpisode() == null) ani.setCustomEpisode(false);
        if (ani.getCustomEpisodeStr() == null) ani.setCustomEpisodeStr("");
        if (ani.getCustomEpisodeGroupIndex() == null) ani.setCustomEpisodeGroupIndex(2);
        if (ani.getTmdb() == null) ani.setTmdb(defaults.getTmdb());
        if (ani.getStandbyRssList() == null) ani.setStandbyRssList(new ArrayList<>());
        if (ani.getNotDownload() == null) ani.setNotDownload(new ArrayList<>());
        if (ani.getCustomTags() == null) ani.setCustomTags(new ArrayList<>());
        if (ani.getCustomPriorityKeywords() == null) ani.setCustomPriorityKeywords(new ArrayList<>());
        if (ani.getReverseMatchRules() == null) ani.setReverseMatchRules(new ArrayList<>());
        if (ani.getReverseMatchAssignments() == null) ani.setReverseMatchAssignments(new ArrayList<>());
        if (ani.getReverseMatchSubgroupOffsets() == null) ani.setReverseMatchSubgroupOffsets(new LinkedHashMap<>());
        if (ani.getReverseMatchKeywords() == null) ani.setReverseMatchKeywords(List.of());
        if (ani.getMatch() == null) ani.setMatch(List.of());
        if (ani.getExclude() == null) ani.setExclude(List.of());
        return ani;
    }

    public static Ani create() {
        Ani ani = new Ani();
        return ani
                .setId(UUID.randomUUID().toString())
                .setMikanTitle("")
                .setStandbyRssList(new ArrayList<>())
                .setOffset(0)
                .setReleaseDate(new DateTime())
                .setEnable(true)
                .setOva(false)
                .setScore(0.0)
                .setLastDownloadTime(0L)
                .setImage("")
                .setThemoviedbName("")
                .setCustomDownloadPath(false)
                .setCustomDownloadPathTemplate("")
                .setGlobalExclude(false)
                .setGlobalMatch(false)
                .setGlobalReverseMatch(false)
                .setCurrentEpisodeNumber(0)
                .setTotalEpisodeNumber(0)
                .setMatch(List.of())
                .setReverseMatchEnabled(false)
                .setReverseMatchKeywords(List.of())
                .setReverseMatchRules(new ArrayList<>())
                .setReverseMatchAssignments(new ArrayList<>())
                .setReverseMatchSubgroupOffsets(new LinkedHashMap<>())
                // 留空表示跟随设置中的全局默认，避免新建订阅时固化旧默认值。
                .setReverseMatchMissBehavior("")
                .setExclude(List.of("720[Pp]", "\\d-\\d", "合集", "特别篇"))
                .setBgmUrl("")
                .setSubgroup("")
                .setCustomEpisode(CONFIG.getCustomEpisode())
                .setCustomEpisodeStr(CONFIG.getCustomEpisodeStr())
                .setCustomEpisodeGroupIndex(CONFIG.getCustomEpisodeGroupIndex())
                .setOmit(true)
                .setDownloadNew(false)
                .setNotDownload(new ArrayList<>())
                .setTmdb(
                        new Tmdb()
                                .setId("")
                                .setName("")
                                .setOriginalName("")
                                .setDate(new Date())
                )
                .setCustomRenameTemplate(CONFIG.getRenameTemplate())
                .setCustomRenameTemplateEnable(false)
                .setCustomPriorityKeywordsEnable(false)
                .setCustomPriorityKeywords(new ArrayList<>())
                .setMessage(true)
                .setCompleted(true)
                .setCustomCompleted(false)
                .setCustomCompletedPathTemplate("")
                .setCustomTags(new ArrayList<>())
                .setCustomTagsEnable(false);
    }
}

package mint.soda.entity;

import mint.soda.commons.DateAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;
import wushuo.tmdb.api.entity.Tmdb;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 订阅
 */
@Data
@Accessors(chain = true)
@Schema(description = "订阅")
public class Ani implements Serializable {
    private Integer sort;

    /**
     * id
     */
    @Schema(description = "id")
    private String id;

    /**
     * 不在页面显示
     */
    @Schema(description = "不在页面显示")
    private String mikanTitle;

    /**
     * RSS URL
     */
    @Schema(description = "RSS URL")
    private String url;

    private Boolean exists;

    /**
     * 备用rss
     */
    @Schema(description = "备用rss")
    private List<StandbyRss> standbyRssList;

    /**
     * 标题
     */
    @Schema(description = "标题")
    private String title;

    /**
     * 日语标题 来源于BGM
     */
    @Schema(description = "日语标题 来源于BGM")
    private String jpTitle;

    /**
     * 剧集偏移
     */
    @Schema(description = "剧集偏移")
    private Integer offset;

    /**
     * 发布日期
     */
    @Schema(description = "发布日期")
    @JsonAdapter(DateAdapter.class)
    private Date releaseDate;

    /**
     * 年度
     */
    @Schema(description = "年度")
    @Deprecated
    private Integer year;

    /**
     * 月
     */
    @Schema(description = "月")
    @Deprecated
    private Integer month;

    /**
     * 日
     */
    @Schema(description = "日")
    @Deprecated
    private Integer date;

    /**
     * 星期 1表示周日，2表示周一
     */
    @Schema(description = "星期 1表示周日，2表示周一")
    private String weekLabel;

    /**
     * 季度
     */
    @Schema(description = "季度")
    private Integer season;

    /**
     * 封面本地保存位置
     */
    @Schema(description = "封面本地保存位置")
    private String cover;

    /**
     * 图片 https://
     */
    @Schema(description = "图片 https://")
    private String image;

    /**
     * 字幕组
     */
    @Schema(description = "字幕组")
    private String subgroup;

    /**
     * 匹配
     */
    @Schema(description = "匹配")
    private List<String> match;

    /** Whether the subscription's reverse-match routing rules are enabled. */
    @Schema(description = "反匹配开关")
    private Boolean reverseMatchEnabled;

    /** Legacy reverse-match rules, migrated to reverseMatchRules during load. */
    @Schema(description = "反匹配关键词")
    private List<String> reverseMatchKeywords;

    /** Title regex rules and their destination subtitle groups. */
    @Schema(description = "反匹配规则")
    private List<ReverseMatchRule> reverseMatchRules;

    /** 是否应用配置中维护的全局反匹配规则。 */
    @Schema(description = "是否应用全局反匹配规则")
    private Boolean globalReverseMatch;

    /** 反匹配未命中后的处理：SKIP、NORMAL 或 ALLOW；留空跟随设置中的全局默认。 */
    @Schema(description = "反匹配未命中行为")
    private String reverseMatchMissBehavior;

    /** 反匹配路由记录，按集数长效保存命中结果。 */
    @Schema(description = "反匹配路由记录")
    private List<ReverseMatchAssignment> reverseMatchAssignments;

    /** 反匹配命中字幕组的独立集数偏移，键为字幕组，值为相对订阅偏移的增量。 */
    @Schema(description = "反匹配字幕组集数偏移")
    private Map<String, Integer> reverseMatchSubgroupOffsets;

    /**
     * 排除
     */
    @Schema(description = "排除")
    private List<String> exclude;

    /**
     * 是否启用全局排除
     */
    @Schema(description = "是否启用全局排除")
    private Boolean globalExclude;

    /** 是否应用全局匹配规则。 */
    @Schema(description = "是否应用全局匹配规则")
    private Boolean globalMatch;

    /**
     * 剧场版 or OVA
     */
    @Schema(description = "剧场版 or OVA")
    private Boolean ova;

    /**
     * 拼音
     */
    @Schema(description = "拼音")
    private String pinyin;

    /**
     * 拼音
     */
    @Schema(description = "拼音首字母")
    private String pinyinInitials;

    /**
     * 启用
     */
    @Schema(description = "启用")
    private Boolean enable;

    /**
     * 当前集数
     */
    @Schema(description = "当前集数")
    private Integer currentEpisodeNumber;

    /**
     * 总集数
     */
    @Schema(description = "总集数")
    private Integer totalEpisodeNumber;

    @Schema(description = "TheMovieDB 名称")
    private String themoviedbName;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "BGM 地址")
    private String bgmUrl;

    /**
     * 自定义下载位置
     */
    @Schema(description = "自定义下载位置")
    private Boolean customDownloadPath;

    /**
     * 自定义下载位置
     */
    @Schema(description = "自定义下载位置路径")
    @SerializedName(value = "customDownloadPathTemplate", alternate = "downloadPath")
    private String customDownloadPathTemplate;

    /**
     * 评分
     */
    @Schema(description = "评分")
    private Double score;

    /**
     * 自定义集数获取规则
     */
    @Schema(description = "自定义集数获取规则")
    private Boolean customEpisode;

    /**
     * 自定义集数获取规则
     */
    @Schema(description = "自定义集数获取规则表达式")
    private String customEpisodeStr;

    /**
     * 自定义集数获取规则 groupIndex
     */
    @Schema(description = "自定义集数获取规则 groupIndex")
    private Integer customEpisodeGroupIndex;

    /**
     * 遗漏检测
     */
    @Schema(description = "遗漏检测")
    private Boolean omit;

    /**
     * 只下载最新集
     */
    @Schema(description = "只下载最新集")
    private Boolean downloadNew;

    /**
     * 不进行下载的集
     */
    @Schema(description = "不进行下载的集")
    private List<Double> notDownload;

    /**
     * tmdb 相关信息
     */
    @Nullable
    @Schema(description = "TMDB 相关信息")
    private Tmdb tmdb;

    /**
     * 自定义重命名模版
     */
    @Schema(description = "自定义重命名模版开关")
    private Boolean customRenameTemplateEnable;

    /**
     * 自定义重命名模版
     */
    @Schema(description = "自定义重命名模版")
    private String customRenameTemplate;

    /**
     * 自定义优先保留开关
     */
    @Schema(description = "自定义优先保留开关")
    private Boolean customPriorityKeywordsEnable;

    /**
     * 自定义优先保留关键词列表
     */
    @Schema(description = "自定义优先保留关键词列表")
    private List<String> customPriorityKeywords;

    /**
     * 上次下载完成时间
     */
    @Schema(description = "上次下载完成时间")
    private Long lastDownloadTime;

    /**
     * 消息通知
     */
    @Schema(description = "消息通知")
    private Boolean message;

    /**
     * 完结迁移
     */
    @Schema(description = "完结迁移")
    private Boolean completed;

    /**
     * 自定义完结迁移
     */
    @Schema(description = "自定义完结迁移开关")
    private Boolean customCompleted;

    /**
     * 自定义完结迁移
     */
    @Schema(description = "自定义完结迁移路径模版")
    private String customCompletedPathTemplate;

    /**
     * 自定义标签开关
     */
    @Schema(description = "自定义标签开关")
    private Boolean customTagsEnable;

    /**
     * 单个订阅自定义标签
     */
    @Schema(description = "单个订阅自定义标签")
    private List<String> customTags;
}

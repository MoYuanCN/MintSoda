package ani.rss.util.other;

import ani.rss.commons.FileUtils;
import ani.rss.commons.MavenUtils;
import ani.rss.commons.URLUtils;
import ani.rss.config.DefaultConfigFactory;
import ani.rss.entity.Config;
import ani.rss.entity.NotificationConfig;
import ani.rss.entity.NotificationTemplate;
import ani.rss.entity.ReverseMatchRule;
import ani.rss.handle.JsonReader;
import ani.rss.handle.JsonWriter;
import ani.rss.util.basic.LogUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.DynaBean;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.lang.func.LambdaUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.OsInfo;
import cn.hutool.system.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ConfigUtil {

    public static final Config CONFIG = DefaultConfigFactory.create();
    public static final String FILE_NAME = "config.v2.json";
    private static volatile File resolvedConfigDir;

    /**
     * 获取设置文件夹
     *
     * @return 文件夹
     */
    public static File getConfigDir() {
        String configDir = SystemUtil.get("CONFIG");
        if (StrUtil.isNotBlank(configDir)) {
            return new File(configDir);
        }

        File cachedConfigDir = resolvedConfigDir;
        if (cachedConfigDir != null) {
            return cachedConfigDir;
        }

        synchronized (ConfigUtil.class) {
            if (resolvedConfigDir != null) {
                return resolvedConfigDir;
            }

            OsInfo osInfo = SystemUtil.getOsInfo();
            if (osInfo.isWindows()) {
                MavenUtils.CurrentFile currentFile = MavenUtils.getCurrentFile();
                if (currentFile.isExe()) {
                    File portableConfigDir = new File(currentFile.getFile().getAbsoluteFile().getParentFile(), "config");
                    migrateLegacyWindowsConfig(portableConfigDir);
                    resolvedConfigDir = portableConfigDir;
                    return resolvedConfigDir;
                }
            }

            // 若当前目录存在 config 则优先使用
            File file = new File("config").getAbsoluteFile();
            if (file.exists()) {
                resolvedConfigDir = file;
                return resolvedConfigDir;
            }

            // Windows（非 exe 运行）默认为 用户目录/ani-rss
            if (osInfo.isWindows()) {
                file = new File(FileUtil.getUserHomePath(), "ani-rss");
            }

            resolvedConfigDir = file;
            return resolvedConfigDir;
        }
    }

    private static void migrateLegacyWindowsConfig(File portableConfigDir) {
        File legacyConfigDir = new File(FileUtil.getUserHomePath(), "ani-rss");
        File legacyConfigFile = new File(legacyConfigDir, FILE_NAME);
        File portableConfigFile = new File(portableConfigDir, FILE_NAME);
        if (!legacyConfigFile.isFile() || portableConfigFile.exists()) {
            return;
        }

        try {
            // Keep the old directory as a backup and only fill missing portable files.
            FileUtil.copy(legacyConfigDir, portableConfigDir, false);
            log.info("已将旧配置复制到 exe 便携目录 {}，旧目录保留为备份", portableConfigDir);
        } catch (Exception e) {
            log.warn("迁移旧配置到 exe 便携目录失败 {}", portableConfigDir, e);
        }
    }

    /**
     * 获取设置文件
     *
     * @return 设置文件
     */
    public static File getConfigFile() {
        File configDir = getConfigDir();
        return new File(configDir + File.separator + FILE_NAME);
    }

    /**
     * 加载设置
     */
    public static synchronized void load() {
        File configFile = getConfigFile();

        Config config = JsonReader.getInstance(configFile)
                .toObject(Config.class, CONFIG);

        // JsonReader returns a fresh object, so convert the legacy minutes field before
        // copying over the defaults, which already contain rssSleepSeconds.
        if (config.getRssSleepSeconds() == null && config.getRssSleepMinutes() != null) {
            config.setRssSleepSeconds(config.getRssSleepMinutes() * 60);
        }

        CopyOptions copyOptions = CopyOptions
                .create()
                .setIgnoreNullValue(true);
        BeanUtil.copyProperties(config, CONFIG, copyOptions);
        format(CONFIG);
        LogUtil.loadLogback();
        log.debug("加载配置文件 {}", configFile);
        TorrentUtil.loadDownloadTool();
    }

    /**
     * 将设置保存到磁盘
     */
    public static synchronized void sync() {
        File configFile = getConfigFile();
        log.debug("保存配置 {}", configFile);
        try {
            ConfigUtil.format(CONFIG);

            // 写入到硬盘
            JsonWriter.getInstance(configFile)
                    .writer(CONFIG);

            LogUtil.loadLogback();
            log.debug("保存成功 {}", configFile);
        } catch (Exception e) {
            log.error("保存失败 {}", configFile);
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 处理设置内的url与文件路径标准
     *
     * @param config 设置
     */
    public static void format(Config config) {
        if (config.getExclude() == null) config.setExclude(new java.util.ArrayList<>());
        if (config.getGlobalMatch() == null) config.setGlobalMatch(new java.util.ArrayList<>());
        if (config.getGlobalReverseMatch() == null) config.setGlobalReverseMatch(new java.util.ArrayList<>());
        if (config.getGlobalReverseMatchRules() == null) config.setGlobalReverseMatchRules(new java.util.ArrayList<>());
        if (config.getImportGlobalReverseMatch() == null) config.setImportGlobalReverseMatch(false);
        if (config.getEnabledGlobalReverseMatch() == null) config.setEnabledGlobalReverseMatch(false);
        if (config.getImportGlobalMatch() == null) config.setImportGlobalMatch(false);
        if (config.getEnabledGlobalMatch() == null) config.setEnabledGlobalMatch(false);
        if (config.getImportExclude() == null) config.setImportExclude(false);
        if (config.getEnabledExclude() == null) config.setEnabledExclude(false);
        if (config.getNotificationTemplateList() == null) config.setNotificationTemplateList(new java.util.ArrayList<>());
        if (config.getNotificationConfigList() == null) config.setNotificationConfigList(new java.util.ArrayList<>());
        if (config.getDeveloperOptionsEnabled() == null) {
            config.setDeveloperOptionsEnabled(StrUtil.isNotBlank(config.getCustomTorrentUrl()));
        }
        migrateLegacyGlobalReverseRules(config);
        if (config.getCardOpacity() == null) config.setCardOpacity(92);
        if (config.getCardBlur() == null) config.setCardBlur(8);
        if (config.getAppCornerRadius() == null) config.setAppCornerRadius(12);
        if (config.getQbAuthType() == null) {
            config.setQbAuthType(StrUtil.startWith(config.getDownloadToolPassword(), "qbt_") ? "API_KEY" : "USER_PASSWORD");
        }
        if (config.getQbUsername() == null) config.setQbUsername("");
        if (config.getQbPassword() == null) config.setQbPassword("");
        if (config.getCustomTorrentUrl() == null) config.setCustomTorrentUrl("");
        if (config.getRssConcurrency() == null) config.setRssConcurrency(4);
        config.setRssConcurrency(Math.max(1, Math.min(16, config.getRssConcurrency())));
        if (config.getRssTimeout() == null) config.setRssTimeout(20);
        if (config.getAppBackgroundImage() == null) config.setAppBackgroundImage("");
        if (config.getLoginBackgroundImage() == null) config.setLoginBackgroundImage("");
        if (config.getLoginLogoImage() == null) config.setLoginLogoImage("");
        if (config.getAppLogoImage() == null) config.setAppLogoImage("");

        // Migrate existing settings to the only supported download client.
        config.setDownloadToolType("qBittorrent");
        Integer intervalSeconds = config.getRssSleepSeconds();
        if (intervalSeconds == null) {
            Integer legacyMinutes = config.getRssSleepMinutes();
            intervalSeconds = legacyMinutes == null ? 15 * 60 : legacyMinutes * 60;
        }
        intervalSeconds = Math.max(1, intervalSeconds);
        config.setRssSleepSeconds(intervalSeconds)
                .setRssSleepMinutes((int) ((intervalSeconds + 59L) / 60L));
        formatPath(config);
        formatUrl(config);

        String legacyNotificationTemplate = StrUtil.trimToEmpty(config.getNotificationTemplate());

        NotificationConfig newNotificationConfig = NotificationConfig.createNotificationConfig();

        List<NotificationConfig> notificationConfigList = config.getNotificationConfigList();

        // Migrate supported legacy webhook entries and discard removed notification integrations.
        notificationConfigList.removeIf(notificationConfig -> {
            if (notificationConfig == null || notificationConfig.getNotificationType() == null) {
                return true;
            }
            if (notificationConfig.getNotificationType() == ani.rss.enums.NotificationTypeEnum.WEB_HOOK) {
                notificationConfig.setNotificationType(ani.rss.enums.NotificationTypeEnum.CUSTOM_REQUEST);
                notificationConfig.setWebHookUrl(replaceLegacyMessageVariable(notificationConfig.getWebHookUrl()));
                notificationConfig.setWebHookHeader(replaceLegacyMessageVariable(notificationConfig.getWebHookHeader()));
                notificationConfig.setWebHookBody(replaceLegacyMessageVariable(notificationConfig.getWebHookBody()));
            }
            return !Set.of(
                    ani.rss.enums.NotificationTypeEnum.TELEGRAM,
                    ani.rss.enums.NotificationTypeEnum.EMBY_REFRESH,
                    ani.rss.enums.NotificationTypeEnum.CUSTOM_REQUEST
            ).contains(notificationConfig.getNotificationType());
        });

        CopyOptions copyOptions = CopyOptions
                .create()
                .setIgnoreNullValue(true)
                // 禁止覆盖模式 仅补全null值
                .setOverride(false);

        for (NotificationConfig notificationConfig : notificationConfigList) {
            if (StrUtil.isBlank(notificationConfig.getNotificationTemplateId())) {
                notificationConfig.setNotificationTemplateId(null);
            }
            BeanUtil.copyProperties(newNotificationConfig, notificationConfig, copyOptions);
        }

        String notificationContent = StrUtil.blankToDefault(legacyNotificationTemplate,
                NotificationTemplate.DEFAULT_TEXT_CONTENT);
        Map<String, String> migratedJsonTemplateIds = migrateNotificationTemplates(config, legacyNotificationTemplate);
        for (NotificationConfig notificationConfig : notificationConfigList) {
            notificationConfig.setWebHookUrl(NotificationTemplate.replaceLegacyNotificationPlaceholder(
                    notificationConfig.getWebHookUrl(), notificationContent, false));
            notificationConfig.setWebHookHeader(NotificationTemplate.replaceLegacyNotificationPlaceholder(
                    notificationConfig.getWebHookHeader(), notificationContent, false));
            notificationConfig.setNotificationTemplate(NotificationTemplate.replaceLegacyNotificationPlaceholder(
                    notificationConfig.getNotificationTemplate(), notificationContent, false));
            notificationConfig.setWebHookBody(NotificationTemplate.replaceLegacyNotificationPlaceholder(
                    notificationConfig.getWebHookBody(), notificationContent,
                    StrUtil.containsIgnoreCase(notificationConfig.getWebHookContentType(), "json")));
            if (notificationConfig.getNotificationType() == ani.rss.enums.NotificationTypeEnum.CUSTOM_REQUEST) {
                String jsonTemplateId = migratedJsonTemplateIds.get(notificationConfig.getNotificationTemplateId());
                if (jsonTemplateId != null) notificationConfig.setNotificationTemplateId(jsonTemplateId);
            }
        }
        config.setNotificationTemplate("");
    }

    private static void migrateLegacyGlobalReverseRules(Config config) {
        List<ReverseMatchRule> rules = config.getGlobalReverseMatchRules();
        List<String> legacyRules = config.getGlobalReverseMatch();
        if (rules.isEmpty() && legacyRules != null) {
            for (String legacyRule : legacyRules) {
                if (StrUtil.isBlank(legacyRule)) continue;
                String subgroup = "";
                String pattern = legacyRule;
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("^\\{\\{(.+)}}:(.+)$")
                        .matcher(legacyRule);
                if (matcher.matches()) {
                    subgroup = matcher.group(1);
                    pattern = matcher.group(2);
                }
                rules.add(new ReverseMatchRule().setPattern(pattern).setSubgroup(subgroup));
            }
        }
        config.setGlobalReverseMatch(new java.util.ArrayList<>());
    }

    /** Split old dual-purpose templates into text and JSON templates without losing selections. */
    private static Map<String, String> migrateNotificationTemplates(Config config, String legacyNotificationTemplate) {
        String notificationContent = StrUtil.blankToDefault(legacyNotificationTemplate,
                NotificationTemplate.DEFAULT_TEXT_CONTENT);
        List<NotificationTemplate> templates = config.getNotificationTemplateList();
        Map<String, String> jsonTemplateIds = new java.util.HashMap<>();
        java.util.Set<String> usedIds = templates.stream()
                .filter(java.util.Objects::nonNull)
                .map(NotificationTemplate::getId)
                .filter(StrUtil::isNotBlank)
                .collect(java.util.stream.Collectors.toSet());
        List<NotificationTemplate> normalized = new java.util.ArrayList<>();

        for (NotificationTemplate template : templates) {
            if (template == null) continue;
            if (StrUtil.isNotBlank(template.getType()) && template.getContent() != null) {
                boolean json = "JSON".equalsIgnoreCase(template.getType());
                template.setType(json ? "JSON" : "TEXT");
                String content = template.getContent();
                if (StrUtil.isNotBlank(legacyNotificationTemplate)
                        && "default".equals(template.getId())
                        && NotificationTemplate.DEFAULT_TEXT_CONTENT.equals(content)) {
                    content = legacyNotificationTemplate;
                } else if ("default-json".equals(template.getId())
                        && NotificationTemplate.LEGACY_DEFAULT_JSON_CONTENT.equals(content)) {
                    content = NotificationTemplate.DEFAULT_JSON_CONTENT;
                }
                content = NotificationTemplate.replaceLegacyNotificationPlaceholder(content, notificationContent, json);
                template.setContent(content);
                if (json) {
                    template.setCustomRequestBody(content).setTelegramTemplate(null);
                } else {
                    template.setTelegramTemplate(content).setCustomRequestBody(null);
                }
                normalized.add(template);
                continue;
            }

            String textContent = NotificationTemplate.replaceLegacyNotificationPlaceholder(
                    template.getTelegramTemplate(), notificationContent, false);
            String jsonContent = NotificationTemplate.replaceLegacyNotificationPlaceholder(
                    template.getCustomRequestBody(), notificationContent, true);
            if (StrUtil.isNotBlank(textContent) && StrUtil.isNotBlank(jsonContent)) {
                String legacyId = template.getId();
                template.setType("TEXT").setContent(textContent).setCustomRequestBody(null);
                normalized.add(template);

                String jsonId = legacyId + "-json";
                while (usedIds.contains(jsonId)) jsonId = java.util.UUID.randomUUID().toString();
                usedIds.add(jsonId);
                NotificationTemplate jsonTemplate = new NotificationTemplate()
                        .setId(jsonId)
                        .setName(StrUtil.blankToDefault(template.getName(), "通知模板") + " JSON")
                        .setType("JSON")
                        .setContent(jsonContent)
                        .setCustomRequestBody(jsonContent);
                normalized.add(jsonTemplate);
                if (StrUtil.isNotBlank(legacyId)) jsonTemplateIds.put(legacyId, jsonId);
            } else if (StrUtil.isNotBlank(jsonContent)) {
                template.setType("JSON").setContent(jsonContent).setTelegramTemplate(null);
                normalized.add(template);
            } else {
                template.setType("TEXT").setContent(StrUtil.nullToEmpty(textContent))
                        .setTelegramTemplate(StrUtil.nullToEmpty(textContent)).setCustomRequestBody(null);
                normalized.add(template);
            }
        }

        config.setNotificationTemplateList(normalized);
        return jsonTemplateIds;
    }

    private static String replaceLegacyMessageVariable(String template) {
        return template == null ? null : template.replace("${message}", "${notification}");
    }

    /**
     * 处理url
     *
     * @param config 设置
     */
    public static void formatUrl(Config config) {
        List<Func1<Config, String>> func1List = List.of(
                Config::getDownloadToolHost,
                Config::getMikanHost,
                Config::getTmdbApi
        );

        DynaBean dynaBean = DynaBean.create(config);

        for (Func1<Config, String> func1 : func1List) {
            String fieldName = LambdaUtil.getFieldName(func1);
            String v = func1.callWithRuntimeException(config);
            v = URLUtils.getUrlStr(v);
            dynaBean.set(fieldName, v);
        }
    }

    /**
     * 处理文件路径
     *
     * @param config 设置
     */
    public static void formatPath(Config config) {
        List<Func1<Config, String>> func1List = List.of(
                Config::getDownloadPathTemplate,
                Config::getOvaDownloadPathTemplate,
                Config::getCompletedPathTemplate
        );

        DynaBean dynaBean = DynaBean.create(config);

        for (Func1<Config, String> func1 : func1List) {
            String fieldName = LambdaUtil.getFieldName(func1);
            String v = func1.callWithRuntimeException(config);
            v = FileUtils.getAbsolutePath(v);
            dynaBean.set(fieldName, v);
        }
    }

}

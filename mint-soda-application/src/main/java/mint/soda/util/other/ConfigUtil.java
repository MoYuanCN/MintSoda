package mint.soda.util.other;

import mint.soda.commons.FileUtils;
import mint.soda.commons.MavenUtils;
import mint.soda.commons.StoragePaths;
import mint.soda.commons.URLUtils;
import mint.soda.config.DefaultConfigFactory;
import mint.soda.commons.CredentialUtils;
import mint.soda.entity.Config;
import mint.soda.entity.Login;
import mint.soda.entity.NotificationConfig;
import mint.soda.entity.NotificationStatusStyle;
import mint.soda.entity.NotificationTemplate;
import mint.soda.entity.ReverseMatchRule;
import mint.soda.enums.NotificationTypeEnum;
import mint.soda.handle.JsonReader;
import mint.soda.handle.JsonWriter;
import mint.soda.util.basic.LogUtil;
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
    public static final String FILE_NAME = StoragePaths.CONFIG_FILE_NAME;
    private static volatile File resolvedConfigDir;

    /**
     * 获取设置文件夹，并确保存储布局已绑定。
     *
     * @return 文件夹
     */
    public static File getConfigDir() {
        File dir = resolveConfigDir();
        StoragePaths.bind(dir);
        return dir;
    }

    /**
     * 解析设置文件夹，不触发存储布局初始化。
     *
     * @return 文件夹
     */
    private static File resolveConfigDir() {
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
            File applicationDir = MavenUtils.getApplicationDir();
            if (osInfo.isWindows()) {
                MavenUtils.CurrentFile currentFile = MavenUtils.getCurrentFile();
                if (currentFile.isExe()) {
                    // 便携运行：配置与数据始终跟随 EXE 所在目录。
                    File exeDir = currentFile.getFile().getAbsoluteFile().getParentFile();
                    resolvedConfigDir = new File(exeDir, "config");
                    return resolvedConfigDir;
                }
            }

            // 程序目录下的 config/ 或 data/ 视为挂载或便携布局，优先于工作目录。
            if (applicationDir != null
                    && (new File(applicationDir, "config").isDirectory()
                    || new File(applicationDir, "data").isDirectory())) {
                resolvedConfigDir = new File(applicationDir, "config");
                return resolvedConfigDir;
            }

            // 若当前目录存在 config 则优先使用
            File file = new File("config").getAbsoluteFile();
            if (file.exists()) {
                resolvedConfigDir = file;
                return resolvedConfigDir;
            }

            // Windows（非 exe 运行）默认为 用户目录/MintSoda
            if (osInfo.isWindows()) {
                file = new File(FileUtil.getUserHomePath(), "MintSoda");
            }

            resolvedConfigDir = file;
            return resolvedConfigDir;
        }
    }

    /**
     * 获取设置文件
     *
     * @return 设置文件
     */
    public static File getConfigFile() {
        getConfigDir();
        return StoragePaths.configFile();
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
        log.info("配置目录 {}，数据目录 {}", StoragePaths.configDir(), StoragePaths.dataDir());
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
        if (config.getNotificationConfigList() == null) {
            config.setNotificationConfigList(new java.util.ArrayList<>());
        }
        // 状态样式允许逐项覆盖：缺字段时补默认值，不覆盖用户已经调整的项。
        Map<String, NotificationStatusStyle> statusStyles = config.getNotificationStatusStyles();
        if (statusStyles == null) {
            statusStyles = new java.util.LinkedHashMap<>();
        }
        for (Map.Entry<String, NotificationStatusStyle> entry : NotificationStatusUtil.defaultStyles().entrySet()) {
            NotificationStatusStyle style = statusStyles.get(entry.getKey());
            NotificationStatusStyle value = entry.getValue();
            if (style == null) {
                statusStyles.put(entry.getKey(), value);
                continue;
            }
            if (StrUtil.isBlank(style.getEmoji())) style.setEmoji(value.getEmoji());
            if (StrUtil.isBlank(style.getLabel())) style.setLabel(value.getLabel());
            if (StrUtil.isBlank(style.getTemplate())) style.setTemplate(value.getTemplate());
        }
        config.setNotificationStatusStyles(statusStyles);
        if (config.getPluginMarketUrl() == null) config.setPluginMarketUrl("");
        // 实验性功能默认开启；旧配置里显式关闭的保持不变。
        if (config.getDeveloperOptionsEnabled() == null) {
            config.setDeveloperOptionsEnabled(true);
        }
        migrateLegacyGlobalReverseRules(config);
        if (config.getCardOpacity() == null) config.setCardOpacity(80);
        if (config.getCardBlur() == null) config.setCardBlur(14);
        if (config.getAppCornerRadius() == null) config.setAppCornerRadius(12);
        if (config.getQbAuthType() == null) {
            config.setQbAuthType(StrUtil.startWith(config.getDownloadToolPassword(), "qbt_") ? "API_KEY" : "USER_PASSWORD");
        }
        if (config.getQbUsername() == null) config.setQbUsername("");
        if (config.getQbPassword() == null) config.setQbPassword("");
        if (config.getCustomTorrentUrl() == null) config.setCustomTorrentUrl("");
        if (config.getCustomUserAgent() == null) config.setCustomUserAgent("");
        // User-Agent 会被写入每个出站请求头，去掉控制字符并限制长度，防止请求头注入。
        String userAgent = StrUtil.trimToEmpty(config.getCustomUserAgent())
                .replaceAll("[\r\n\t]", "");
        config.setCustomUserAgent(StrUtil.maxLength(userAgent, 256));
        if (config.getDevHttpTrace() == null) config.setDevHttpTrace(false);
        if (config.getCollectionDownloadPathTemplate() == null) config.setCollectionDownloadPathTemplate("");
        String reverseMissBehavior = StrUtil.trimToEmpty(config.getReverseMatchMissBehavior())
                .toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("SKIP", "NORMAL", "ALLOW").contains(reverseMissBehavior)) {
            reverseMissBehavior = "SKIP";
        }
        config.setReverseMatchMissBehavior(reverseMissBehavior);
        if (config.getRssConcurrency() == null) config.setRssConcurrency(4);
        config.setRssConcurrency(Math.max(1, Math.min(16, config.getRssConcurrency())));
        if (config.getRssTimeout() == null) config.setRssTimeout(20);
        if (config.getAppBackgroundImage() == null) config.setAppBackgroundImage("");
        if (config.getLoginBackgroundImage() == null) config.setLoginBackgroundImage("");
        if (config.getAppBackgroundImageDark() == null) config.setAppBackgroundImageDark("");
        if (config.getLoginBackgroundImageDark() == null) config.setLoginBackgroundImageDark("");
        if (config.getBackgroundBlur() == null) config.setBackgroundBlur(10);
        config.setBackgroundBlur(Math.max(0, Math.min(40, config.getBackgroundBlur())));
        if (config.getBackgroundOpacity() == null) config.setBackgroundOpacity(100);
        config.setBackgroundOpacity(Math.max(0, Math.min(100, config.getBackgroundOpacity())));
        if (config.getLoginLogoImage() == null) config.setLoginLogoImage("");
        if (config.getAppLogoImage() == null) config.setAppLogoImage("");
        if (config.getAccentColor() == null) config.setAccentColor("");
        if (config.getThemeDynamicColor() == null) config.setThemeDynamicColor(false);
        if (config.getNavBlur() == null) config.setNavBlur(18);
        if (config.getNavOpacity() == null) config.setNavOpacity(78);
        if (config.getDialogBlur() == null) config.setDialogBlur(16);
        if (config.getDialogOpacity() == null) config.setDialogOpacity(84);
        if (config.getCacheAutoClear() == null) config.setCacheAutoClear(false);
        if (config.getCacheAutoClearHours() == null) config.setCacheAutoClearHours(72);
        config.setCacheAutoClearHours(Math.max(1, Math.min(24 * 30, config.getCacheAutoClearHours())));
        if (config.getCacheLastClearTime() == null) config.setCacheLastClearTime(0L);
        migrateLoginCredentials(config);

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
            if (notificationConfig.getNotificationType() == NotificationTypeEnum.WEB_HOOK) {
                notificationConfig.setNotificationType(NotificationTypeEnum.CUSTOM_REQUEST);
                notificationConfig.setWebHookUrl(replaceLegacyMessageVariable(notificationConfig.getWebHookUrl()));
                notificationConfig.setWebHookHeader(replaceLegacyMessageVariable(notificationConfig.getWebHookHeader()));
                notificationConfig.setWebHookBody(replaceLegacyMessageVariable(notificationConfig.getWebHookBody()));
            }
            return !Set.of(
                    NotificationTypeEnum.TELEGRAM,
                    NotificationTypeEnum.EMBY_REFRESH,
                    NotificationTypeEnum.CUSTOM_REQUEST,
                    NotificationTypeEnum.PLUGIN
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
            if (notificationConfig.getNotificationType() == NotificationTypeEnum.CUSTOM_REQUEST) {
                String jsonTemplateId = migratedJsonTemplateIds.get(notificationConfig.getNotificationTemplateId());
                if (jsonTemplateId != null) notificationConfig.setNotificationTemplateId(jsonTemplateId);
            }
        }
        config.setNotificationTemplate("");
    }

    /**
     * 补齐登录凭据状态。
     * <p>
     * 旧配置可能仍是 admin/admin，直接标记为已配置以免升级后无法登录，
     * 但会提示尽快修改；只有用户名或密码为空的全新配置才算未初始化。
     */
    private static void migrateLoginCredentials(Config config) {
        Login login = config.getLogin();
        if (login == null) {
            config.setLogin(new Login().setUsername("").setPassword(""));
        }
        if (Boolean.TRUE.equals(config.getLoginConfigured())) {
            return;
        }
        String username = login.getUsername();
        String password = login.getPassword();
        boolean hasCredentials = StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password);
        config.setLoginConfigured(hasCredentials);
        if (hasCredentials && "admin".equals(username)
                && CredentialUtils.md5("admin").equalsIgnoreCase(password)) {
            log.warn("检测到默认账号 admin/admin，请在设置中修改用户名与强密码");
        }
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

        // 合集保存位置留空表示不启用，不能归一化为当前工作目录。
        String collectionPath = config.getCollectionDownloadPathTemplate();
        config.setCollectionDownloadPathTemplate(
                StrUtil.isBlank(collectionPath) ? "" : FileUtils.getAbsolutePath(collectionPath));
    }

}

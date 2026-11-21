<template>
  <div class="config-page app-page-layout">
    <PageHeaderView :title="$t('settings')" :subtitle="activeDescription">
      <template #actions>
        <el-tag v-if="dirty" class="config-header-status" type="warning" effect="light">
          {{ $t('nunsavedchanges', {n: changedKeys.length}) }}
        </el-tag>
        <el-tag v-else class="config-header-status" type="success" effect="plain">{{ $t('saved') }}</el-tag>
      </template>
    </PageHeaderView>

    <div v-if="loading" class="config-content app-page-content app-page-padding">
      <div class="config-skeleton">
        <el-skeleton :rows="3" animated/>
        <el-skeleton :rows="4" animated/>
        <el-skeleton :rows="3" animated/>
      </div>
    </div>

    <div v-else class="config-content app-page-content app-page-padding">
      <div class="config-toolbar">
        <el-input v-model="searchText" class="config-search" clearable
                  :placeholder="$t('searchsettingsegintervalnotificationproxy')"
                  prefix-icon="Search"/>
      </div>

      <div v-if="filteredGroups.length" class="settings-layout">
        <nav class="settings-nav" :aria-label="$t('settings')">
          <div v-for="group in filteredGroups" :key="group.name" class="settings-nav-group">
            <div class="settings-nav-title">{{ group.label }}</div>
            <button v-for="tab in group.items" :key="tab.name" type="button"
                    class="settings-nav-item" :class="{'is-active': tab.name === activeName}"
                    @click="activeName = tab.name">
              <span class="settings-nav-label">{{ tab.label }}</span>
              <span v-if="isTabDirty(tab.name)" class="settings-nav-dot"/>
            </button>
          </div>
        </nav>

        <section class="settings-panel">
          <header class="settings-panel-heading">
            <h3 class="settings-panel-title">{{ activeTab?.label }}</h3>
            <p v-if="activeDescription" class="settings-panel-description">{{ activeDescription }}</p>
          </header>
          <el-scrollbar class="settings-scrollbar">
            <div class="settings-panel-content">
              <component :is="activeComponent" :config="config"/>
            </div>
          </el-scrollbar>
        </section>
      </div>

      <div v-else class="config-empty">
        <el-empty :description="$t('nomatchingsettingspage')"/>
      </div>
    </div>

    <div class="config-savebar" :class="{'is-dirty': dirty}">
      <el-text class="config-savebar-text" size="small">
        {{ dirty ? $t('nunsavedchanges', {n: changedKeys.length}) : $t('allchangessaved') }}
        <span class="config-savebar-tip">{{ $t('ctrlstosave') }}</span>
      </el-text>
      <div class="config-savebar-actions">
        <el-button bg text :disabled="!dirty || saving" @click="discardChanges">{{ $t('discardchanges') }}</el-button>
        <el-button type="primary" :disabled="!dirty" :loading="saving" @click="saveConfig">
          {{ $t('savesettings') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed, onActivated, onMounted, ref, watch} from "vue"
import {onBeforeRouteLeave} from "vue-router"
import {ElMessage, ElMessageBox} from "element-plus"
import {md5} from "js-md5"
import {useEventListener} from "@vueuse/core"

import AppearanceSettingsView from "@/view/config/AppearanceSettingsView.vue"
import SubscriptionSettingsView from "@/view/config/SubscriptionSettingsView.vue"
import NamingSettingsView from "@/view/config/NamingSettingsView.vue"
import DownloadSettingsView from "@/view/config/DownloadSettingsView.vue"
import MetadataSettingsView from "@/view/config/MetadataSettingsView.vue"
import MatchRulesView from "@/view/config/MatchRulesView.vue"
import NotificationView from "@/view/config/NotificationView.vue"
import NetworkSettingsView from "@/view/config/NetworkSettingsView.vue"
import SecuritySettingsView from "@/view/config/SecuritySettingsView.vue"
import SystemSettingsView from "@/view/config/SystemSettingsView.vue"
import DeveloperView from "@/view/config/DeveloperView.vue"
import AboutView from "@/view/config/AboutView.vue"
import AfdianView from "@/view/config/AfdianView.vue"
import PageHeaderView from "@/view/custom/PageHeaderView.vue"
import {configData} from "@/js/config.js"
import {applyUiCustomization} from "@/js/global.js"
import * as http from "@/js/http.js"

const {t} = useI18n()
const saving = ref(false)
const loading = ref(true)
const searchText = ref('')
const activeName = ref('appearance')

const config = ref(configData)
const baseline = ref('')

const groups = [
  {
    name: 'common',
    label: t('navgroupcommon'),
    items: [
      {
        name: 'appearance',
        label: t('appearanceandtheme'),
        description: t('appearancehint'),
        keywords: '外观 主题 背景 模糊 透明度 圆角 强调色 logo 布局 排序 apperance theme background blur opacity'
      },
      {
        name: 'subscription',
        label: t('subscriptionsandupdates'),
        description: t('rssandupdateshint'),
        keywords: 'rss 订阅 刷新 间隔 并发 总集数 完结 备用 跳过 om it 偏移 默认规则'
      },
      {
        name: 'naming',
        label: t('namingandpaths'),
        description: t('naming') + ' · ' + t('savingpaths'),
        keywords: '重命名 模板 保存位置 路径 剧场版 合集 字幕文件夹 rename path template'
      },
      {
        name: 'download',
        label: t('downloadandtasks'),
        description: t('downloaderconnection') + ' · ' + t('taskexecution'),
        keywords: 'qbittorrent 下载器 地址 标签 限速 做种 延迟 重试 清理 priority'
      }
    ]
  },
  {
    name: 'rules',
    label: t('navgrouprules'),
    items: [
      {
        name: 'match',
        label: t('matchingrules'),
        description: t('excludeunwantedreleasesinoneplace'),
        keywords: '匹配 排除 反匹配 正则 字幕组 match exclude reverse regex'
      },
      {
        name: 'notification',
        label: t('notifications'),
        description: t('channelsandeventnotifications'),
        keywords: 'telegram 通知 webhook json 模板 emby 状态样式'
      }
    ]
  },
  {
    name: 'services',
    label: t('navgroupservice'),
    items: [
      {
        name: 'metadata',
        label: t('metadatasource'),
        description: t('siteandmetadatahint'),
        keywords: 'mikan bangumi tmdb 元数据 token api 封面 github'
      },
      {
        name: 'network',
        label: t('networkandproxy'),
        description: t('networkproxyandconnectionsettings'),
        keywords: '代理 proxy http socks 网络 测试'
      },
      {
        name: 'security',
        label: t('securityandaccess'),
        description: t('accountandaccesssecurity'),
        keywords: '密码 账号 api key 白名单 跨域 登录 反代 会话'
      },
      {
        name: 'system',
        label: t('systemandmaintenance'),
        description: t('maintenance'),
        keywords: '备份 缓存 日志 自启 更新 debug ics'
      }
    ]
  },
  {
    name: 'more',
    label: t('navgroupother'),
    items: [
      {
        name: 'developer',
        label: t('developer'),
        description: t('developeroptionsrequestdiagnosticsandcustomt'),
        keywords: '开发者 种子 info_hash 调试 诊断 ua 请求日志'
      },
      {
        name: 'about',
        label: t('about'),
        description: t('versionandprojectlinks'),
        keywords: '版本 更新 关于 重启 关闭 退出'
      },
      {
        name: 'donate',
        label: t('donate'),
        description: t('supportongoingmaintenance'),
        keywords: '捐赠 爱发电'
      }
    ]
  }
]

const views = {
  appearance: AppearanceSettingsView,
  subscription: SubscriptionSettingsView,
  naming: NamingSettingsView,
  download: DownloadSettingsView,
  match: MatchRulesView,
  notification: NotificationView,
  metadata: MetadataSettingsView,
  network: NetworkSettingsView,
  security: SecuritySettingsView,
  system: SystemSettingsView,
  developer: DeveloperView,
  about: AboutView,
  donate: AfdianView
}

const allTabs = groups.flatMap(group => group.items)
const activeTab = computed(() => allTabs.find(tab => tab.name === activeName.value))
const activeDescription = computed(() => activeTab.value?.description || '')
const activeComponent = computed(() => views[activeName.value] || AppearanceSettingsView)

const filteredGroups = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  const matches = tab => !keyword
      || `${tab.label} ${tab.description} ${tab.keywords || ''}`.toLowerCase().includes(keyword)
  return groups
      .map(group => ({...group, items: group.items.filter(matches)}))
      .filter(group => group.items.length)
})

watch(filteredGroups, list => {
  const items = list.flatMap(group => group.items)
  if (!items.length) return
  if (!items.some(tab => tab.name === activeName.value)) {
    activeName.value = items[0].name
  }
})

const normalize = value => JSON.parse(JSON.stringify(value ?? {}))

const stableStringify = value => JSON.stringify(value, (key, item) => {
  if (item && typeof item === 'object' && !Array.isArray(item)) {
    return Object.keys(item).sort().reduce((sorted, k) => {
      sorted[k] = item[k]
      return sorted
    }, {})
  }
  return item
})

// 逐键比较即可得出是否有改动，避免每次渲染都重新序列化整份设置。
const changedKeys = computed(() => {
  if (!baseline.value) return []
  const current = normalize(config.value)
  const saved = JSON.parse(baseline.value)
  const keys = new Set([...Object.keys(current), ...Object.keys(saved)])
  return [...keys].filter(key => stableStringify(current[key]) !== stableStringify(saved[key]))
})

const dirty = computed(() => changedKeys.value.length > 0)

const tabKeyMap = {
  appearance: new Set([
    'accentColor', 'themeDynamicColor', 'cardOpacity', 'cardBlur', 'appCornerRadius',
    'navOpacity', 'navBlur', 'dialogOpacity', 'dialogBlur',
    'appBackgroundImage', 'appBackgroundImageDark', 'loginBackgroundImage', 'loginBackgroundImageDark',
    'backgroundBlur', 'backgroundOpacity', 'loginLogoImage', 'appLogoImage',
    'customCss', 'customJs', 'sortType'
  ]),
  subscription: new Set([
    'rss', 'rssSleepSeconds', 'rssSleepMinutes', 'rssConcurrency', 'rssTimeout',
    'fileExist', 'skip5', 'omit', 'updateTotalEpisodeNumber', 'forceUpdateTotalEpisodeNumber',
    'autoDisabled', 'completed', 'completedPathTemplate', 'standbyRss', 'coexist',
    'downloadNew', 'titleYear', 'offset', 'offsetType', 'bgmJpName', 'bgmImageSize',
    'customEpisode', 'customEpisodeStr', 'customEpisodeGroupIndex',
    'enabledExclude', 'importExclude', 'enabledGlobalMatch', 'importGlobalMatch',
    'enabledGlobalReverseMatch', 'importGlobalReverseMatch'
  ]),
  naming: new Set([
    'rename', 'renameSleepSeconds', 'renameTemplate', 'maxFileNameLength',
    'renameDelYear', 'renameDelTmdbId', 'subtitleIndependentFolderEnabled',
    'subtitleIndependentFolderName', 'downloadPathTemplate', 'ovaDownloadPathTemplate',
    'collectionDownloadPathTemplate'
  ]),
  download: new Set([
    'downloadToolType', 'downloadToolHost', 'downloadToolPassword', 'qbAuthType',
    'qbUsername', 'qbPassword', 'qbUseDownloadPath', 'qbContentLayout',
    'ratioLimit', 'seedingTimeLimit', 'inactiveSeedingTimeLimit', 'upLimit', 'dlLimit',
    'downloadCount', 'delayedDownload', 'downloadRetry', 'customTags',
    'delete', 'awaitStalledUP', 'deleteStandbyRSSOnly',
    'priorityKeywordsEnable', 'priorityKeywords'
  ]),
  match: new Set(['exclude', 'globalMatch', 'globalReverseMatchRules', 'globalReverseMatch', 'reverseMatchMissBehavior']),
  notification: new Set(['notificationConfigList', 'notificationTemplateList', 'notificationTemplate', 'notificationStatusStyles']),
  metadata: new Set([
    'mikanHost', 'bgmApi', 'bgmTokenType', 'bgmToken', 'bgmAppID', 'bgmAppSecret',
    'bgmRefreshToken', 'bgmRedirectUri', 'tmdb', 'tmdbId', 'tmdbIdPlexMode',
    'tmdbLanguage', 'tmdbRomaji', 'tmdbOriginalName', 'tmdbAnime',
    'tmdbApiKey', 'tmdbApi', 'tmdbImage', 'githubToken'
  ]),
  network: new Set(['proxy', 'proxyHost', 'proxyPort', 'proxyUsername', 'proxyPassword', 'proxyList']),
  security: new Set([
    'login', 'loginEffectiveHours', 'multiLoginForbidden', 'verifyLoginIp', 'limitLoginAttempts',
    'innerIP', 'allowCors', 'ipWhitelist', 'ipWhitelistStr',
    'reverseProxyTrustIpListEnabled', 'reverseProxyTrustIpList', 'apiKey'
  ]),
  system: new Set([
    'configBackup', 'configBackupDay', 'cacheAutoClear', 'cacheAutoClearHours',
    'cacheLastClearTime', 'logsMax', 'debug', 'autoUpdate', 'autoStart'
  ]),
  developer: new Set(['developerOptionsEnabled', 'customTorrentUrl', 'customUserAgent', 'devHttpTrace']),
  about: new Set(['gitInfo', 'version', 'branch', 'shortCommitId', 'commitId', 'uuid'])
}

const classifyKey = key => {
  for (const [tab, keys] of Object.entries(tabKeyMap)) {
    if (keys.has(key)) return tab
  }
  return 'system'
}

const isTabDirty = tabName => changedKeys.value.some(key => classifyKey(key) === tabName)

const loadConfig = (silent = false) => {
  if (!silent) loading.value = true
  return http.config()
      .then(res => {
        config.value = res.data
        baseline.value = stableStringify(normalize(res.data))
        applyUiCustomization(res.data)
      })
      .finally(() => {
        if (!silent) loading.value = false
      })
}

// 与后端 CredentialUtils 保持一致的最短密码长度。
const PASSWORD_MIN_LENGTH = 8

const checkPasswordStrength = password => {
  if (!password) return ''
  if (password.length < PASSWORD_MIN_LENGTH) {
    return t('passwordmustbeatleastncharacters', {n: PASSWORD_MIN_LENGTH})
  }
  let types = 0
  if (/[a-z]/.test(password)) types++
  if (/[A-Z]/.test(password)) types++
  if (/\d/.test(password)) types++
  if (/[^A-Za-z0-9]/.test(password)) types++
  if (types < 2) {
    return t('passwordmustcontainatleasttwoofuppercaselett')
  }
  return ''
}

const validateConfig = value => {
  const username = (value.login?.username || '').trim()
  // 只校验新用户名，历史配置中的用户名不阻止保存其他设置。
  const savedUsername = baseline.value ? (JSON.parse(baseline.value).login?.username || '').trim() : username
  if (username !== savedUsername && !/^[A-Za-z0-9_-]{3,32}$/.test(username)) {
    return t('usernamemustbe332lettersdigitsunderscoresorh')
  }
  const passwordError = checkPasswordStrength((value.login?.password || '').trim())
  if (passwordError) return passwordError
  const interval = Number(value.rssSleepSeconds)
  if (!Number.isFinite(interval) || interval < 1) {
    return t('rssrefreshintervalmustbeatleastonesecond')
  }
  const renameInterval = Number(value.renameSleepSeconds)
  if (!Number.isFinite(renameInterval) || renameInterval < 1) {
    return t('renameintervalmustbeatleastonesecond')
  }
  // 旧配置可能还没有并发字段，缺省时交给服务端补默认值。
  if (value.rssConcurrency !== undefined && value.rssConcurrency !== null) {
    const concurrency = Number(value.rssConcurrency)
    if (!Number.isFinite(concurrency) || concurrency < 1 || concurrency > 16) {
      return t('rssconcurrencymustbebetweenand')
    }
  }
  const timeout = Number(value.rssTimeout)
  if (!Number.isFinite(timeout) || timeout < 1) {
    return t('rsstimeoutmustbeatleastonesecond')
  }
  for (const template of value.notificationTemplateList || []) {
    if (template?.type !== 'JSON') continue
    const body = (template.content || '').trim()
    if (!body) continue
    try {
      JSON.parse(body)
    } catch {
      return t('jsontemplateisnotvalidjson', {name: template.name || t('untitled')})
    }
  }
  return ''
}

const buildPayload = () => {
  const payload = JSON.parse(JSON.stringify(config.value))
  if (payload.login) {
    const username = (payload.login.username || '').trim()
    const password = (payload.login.password || '').trim()
    payload.login.username = username
    // 留空表示不修改密码，服务端会保留原密码摘要。
    payload.login.password = password ? md5(password) : ''
  }
  payload.qbUsername = (payload.qbUsername || '').trim()
  payload.downloadToolHost = (payload.downloadToolHost || '').trim()
  payload.proxyHost = (payload.proxyHost || '').trim()
  return payload
}

const saveConfig = () => {
  if (saving.value || !dirty.value) return
  const error = validateConfig(config.value)
  if (error) {
    ElMessage.error(error)
    return
  }
  saving.value = true
  http.setConfig(buildPayload())
      .then(res => {
        ElMessage.success(res.message)
        window.$reLoadList?.()
        // 以服务端归一化后的结果作为新的基线，密码输入框保持为空。
        return loadConfig(true)
      })
      .finally(() => {
        saving.value = false
      })
}

const discardChanges = () => {
  if (!dirty.value) return
  ElMessageBox.confirm(t('unsavedchangeswillbediscardedandthelastsaved'), t('discardchanges'), {
    confirmButtonText: t('discardchanges-2'),
    cancelButtonText: t('keepediting'),
    type: 'warning'
  })
      .then(() => {
        config.value = JSON.parse(baseline.value)
        applyUiCustomization(config.value)
        ElMessage.info(t('restoredthelastsavedsettings'))
      })
      .catch(() => {})
}

/**
 * 恢复到上次保存的状态，不弹出确认。
 * 页面在 KeepAlive 中会被缓存，离开时必须真正丢弃改动，提示才与实际行为一致。
 */
const restoreBaseline = () => {
  if (!baseline.value) return
  config.value = JSON.parse(baseline.value)
  applyUiCustomization(config.value)
}

onBeforeRouteLeave(async () => {
  if (!dirty.value || window.$skipUnsavedGuard) return true
  try {
    await ElMessageBox.confirm(t('settingsarenotsavedleavingwilldiscardthesech'), t('unsavedchanges'), {
      confirmButtonText: t('discardandleave'),
      cancelButtonText: t('stayonthispage'),
      type: 'warning'
    })
    restoreBaseline()
    return true
  } catch {
    return false
  }
})

useEventListener(window, 'beforeunload', event => {
  if (!dirty.value || window.$skipUnsavedGuard) return
  event.preventDefault()
  event.returnValue = ''
})

useEventListener(window, 'keydown', event => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    saveConfig()
  }
})

onMounted(() => {
  loadConfig()
})

onActivated(() => {
  // 没有未保存修改时，重新读取一次，避免展示被其它页面改动过的旧配置。
  if (!dirty.value && !loading.value) {
    loadConfig(true)
  }
})
</script>

<style scoped>
.config-page {
  padding-bottom: 8px;
}

.config-header-status {
  white-space: nowrap;
}

.config-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  padding-bottom: 10px;
}

.config-search {
  max-width: 320px;
}

.settings-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 196px minmax(0, 1fr);
  gap: 18px;
}

.settings-nav {
  min-height: 0;
  overflow-y: auto;
  padding: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
  backdrop-filter: blur(var(--app-card-blur));
  scrollbar-width: thin;
}

.settings-nav-group + .settings-nav-group {
  margin-top: 14px;
}

.settings-nav-title {
  padding: 4px 10px;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .04em;
}

.settings-nav-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: none;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.35;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--el-transition-duration), color var(--el-transition-duration);
}

.settings-nav-item:hover,
.settings-nav-item:focus-visible {
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
  outline: none;
}

.settings-nav-item.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-weight: 600;
}

.settings-nav-label {
  min-width: 0;
  overflow-wrap: anywhere;
}

.settings-nav-dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-warning);
}

.settings-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: 10px;
  background: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
  backdrop-filter: blur(var(--app-card-blur));
}

.settings-panel-heading {
  flex-shrink: 0;
  padding: 16px 22px 10px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.settings-panel-title {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 650;
  line-height: 1.4;
}

.settings-panel-description {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.settings-scrollbar {
  flex: 1;
  min-height: 0;
}

.settings-panel-content {
  max-width: 1180px;
  padding: 20px 22px 32px;
  animation: settings-content-in .22s ease-out both;
  box-sizing: border-box;
}

.settings-panel-content :deep(.proxy-settings) {
  display: block;
}

@keyframes settings-content-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.config-skeleton {
  display: grid;
  gap: 18px;
  width: 100%;
  max-width: 1180px;
  margin: 24px auto 0;
  padding: 0 28px;
  box-sizing: border-box;
}

.config-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.config-savebar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 24px 10px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
  backdrop-filter: blur(var(--app-card-blur));
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}

.config-savebar.is-dirty {
  border-color: var(--el-color-warning-light-5);
}

.config-savebar-tip {
  margin-left: 8px;
  color: var(--el-text-color-placeholder);
}

.config-savebar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (prefers-reduced-motion: reduce) {
  .settings-panel-content {
    animation: none;
  }
}

@media (max-width: 900px) {
  .settings-layout {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
  }

  .settings-nav {
    display: flex;
    gap: 14px;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 8px;
    scrollbar-width: none;
  }

  .settings-nav::-webkit-scrollbar {
    display: none;
  }

  .settings-nav-group {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .settings-nav-group + .settings-nav-group {
    margin-top: 0;
  }

  .settings-nav-title {
    display: none;
  }

  .settings-nav-item {
    width: auto;
    white-space: nowrap;
  }

  .settings-panel-heading {
    padding: 14px 16px 8px;
  }

  .settings-panel-content {
    padding: 16px 4px 26px;
  }
}

@media (max-width: 700px) {
  .config-toolbar {
    flex-wrap: wrap;
  }

  .config-search {
    max-width: 100%;
    flex: 1 1 100%;
  }

  .config-savebar {
    margin: 0 12px 8px;
    flex-wrap: wrap;
  }

  .config-savebar-text {
    flex: 1 1 100%;
  }
}
</style>

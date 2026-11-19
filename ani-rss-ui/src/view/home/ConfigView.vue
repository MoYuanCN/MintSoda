<template>
  <div v-loading="loading" class="config-page app-page-layout">
    <PageHeaderView :title="$t('settings')" :subtitle="activeDescription">
      <template #actions>
        <el-tag v-if="dirty" class="config-header-status" type="warning" effect="light">
          {{ $t('nunsavedchanges', {n: changedKeys.length}) }}
        </el-tag>
        <el-tag v-else class="config-header-status" type="success" effect="plain">{{ $t('saved') }}</el-tag>
      </template>
    </PageHeaderView>
    <div class="config-content app-page-content app-page-padding">
      <div class="config-toolbar">
        <el-input
            v-model="searchText"
            class="config-search"
            clearable
            :placeholder="$t('searchsettingsegintervalnotificationproxy')"
            prefix-icon="Search"/>
        <el-text v-if="searchText && !filteredTabs.length" size="small" type="warning">
          {{ $t('nomatchingsettingspage') }}
        </el-text>
      </div>
      <el-tabs v-if="filteredTabs.length" v-model="activeName" class="segmented-tabs config-tabs">
        <el-tab-pane
            v-for="tab in filteredTabs"
            :key="tab.name"
            :name="tab.name"
            :lazy="true">
          <template #label>
            <span class="config-tab-label">
              {{ tab.label }}
              <span v-if="isTabDirty(tab.name)" class="config-tab-dot" :title="$t('thispagehasunsavedchanges')"/>
            </span>
          </template>
          <el-scrollbar class="config-scrollbar">
            <div class="tab-scroll-content">
              <DownloadView v-if="tab.name === 'download'" v-model:config="config"/>
              <BasicView v-else-if="tab.name === 'basic'" v-model:config="config"/>
              <ExcludeView
                  v-else-if="tab.name === 'exclude'"
                  v-model:exclude="config.exclude"
                  :show-text="true"/>
              <div v-else-if="tab.name === 'match'" class="global-match-content">
                <el-alert :closable="false" show-icon type="info"
                          :title="$t('everyglobalmatchrulemusthitamatchtakespreced')"/>
                <ExcludeView v-model:exclude="config.globalMatch" :show-text="true"/>
              </div>
              <ReverseMatchSettingsView v-else-if="tab.name === 'reverse-match'" :config="config"/>
              <ProxyView v-else-if="tab.name === 'proxy'" v-model:config="config"/>
              <Security v-else-if="tab.name === 'security'" :config="config"/>
              <NotificationView v-else-if="tab.name === 'notification'" v-model:config="config"/>
              <DeveloperView v-else-if="tab.name === 'developer'" :config="config"/>
              <AfdianView v-else-if="tab.name === 'afdian'" :config="config"/>
              <AboutView v-else-if="tab.name === 'about'" :config="config"/>
            </div>
          </el-scrollbar>
        </el-tab-pane>
      </el-tabs>
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
        <el-button type="primary" :disabled="!dirty" :loading="saving" @click="saveConfig">{{ $t('savesettings') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed, onActivated, onMounted, ref, watch} from "vue";
import {onBeforeRouteLeave} from "vue-router";
import {ElMessage, ElMessageBox} from "element-plus";
import {md5} from "js-md5";
import {useEventListener} from "@vueuse/core";
import ExcludeView from "@/view/config/ExcludeView.vue";
import ReverseMatchSettingsView from "@/view/config/ReverseMatchSettingsView.vue";
import NotificationView from "@/view/config/NotificationView.vue";
import ProxyView from "@/view/config/ProxyView.vue";
import DownloadView from "@/view/config/DownloadView.vue";
import BasicView from "@/view/config/BasicView.vue";
import AboutView from "@/view/config/AboutView.vue";
import Security from "@/view/config/SecurityView.vue";
import AfdianView from "@/view/config/AfdianView.vue";
import DeveloperView from "@/view/config/DeveloperView.vue";
import PageHeaderView from "@/view/custom/PageHeaderView.vue";
import {configData} from "@/js/config.js";
import {applyUiCustomization} from "@/js/global.js";
import * as http from "@/js/http.js";
const {t} = useI18n()
const saving = ref(false)
const loading = ref(true)
const searchText = ref('')
const activeName = ref('download')

const config = ref(configData)
const baseline = ref('')

const tabs = [
  {name: 'download', label: t('downloadsettings'), description: t('downloaderfoldersandtaskbehavior'), keywords: 'qbittorrent 种子 保存 路径 下载 限速 标签 ' + t('qbittorrenttorrentsavepathdownloadlimittags')},
  {name: 'basic', label: t('basicsettings'), description: t('subscriptionsnamingandbackup'), keywords: '外观 主题 重命名 rss 间隔 bangumi token 备份 自启 ' + t('appearancethemerenamerssintervalbangumitoken')},
  {name: 'exclude', label: t('globalexclude'), description: t('excludeunwantedreleasesinoneplace'), keywords: '正则 排除 过滤 ' + t('regexexcludefilter')},
  {name: 'match', label: t('globalmatch'), description: t('requireeveryrsstitletomatchtheserules'), keywords: '正则 匹配 标题 ' + t('regexmatchtitle')},
  {name: 'reverse-match', label: t('reversematch'), description: t('manageglobalandpersubscriptionreversematchru'), keywords: '反匹配 字幕组 测试 正则 ' + t('reversematchsubgrouptestregex')},
  {name: 'proxy', label: t('proxy'), description: t('networkproxyandconnectionsettings'), keywords: '代理 网络 http socks 测试 ' + t('proxynetworkhttpsockstest')},
  {name: 'security', label: t('security'), description: t('accountandaccesssecurity'), keywords: '密码 账号 api key 白名单 跨域 登录 ' + t('passwordaccountapikeyallowlistcorslogin')},
  {name: 'notification', label: t('notifications'), description: t('channelsandeventnotifications'), keywords: 'telegram 通知 webhook json 模板 emby ' + t('telegramnotificationwebhookjsontemplateemby')},
  {name: 'developer', label: t('developer'), description: t('developeroptionsrequestdiagnosticsandcustomt'), keywords: '开发者 种子 info_hash 调试 诊断 ua 请求日志 ' + t('developertorrentinfohashdebugdiagnosticsuare')},
  {name: 'afdian', label: t('donate'), description: t('supportongoingmaintenance'), keywords: '捐赠 爱发电 ' + t('donateafdian')},
  {name: 'about', label: t('about'), description: t('versionandprojectlinks'), keywords: '版本 更新 开发者 关于 日志 ' + t('versionupdatedeveloperaboutlogs')}
]

const activeDescription = computed(() => tabs.find(tab => tab.name === activeName.value)?.description || '')

const filteredTabs = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return tabs
  return tabs.filter(tab =>
      `${tab.label} ${tab.description} ${tab.keywords || ''}`.toLowerCase().includes(keyword))
})

watch(filteredTabs, list => {
  if (!list.length) return
  if (!list.some(tab => tab.name === activeName.value)) {
    activeName.value = list[0].name
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
  download: new Set([
    'downloadToolType', 'downloadToolHost', 'downloadToolPassword', 'downloadRetry', 'qbAuthType',
    'qbUsername', 'qbPassword', 'qbUseDownloadPath', 'qbContentLayout', 'ratioLimit', 'seedingTimeLimit',
    'inactiveSeedingTimeLimit', 'downloadPathTemplate', 'ovaDownloadPathTemplate',
    'collectionDownloadPathTemplate', 'customTags',
    'priorityKeywordsEnable', 'priorityKeywords', 'delayedDownload', 'delete', 'awaitStalledUP',
    'deleteStandbyRSSOnly', 'downloadCount', 'upLimit', 'dlLimit'
  ]),
  exclude: new Set(['exclude']),
  match: new Set(['globalMatch']),
  'reverse-match': new Set(['globalReverseMatchRules', 'globalReverseMatch']),
  proxy: new Set(['proxy', 'proxyHost', 'proxyPort', 'proxyUsername', 'proxyPassword', 'proxyList']),
  security: new Set([
    'login', 'loginEffectiveHours', 'multiLoginForbidden', 'innerIP', 'verifyLoginIp',
    'limitLoginAttempts', 'allowCors', 'ipWhitelist', 'ipWhitelistStr',
    'reverseProxyTrustIpListEnabled', 'reverseProxyTrustIpList', 'apiKey'
  ]),
  notification: new Set(['notificationConfigList', 'notificationTemplateList', 'notificationTemplate']),
  developer: new Set(['developerOptionsEnabled', 'customTorrentUrl', 'customUserAgent', 'devHttpTrace']),
  about: new Set(['gitInfo', 'version'])
}

const classifyKey = key => {
  for (const [tab, keys] of Object.entries(tabKeyMap)) {
    if (keys.has(key)) return tab
  }
  return 'basic'
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
      .catch(() => {
      })
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
  padding-bottom: 8px;
}

.config-search {
  max-width: 320px;
}

.config-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.config-tabs :deep(.el-tabs__header) {
  flex-shrink: 0;
}

.config-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
  backdrop-filter: blur(var(--app-card-blur));
}

.config-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.config-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.config-tab-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-warning);
}

.config-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.config-scrollbar {
  height: 100%;
}

.tab-scroll-content {
  width: min(100%, 920px);
  min-width: 0;
  margin: 0 auto;
  padding: 24px 28px 40px;
}

.config-tabs :deep(.el-alert) {
  border-radius: 6px;
}

.global-match-content {
  display: grid;
  gap: 16px;
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

@media (max-width: 700px) {
  .tab-scroll-content {
    padding: 18px 4px 30px;
  }

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

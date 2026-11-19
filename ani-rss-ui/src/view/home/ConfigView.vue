<template>
  <div v-loading="loading" class="config-page app-page-layout">
    <PageHeaderView title="设置" :subtitle="activeDescription">
      <template #actions>
        <el-tag v-if="dirty" class="config-header-status" type="warning" effect="light">
          {{ changedKeys.length }} 处未保存
        </el-tag>
        <el-tag v-else class="config-header-status" type="success" effect="plain">已保存</el-tag>
      </template>
    </PageHeaderView>
    <div class="config-content app-page-content app-page-padding">
      <div class="config-toolbar">
        <el-input
            v-model="searchText"
            class="config-search"
            clearable
            placeholder="搜索设置页，例如 间隔、通知、代理"
            prefix-icon="Search"/>
        <el-text v-if="searchText && !filteredTabs.length" size="small" type="warning">
          没有匹配的设置页
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
              <span v-if="isTabDirty(tab.name)" class="config-tab-dot" title="该页有未保存的修改"/>
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
                          title="全局匹配规则要求每条规则都命中；匹配成功时优先于排除规则。反匹配命中优先级最高。"/>
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
        <el-empty description="没有匹配的设置页"/>
      </div>
    </div>
    <div class="config-savebar" :class="{'is-dirty': dirty}">
      <el-text class="config-savebar-text" size="small">
        {{ dirty ? `有 ${changedKeys.length} 处修改未保存` : '所有修改已保存' }}
        <span class="config-savebar-tip">Ctrl + S 可保存</span>
      </el-text>
      <div class="config-savebar-actions">
        <el-button bg text :disabled="!dirty || saving" @click="discardChanges">撤销修改</el-button>
        <el-button type="primary" :disabled="!dirty" :loading="saving" @click="saveConfig">保存设置</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
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

const saving = ref(false)
const loading = ref(true)
const searchText = ref('')
const activeName = ref('download')

const config = ref(configData)
const baseline = ref('')

const tabs = [
  {name: 'download', label: '下载设置', description: '下载器、目录与任务行为', keywords: 'qbittorrent 种子 保存 路径 下载 限速 标签'},
  {name: 'basic', label: '基本设置', description: '订阅、命名与备份', keywords: '外观 主题 重命名 rss 间隔 bangumi token 备份 自启'},
  {name: 'exclude', label: '全局排除', description: '统一排除不需要的资源', keywords: '正则 排除 过滤'},
  {name: 'match', label: '全局匹配', description: '统一限定 RSS 标题必须符合的条件', keywords: '正则 匹配 标题'},
  {name: 'reverse-match', label: '反匹配', description: '管理全局与订阅反匹配规则并测试标题', keywords: '反匹配 字幕组 测试 正则'},
  {name: 'proxy', label: '代理', description: '网络代理与连接配置', keywords: '代理 网络 http socks 测试'},
  {name: 'security', label: '安全', description: '账号与访问安全', keywords: '密码 账号 api key 白名单 跨域 登录'},
  {name: 'notification', label: '通知', description: '消息渠道与事件通知', keywords: 'telegram 通知 webhook json 模板 emby'},
  {name: 'developer', label: '开发者', description: '开发者选项、请求诊断与自定义种子地址', keywords: '开发者 种子 info_hash 调试 诊断 ua 请求日志'},
  {name: 'afdian', label: '捐赠', description: '支持项目持续维护', keywords: '捐赠 爱发电'},
  {name: 'about', label: '关于', description: '版本信息与项目链接', keywords: '版本 更新 开发者 关于 日志'}
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
    return `密码至少需要 ${PASSWORD_MIN_LENGTH} 位`
  }
  let types = 0
  if (/[a-z]/.test(password)) types++
  if (/[A-Z]/.test(password)) types++
  if (/\d/.test(password)) types++
  if (/[^A-Za-z0-9]/.test(password)) types++
  if (types < 2) {
    return '密码需包含大写字母、小写字母、数字、符号中的至少两类'
  }
  return ''
}

const validateConfig = value => {
  const username = (value.login?.username || '').trim()
  // 只校验新用户名，历史配置中的用户名不阻止保存其他设置。
  const savedUsername = baseline.value ? (JSON.parse(baseline.value).login?.username || '').trim() : username
  if (username !== savedUsername && !/^[A-Za-z0-9_-]{3,32}$/.test(username)) {
    return '用户名需为 3-32 位字母、数字、下划线或连字符'
  }
  const passwordError = checkPasswordStrength((value.login?.password || '').trim())
  if (passwordError) return passwordError
  const interval = Number(value.rssSleepSeconds)
  if (!Number.isFinite(interval) || interval < 1) {
    return 'RSS 刷新间隔必须至少为 1 秒'
  }
  const renameInterval = Number(value.renameSleepSeconds)
  if (!Number.isFinite(renameInterval) || renameInterval < 1) {
    return '重命名间隔必须至少为 1 秒'
  }
  // 旧配置可能还没有并发字段，缺省时交给服务端补默认值。
  if (value.rssConcurrency !== undefined && value.rssConcurrency !== null) {
    const concurrency = Number(value.rssConcurrency)
    if (!Number.isFinite(concurrency) || concurrency < 1 || concurrency > 16) {
      return 'RSS 并发数需在 1 到 16 之间'
    }
  }
  const timeout = Number(value.rssTimeout)
  if (!Number.isFinite(timeout) || timeout < 1) {
    return 'RSS 超时时间必须至少为 1 秒'
  }
  for (const template of value.notificationTemplateList || []) {
    if (template?.type !== 'JSON') continue
    const body = (template.content || '').trim()
    if (!body) continue
    try {
      JSON.parse(body)
    } catch {
      return `JSON 模板「${template.name || '未命名'}」不是有效 JSON，请先修正`
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
  ElMessageBox.confirm('将放弃所有未保存的修改，恢复为上次保存的设置。', '撤销修改', {
    confirmButtonText: '放弃修改',
    cancelButtonText: '继续编辑',
    type: 'warning'
  })
      .then(() => {
        config.value = JSON.parse(baseline.value)
        applyUiCustomization(config.value)
        ElMessage.info('已恢复为上次保存的设置')
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
    await ElMessageBox.confirm('设置尚未保存，离开将丢失这些修改。', '未保存的修改', {
      confirmButtonText: '放弃并离开',
      cancelButtonText: '留在此页',
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

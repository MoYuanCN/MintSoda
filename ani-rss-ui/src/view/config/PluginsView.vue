<template>
  <div class="plugins-page app-page-layout">
    <PageHeaderView :title="$t('plugins')" :subtitle="$t('extendnotificationsfilteringandautomationins')">
      <template #actions>
        <div class="plugin-header-actions">
          <el-button bg :loading="checking" icon="Refresh" @click="checkAll">{{ $t('checkforupdates') }}</el-button>
          <UploadView url="api/plugin/upload" :extensions="['jar', 'zip']" :callback="uploadDone">
            <el-button type="primary" icon="Upload">{{ $t('installplugin') }}</el-button>
          </UploadView>
        </div>
      </template>
    </PageHeaderView>

    <div class="plugin-body app-page-content app-page-padding">
      <el-scrollbar class="plugin-scrollbar">
        <div class="plugin-content">
          <el-card shadow="never" class="plugin-card">
            <div class="plugin-market-row">
              <div class="plugin-market-input">
                <el-text size="small">{{ $t('marketplaceurl') }}</el-text>
                <el-input v-model="marketUrl" clearable placeholder="https://example.com/plugins/index.json"/>
              </div>
              <el-button bg :loading="savingMarket" @click="saveMarket">{{ $t('save') }}</el-button>
              <el-button bg :loading="loadingMarket" @click="loadMarket">{{ $t('openmarketplace') }}</el-button>
            </div>
            <el-text size="small" type="info">
              {{ $t('themarketplaceisajsonindexinstallsandupdates') }}
            </el-text>
          </el-card>

          <el-card v-if="stats" shadow="never" class="plugin-card">
            <el-text size="small" type="info">
              {{ $t('pluginruntimeoverview', {
                total: stats.total,
                enabled: stats.enabled,
                error: stats.error,
                points: stats.filters + stats.listeners + stats.downloadInterceptors
                    + stats.downloadListeners + stats.channels + stats.variableProviders
                    + stats.endpoints + stats.transformers + stats.pages
              }) }}
            </el-text>
          </el-card>

          <el-tabs v-model="activeTab" class="segmented-tabs plugin-tabs">
            <el-tab-pane :label="$t('installed')" name="installed">
              <div v-loading="loading" class="plugin-list">
                <el-empty v-if="!plugins.length && !loading" :description="$t('nopluginsinstalledyet')">
                  <el-text size="small" type="info">{{ $t('uploadapluginpackageorinstallfromthemarketpl') }}</el-text>
                </el-empty>
                <el-card v-for="plugin in plugins" :key="plugin.id" shadow="never" class="plugin-item">
                  <div class="plugin-item-heading">
                    <div class="plugin-item-title">
                      <span class="plugin-item-name">{{ plugin.name || plugin.id }}</span>
                      <el-tag size="small" :type="stateTag(plugin.state).type" effect="light">
                        {{ stateTag(plugin.state).label }}
                      </el-tag>
                      <el-tag v-if="plugin.version" size="small" effect="plain">{{ plugin.version }}</el-tag>
                      <el-tag v-if="plugin.updateAvailable" size="small" type="success" effect="light">
                        {{ $t('updatetoversion', {version: plugin.latestVersion}) }}
                      </el-tag>
                    </div>
                    <div class="plugin-item-actions">
                      <el-button v-if="plugin.state === 'ENABLED'" bg text @click="toggle(plugin, false)">
                        {{ $t('disable-2') }}
                      </el-button>
                      <el-button v-else bg text type="primary" @click="toggle(plugin, true)">{{ $t('enable-3') }}</el-button>
                      <el-button bg text icon="Refresh" :title="$t('checkforupdates')" @click="checkOne(plugin)"/>
                      <el-button bg text icon="RefreshRight" :title="$t('reload')" @click="reloadPlugin(plugin)"/>
                      <el-button v-if="plugin.updateAvailable" bg text type="success"
                                 :loading="updating === plugin.id" @click="update(plugin)">
                        {{ $t('update') }}
                      </el-button>
                      <PopconfirmView :title="$t('uninstallthisplugin')" @confirm="uninstall(plugin)">
                        <el-button bg text type="danger" icon="Delete"/>
                      </PopconfirmView>
                    </div>
                  </div>
                  <el-text v-if="plugin.description" size="small" class="plugin-item-desc">
                    {{ plugin.description }}
                  </el-text>
                  <el-text v-if="plugin.error" size="small" type="danger">{{ plugin.error }}</el-text>
                  <div class="plugin-item-meta">
                    <el-text v-if="plugin.author" size="small" type="info">{{ $t('author') }}{{ plugin.author }}</el-text>
                    <el-text v-if="plugin.apiVersion" size="small" type="info">
                      {{ $t('apiversion') }}{{ plugin.apiVersion }}
                    </el-text>
                    <el-text v-if="plugin.runtime" size="small" type="info">
                      {{ $t('runtime') }}{{ runtimeLabel(plugin.runtime) }}
                    </el-text>
                    <el-text v-if="plugin.channels?.length" size="small" type="info">
                      {{ $t('channels') }}{{ plugin.channels.map(it => it.name || it.id).join(', ') }}
                    </el-text>
                    <el-text v-if="plugin.endpoints?.length" size="small" type="info">
                      {{ $t('endpoints') }}{{ plugin.endpoints.map(it => `/${it.path}`).join(', ') }}
                    </el-text>
                    <el-text v-if="plugin.pages?.length" size="small" type="info">
                      {{ $t('pluginpages') }}: {{ plugin.pages.map(it => it.title || it.id).join(', ') }}
                    </el-text>
                  </div>
                  <div v-if="plugin.pages?.length" class="plugin-pages">
                    <el-button v-for="page in plugin.pages" :key="page.id" bg text type="primary"
                               @click="openPage(plugin, page)">
                      {{ page.title || page.id }}
                    </el-button>
                  </div>

                  <el-collapse class="plugin-sections">
                    <el-collapse-item :title="$t('permissions')" name="permissions">
                      <el-text size="small" type="info">
                        {{ $t('disablingapermissionstopsthepluginfromregist') }}
                      </el-text>
                      <el-checkbox-group :model-value="grantedOf(plugin)" class="plugin-permission-list"
                                         @update:model-value="value => draftPermissions[plugin.id] = value">
                        <div v-for="group in permissionGroups" :key="group.name" class="plugin-permission-group">
                          <div class="plugin-permission-group-title">{{ group.label }}</div>
                          <el-checkbox v-for="permission in group.items" :key="permission.id"
                                       :value="permission.id" :label="permission.id">
                            <span class="plugin-permission-label">{{ permission.label }}</span>
                            <el-text size="small" type="info">{{ permission.description }}</el-text>
                            <el-tag v-if="permission.risk === 'HIGH'" size="small" type="danger" effect="plain">
                              {{ $t('highrisk') }}
                            </el-tag>
                            <el-tag v-if="!permission.enforced" size="small" type="warning" effect="plain">
                              {{ $t('advisoryonly') }}
                            </el-tag>
                          </el-checkbox>
                        </div>
                      </el-checkbox-group>
                      <div class="plugin-section-actions">
                        <el-button bg text type="primary" :loading="savingPermissions === plugin.id"
                                   @click="savePermissions(plugin)">
                          {{ $t('savepermissions') }}
                        </el-button>
                      </div>
                    </el-collapse-item>
                  </el-collapse>

                  <el-collapse v-if="plugin.sections?.length" class="plugin-sections">
                    <el-collapse-item v-for="section in plugin.sections" :key="section.id"
                                      :title="section.title || section.id" :name="section.id">
                      <el-form label-position="top" class="plugin-section-form">
                        <el-form-item v-for="field in section.fields" :key="field.key"
                                      :label="field.label || field.key">
                          <PluginFieldInput :field="field" :model="sectionValues(plugin, section)"/>
                        </el-form-item>
                      </el-form>
                      <div class="plugin-section-actions">
                        <el-button bg text type="primary" @click="saveSection(plugin)">
                          {{ $t('savepluginsettings') }}
                        </el-button>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </el-card>
              </div>
            </el-tab-pane>

            <el-tab-pane :label="$t('pluginmarketplace')" name="market">
              <div v-loading="loadingMarket" class="plugin-list">
                <el-empty v-if="!marketPlugins.length && !loadingMarket"
                          :description="marketLoaded ? $t('nopluginsinthemarketplace') : $t('enteramarketplaceurlandclickopenmarketplace')"/>
                <el-card v-for="entry in marketPlugins" :key="entry.id" shadow="never" class="plugin-item">
                  <div class="plugin-item-heading">
                    <div class="plugin-item-title">
                      <span class="plugin-item-name">{{ entry.name || entry.id }}</span>
                      <el-tag size="small" effect="plain">{{ entry.version }}</el-tag>
                      <el-tag v-if="installedVersion(entry.id)" size="small" type="info" effect="light">
                        {{ $t('installedversion', {version: installedVersion(entry.id)}) }}
                      </el-tag>
                    </div>
                    <el-button bg text type="primary" :loading="installing === entry.id"
                               @click="installFromMarket(entry)">
                      {{ installedVersion(entry.id) ? $t('reinstall') : $t('install') }}
                    </el-button>
                  </div>
                  <el-text v-if="entry.description" size="small" class="plugin-item-desc">
                    {{ entry.description }}
                  </el-text>
                  <div class="plugin-item-meta">
                    <el-text v-if="entry.author" size="small" type="info">{{ $t('author') }}{{ entry.author }}</el-text>
                    <el-text v-if="entry.permissions?.length" size="small" type="info">
                      {{ $t('requestedpermissions') }}{{ permissionLabels(entry.permissions) }}
                    </el-text>
                  </div>
                </el-card>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-scrollbar>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed, defineComponent, h, onActivated, onMounted, ref, reactive} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage, ElMessageBox, ElInput, ElInputNumber, ElSelect, ElOption, ElSwitch} from 'element-plus'
import PageHeaderView from '@/view/custom/PageHeaderView.vue'
import UploadView from '@/view/custom/UploadView.vue'
import PopconfirmView from '@/view/custom/PopconfirmView.vue'
import * as http from '@/js/plugin.js'
const {t} = useI18n()
const router = useRouter()
// 插件字段只支持有限类型，按 type 渲染，避免插件注入任意 HTML。
const PluginFieldInput = defineComponent({
  props: {field: {type: Object, required: true}, model: {type: Object, required: true}},
  setup(props) {
    return () => {
      const key = props.field.key
      const type = props.field.type
      if (type === 'SWITCH') {
        return h(ElSwitch, {
          modelValue: props.model[key] === 'true',
          'onUpdate:modelValue': value => props.model[key] = value ? 'true' : 'false'
        })
      }
      if (type === 'NUMBER') {
        return h(ElInputNumber, {
          modelValue: Number(props.model[key] || 0),
          'onUpdate:modelValue': value => props.model[key] = String(value)
        })
      }
      if (type === 'SELECT') {
        return h(ElSelect, {
          modelValue: props.model[key],
          'onUpdate:modelValue': value => props.model[key] = value
        }, () => (props.field.options || []).map(option => h(ElOption, {label: option, value: option})))
      }
      return h(ElInput, {
        modelValue: props.model[key] || '',
        type: type === 'PASSWORD' ? 'password' : 'text',
        showPassword: type === 'PASSWORD',
        placeholder: props.field.placeholder || '',
        'onUpdate:modelValue': value => props.model[key] = value
      })
    }
  }
})

const loading = ref(false)
const checking = ref(false)
const savingMarket = ref(false)
const loadingMarket = ref(false)
const marketLoaded = ref(false)
const installing = ref('')
const updating = ref('')
const activeTab = ref('installed')
const plugins = ref([])
const stats = ref(null)
const permissions = ref([])
const draftPermissions = reactive({})
const savingPermissions = ref('')
const marketPlugins = ref([])
const marketUrl = ref('')
const sectionModels = reactive({})

const openPage = (plugin, page) => {
  if (plugin.state !== 'ENABLED') {
    ElMessage.warning(t('pluginpagenotfound'))
    return
  }
  router.push({path: `/plugins/${plugin.id}/${page.id}`})
}

const runtimeLabel = runtime => ({
  JAVASCRIPT: 'JavaScript',
  LUA: 'Lua',
  JAVA: 'Java'
}[runtime] || runtime || 'Java')

const permissionGroups = computed(() => {
  const groups = []
  const index = new Map()
  permissions.value.forEach(permission => {
    const name = permission.group || 'OTHER'
    if (!index.has(name)) {
      index.set(name, {name, label: permission.groupLabel || name, items: []})
      groups.push(index.get(name))
    }
    index.get(name).items.push(permission)
  })
  return groups
})

const permissionLabels = ids => (ids || [])
    .map(id => permissions.value.find(it => it.id === normalizePermission(id))?.label || id)
    .join('、')

const normalizePermission = id => String(id || '').trim().replaceAll('-', '_').toUpperCase()

const normalizeGranted = ids => (ids || [])
    .map(id => normalizePermission(id))
    .flatMap(id => id === 'CONFIG' ? ['CONFIG_READ', 'CONFIG_WRITE']
        : id === 'DATA_DIR' || id === 'DATA' ? ['DATA_READ', 'DATA_WRITE'] : [id])

const grantedOf = plugin => draftPermissions[plugin.id]
    || normalizeGranted(plugin.grantedPermissions)
    || normalizeGranted(plugin.permissions)

const savePermissions = plugin => {
  savingPermissions.value = plugin.id
  http.setPluginPermissions(plugin.id, grantedOf(plugin))
      .then(res => {
        ElMessage.success(res.message)
        delete draftPermissions[plugin.id]
        loadPlugins(true)
      })
      .finally(() => {
        savingPermissions.value = ''
      })
}

const stateTag = state => ({
  ENABLED: {type: 'success', label: t('enabled')},
  DISABLED: {type: 'info', label: t('disabled')},
  ERROR: {type: 'danger', label: t('error-2')},
  INCOMPATIBLE: {type: 'warning', label: t('incompatible')}
}[state] || {type: 'info', label: t('unknown')})

const installedVersion = id => plugins.value.find(plugin => plugin.id === id)?.version || ''

const loadPlugins = (silent = false) => {
  if (!silent) loading.value = true
  http.pluginStats()
      .then(res => { stats.value = res.data || null })
      .catch(() => { stats.value = null })
  return http.listPlugins()
      .then(res => {
        plugins.value = res.data || []
        // 同步配置模型，保证插件声明的字段有初始值。
        plugins.value.forEach(plugin => {
          let config = {}
          try {
            config = plugin.config ? JSON.parse(plugin.config) : {}
          } catch {
            config = {}
          }
          ;(plugin.sections || []).forEach(section => {
            const modelKey = `${plugin.id}:${section.id}`
            if (!sectionModels[modelKey]) {
              sectionModels[modelKey] = {}
            }
            ;(section.fields || []).forEach(field => {
              if (sectionModels[modelKey][field.key] === undefined) {
                sectionModels[modelKey][field.key] = config[field.key] ?? ''
              }
            })
          })
        })
      })
      .finally(() => {
        if (!silent) loading.value = false
      })
}

const sectionValues = (plugin, section) => {
  const key = `${plugin.id}:${section.id}`
  if (!sectionModels[key]) {
    sectionModels[key] = {}
  }
  return sectionModels[key]
}

const saveSection = plugin => {
  const merged = {}
  ;(plugin.sections || []).forEach(section => {
    Object.assign(merged, sectionValues(plugin, section))
  })
  http.savePluginConfig(plugin.id, JSON.stringify(merged))
      .then(res => {
        ElMessage.success(res.message)
        loadPlugins(true)
      })
}

const toggle = (plugin, enable) => {
  const action = enable ? http.enablePlugin(plugin.id) : http.disablePlugin(plugin.id)
  action.then(res => {
    ElMessage.success(res.message)
    loadPlugins(true)
  })
}

const reloadPlugin = plugin => http.reloadPlugin(plugin.id)
    .then(res => {
      ElMessage.success(res.message)
      loadPlugins(true)
    })

const uninstall = plugin => http.uninstallPlugin(plugin.id)
    .then(res => {
      ElMessage.success(res.message)
      loadPlugins(true)
    })

const uploadDone = response => {
  if (response.code !== 200) {
    ElMessage.error(response.message || t('plugininstallationfailed'))
    return
  }
  ElMessage.success(response.message || t('plugininstalled'))
  loadPlugins(true)
}

const checkOne = plugin => {
  checking.value = true
  http.checkPluginUpdate(plugin.id, marketUrl.value)
      .then(res => {
        const info = res.data
        ElMessage.success(info?.updateAvailable ? t('newversionavailable', {version: info.latestVersion}) : t('alreadyuptodate'))
        loadPlugins(true)
      })
      .finally(() => {
        checking.value = false
      })
}

const checkAll = () => {
  checking.value = true
  http.checkAllPluginUpdates(marketUrl.value)
      .then(() => {
        ElMessage.success(t('checkcomplete'))
        loadPlugins(true)
      })
      .finally(() => {
        checking.value = false
      })
}

const update = plugin => {
  updating.value = plugin.id
  http.updatePlugin(plugin.id, marketUrl.value)
      .then(res => {
        ElMessage.success(t('updatedto', {version: res.data?.version || t('latestversion')}))
        loadPlugins(true)
      })
      .finally(() => {
        updating.value = ''
      })
}

const loadMarket = () => {
  loadingMarket.value = true
  marketLoaded.value = false
  http.getPluginMarket(marketUrl.value)
      .then(res => {
        marketPlugins.value = res.data?.plugins || []
        marketLoaded.value = true
      })
      .finally(() => {
        loadingMarket.value = false
      })
}

const installFromMarket = entry => {
  const declared = (entry.permissions || []).join('、') || t('none')
  ElMessageBox.confirm(t('installpluginconfirm', {name: entry.name || entry.id, permissions: declared}),
      t('installplugin'), {type: 'warning', confirmButtonText: t('install'), cancelButtonText: t('cancel')})
      .then(() => {
        installing.value = entry.id
        http.installPluginFromMarket(entry)
            .then(res => {
              ElMessage.success(t('plugininstalled', {name: res.data?.name || entry.id}))
              loadPlugins(true)
            })
            .finally(() => {
              installing.value = ''
            })
      })
      .catch(() => {
      })
}

const saveMarket = () => {
  savingMarket.value = true
  http.setPluginMarketUrl(marketUrl.value)
      .then(res => {
        ElMessage.success(res.message)
      })
      .finally(() => {
        savingMarket.value = false
      })
}

onMounted(() => {
  http.getPluginPermissions()
      .then(res => {
        permissions.value = res.data || []
      })
      .catch(() => {
      })
  http.getPluginMarketUrl()
      .then(res => {
        marketUrl.value = res.data || ''
      })
      .catch(() => {
      })
  loadPlugins()
})

onActivated(() => loadPlugins(true))
</script>

<style scoped>
.plugins-page {
  padding-bottom: 8px;
}

.plugin-permission-list {
  display: grid;
  gap: 6px;
  margin: 10px 0;
}

.plugin-permission-list :deep(.el-checkbox) {
  height: auto;
  align-items: flex-start;
  margin-right: 0;
}

.plugin-permission-label {
  margin-right: 6px;
}

.plugin-permission-group {
  margin-bottom: 10px;
}

.plugin-permission-group-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.plugin-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.plugin-body {
  padding-top: 8px;
}

.plugin-scrollbar {
  height: 100%;
}

.plugin-content {
  width: min(100%, 1100px);
  margin: 0 auto;
  padding: 0 4px 40px;
  display: grid;
  gap: 12px;
}

.plugin-market-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.plugin-market-input {
  flex: 1;
  min-width: 240px;
  display: grid;
  gap: 4px;
}

.plugin-tabs {
  display: flex;
  flex-direction: column;
}

.plugin-list {
  display: grid;
  gap: 12px;
}

.plugin-item-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.plugin-item-title {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-width: 0;
}

.plugin-item-name {
  font-weight: 600;
  font-size: 15px;
  overflow-wrap: anywhere;
}

.plugin-item-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}

.plugin-item-desc {
  display: block;
  margin-top: 6px;
}

.plugin-item-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.plugin-pages {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.plugin-sections {
  margin-top: 8px;
}

.plugin-section-form {
  display: grid;
  gap: 4px;
}

.plugin-section-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 700px) {
  .plugin-content {
    padding: 0 0 30px;
  }

  .plugin-item-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>

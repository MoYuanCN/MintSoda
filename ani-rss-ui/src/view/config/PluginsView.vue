<template>
  <div class="plugins-page app-page-layout">
    <PageHeaderView title="插件" subtitle="扩展通知渠道、过滤与自动化，并可在线安装与更新">
      <template #actions>
        <div class="plugin-header-actions">
          <el-button bg :loading="checking" icon="Refresh" @click="checkAll">检查更新</el-button>
          <UploadView url="api/plugin/upload" :extensions="['jar', 'zip']" :callback="uploadDone">
            <el-button type="primary" icon="Upload">安装插件</el-button>
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
                <el-text size="small">插件市场地址</el-text>
                <el-input v-model="marketUrl" clearable placeholder="https://example.com/plugins/index.json"/>
              </div>
              <el-button bg :loading="savingMarket" @click="saveMarket">保存</el-button>
              <el-button bg :loading="loadingMarket" @click="loadMarket">打开市场</el-button>
            </div>
            <el-text size="small" type="info">
              市场是一个 JSON 索引；安装与更新都会校验 sha256 与文件大小，地址需为公网 http/https。
            </el-text>
          </el-card>

          <el-tabs v-model="activeTab" class="segmented-tabs plugin-tabs">
            <el-tab-pane label="已安装" name="installed">
              <div v-loading="loading" class="plugin-list">
                <el-empty v-if="!plugins.length && !loading" description="还没有安装插件">
                  <el-text size="small" type="info">可以上传插件包，或从市场安装。</el-text>
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
                        可更新 {{ plugin.latestVersion }}
                      </el-tag>
                    </div>
                    <div class="plugin-item-actions">
                      <el-button v-if="plugin.state === 'ENABLED'" bg text @click="toggle(plugin, false)">
                        停用
                      </el-button>
                      <el-button v-else bg text type="primary" @click="toggle(plugin, true)">启用</el-button>
                      <el-button bg text icon="Refresh" title="检查更新" @click="checkOne(plugin)"/>
                      <el-button bg text icon="RefreshRight" title="重新加载" @click="reloadPlugin(plugin)"/>
                      <el-button v-if="plugin.updateAvailable" bg text type="success"
                                 :loading="updating === plugin.id" @click="update(plugin)">
                        更新
                      </el-button>
                      <PopconfirmView title="确认卸载该插件？" @confirm="uninstall(plugin)">
                        <el-button bg text type="danger" icon="Delete"/>
                      </PopconfirmView>
                    </div>
                  </div>
                  <el-text v-if="plugin.description" size="small" class="plugin-item-desc">
                    {{ plugin.description }}
                  </el-text>
                  <el-text v-if="plugin.error" size="small" type="danger">{{ plugin.error }}</el-text>
                  <div class="plugin-item-meta">
                    <el-text v-if="plugin.author" size="small" type="info">作者：{{ plugin.author }}</el-text>
                    <el-text v-if="plugin.apiVersion" size="small" type="info">
                      接口版本：{{ plugin.apiVersion }}
                    </el-text>
                    <el-text v-if="plugin.runtime" size="small" type="info">
                      运行时：{{ plugin.runtime === 'JAVASCRIPT' ? 'JavaScript' : 'Java' }}
                    </el-text>
                    <el-text v-if="plugin.channels?.length" size="small" type="info">
                      通知渠道：{{ plugin.channels.map(it => it.name || it.id).join('、') }}
                    </el-text>
                    <el-text v-if="plugin.endpoints?.length" size="small" type="info">
                      接口：{{ plugin.endpoints.map(it => `/${it.path}`).join('、') }}
                    </el-text>
                  </div>

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
                          保存插件配置
                        </el-button>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </el-card>
              </div>
            </el-tab-pane>

            <el-tab-pane label="插件市场" name="market">
              <div v-loading="loadingMarket" class="plugin-list">
                <el-empty v-if="!marketPlugins.length && !loadingMarket"
                          :description="marketLoaded ? '市场暂无插件' : '填写市场地址后点击“打开市场”'"/>
                <el-card v-for="entry in marketPlugins" :key="entry.id" shadow="never" class="plugin-item">
                  <div class="plugin-item-heading">
                    <div class="plugin-item-title">
                      <span class="plugin-item-name">{{ entry.name || entry.id }}</span>
                      <el-tag size="small" effect="plain">{{ entry.version }}</el-tag>
                      <el-tag v-if="installedVersion(entry.id)" size="small" type="info" effect="light">
                        已安装 {{ installedVersion(entry.id) }}
                      </el-tag>
                    </div>
                    <el-button bg text type="primary" :loading="installing === entry.id"
                               @click="installFromMarket(entry)">
                      {{ installedVersion(entry.id) ? '重新安装' : '安装' }}
                    </el-button>
                  </div>
                  <el-text v-if="entry.description" size="small" class="plugin-item-desc">
                    {{ entry.description }}
                  </el-text>
                  <el-text v-if="entry.author" size="small" type="info">作者：{{ entry.author }}</el-text>
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
import {defineComponent, h, onActivated, onMounted, ref, reactive} from 'vue'
import {ElMessage, ElMessageBox, ElInput, ElInputNumber, ElSelect, ElOption, ElSwitch} from 'element-plus'
import PageHeaderView from '@/view/custom/PageHeaderView.vue'
import UploadView from '@/view/custom/UploadView.vue'
import PopconfirmView from '@/view/custom/PopconfirmView.vue'
import * as http from '@/js/plugin.js'

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
const marketPlugins = ref([])
const marketUrl = ref('')
const sectionModels = reactive({})

const stateTag = state => ({
  ENABLED: {type: 'success', label: '已启用'},
  DISABLED: {type: 'info', label: '已停用'},
  ERROR: {type: 'danger', label: '异常'},
  INCOMPATIBLE: {type: 'warning', label: '不兼容'}
}[state] || {type: 'info', label: '未知'})

const installedVersion = id => plugins.value.find(plugin => plugin.id === id)?.version || ''

const loadPlugins = (silent = false) => {
  if (!silent) loading.value = true
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
    ElMessage.error(response.message || '插件安装失败')
    return
  }
  ElMessage.success(response.message || '插件已安装')
  loadPlugins(true)
}

const checkOne = plugin => {
  checking.value = true
  http.checkPluginUpdate(plugin.id, marketUrl.value)
      .then(res => {
        const info = res.data
        ElMessage.success(info?.updateAvailable ? `发现新版本 ${info.latestVersion}` : '已是最新版本')
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
        ElMessage.success('检查完成')
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
        ElMessage.success(`已更新到 ${res.data?.version || '最新版本'}`)
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
  ElMessageBox.confirm(`安装插件「${entry.name || entry.id}」？插件将获得与服务相同的运行权限。`,
      '安装插件', {type: 'warning', confirmButtonText: '安装', cancelButtonText: '取消'})
      .then(() => {
        installing.value = entry.id
        http.installPluginFromMarket(entry)
            .then(res => {
              ElMessage.success(`插件 ${res.data?.name || entry.id} 已安装`)
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

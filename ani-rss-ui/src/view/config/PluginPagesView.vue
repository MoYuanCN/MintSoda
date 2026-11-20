<template>
  <div class="plugin-page-view app-page-layout">
    <PageHeaderView :title="activePage?.title || $t('pluginpages')" :subtitle="activePage?.description || ''">
      <template #actions>
        <el-select v-if="pages.length > 1" v-model="activeKey" class="plugin-page-select">
          <el-option v-for="item in pages" :key="item.key" :label="item.page.title || item.page.id"
                     :value="item.key"/>
        </el-select>
      </template>
    </PageHeaderView>
    <div class="plugin-page-body app-page-content app-page-padding">
      <el-scrollbar class="plugin-page-scrollbar">
        <div v-loading="loading" class="plugin-page-content">
          <el-empty v-if="!loading && !pages.length" :description="$t('pluginpagenotfound')"/>
          <el-card v-for="(block, index) in activePage?.blocks || []" :key="`${block.type}-${index}`"
                   shadow="never" class="plugin-page-block">
            <template v-if="block.type === 'DIVIDER'">
              <el-divider/>
            </template>
            <template v-else-if="block.type === 'TEXT'">
              <strong v-if="block.title" class="plugin-page-block-title">{{ block.title }}</strong>
              <el-text class="plugin-page-text">{{ block.text }}</el-text>
            </template>
            <template v-else-if="block.type === 'FORM'">
              <strong v-if="block.title" class="plugin-page-block-title">{{ block.title }}</strong>
              <el-form label-position="top" class="plugin-page-form" @submit.prevent>
                <el-form-item v-for="field in block.fields" :key="field.key" :label="field.label || field.key">
                  <PluginFieldInput :field="field" :model="formValues(block)"/>
                </el-form-item>
              </el-form>
              <div class="plugin-page-actions">
                <el-button bg text type="primary" :loading="submitting === blockKey(block)"
                           @click="submitForm(block)">
                  {{ $t('save') }}
                </el-button>
              </div>
            </template>
            <template v-else-if="block.type === 'TABLE'">
              <strong v-if="block.title" class="plugin-page-block-title">{{ block.title }}</strong>
              <el-table :data="tableRows(block)" size="small" class="plugin-page-table">
                <el-table-column v-for="column in block.columns" :key="column.key"
                                 :prop="column.key" :label="column.label || column.key" show-overflow-tooltip/>
                <template #empty>
                  <el-empty :description="$t('pluginpagenodata')"/>
                </template>
              </el-table>
              <div class="plugin-page-actions">
                <el-button bg text icon="Refresh" :loading="loadingBlock === blockKey(block)"
                           @click="loadTable(block)">
                  {{ $t('refresh') }}
                </el-button>
              </div>
            </template>
            <template v-else-if="block.type === 'ACTIONS'">
              <strong v-if="block.title" class="plugin-page-block-title">{{ block.title }}</strong>
              <div class="plugin-page-actions">
                <el-button v-for="action in block.actions" :key="action.id" bg text type="primary"
                           :loading="submitting === actionKey(block, action)" @click="runAction(block, action)">
                  {{ action.label || action.id }}
                </el-button>
              </div>
            </template>
          </el-card>
        </div>
      </el-scrollbar>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {useRoute} from "vue-router"
import {ElMessage, ElMessageBox} from "element-plus"
import PageHeaderView from '@/view/custom/PageHeaderView.vue'
import PluginFieldInput from '@/view/custom/PluginFieldInput.vue'
import * as http from '@/js/plugin.js'

const {t} = useI18n()
const route = useRoute()

const loading = ref(false)
const loadingBlock = ref('')
const submitting = ref('')
const activeKey = ref('')
const pages = ref([])
const store = reactive({})
const tableData = reactive({})

const blockKey = block => `${activeKey.value}:${block.source}:${block.title || ''}`
const actionKey = (block, action) => `${blockKey(block)}:${action.id}`

const activePage = computed(() => pages.value.find(item => item.key === activeKey.value)?.page || null)

const parseData = value => {
  if (value == null || value === '') return null
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

const formValues = block => {
  const key = blockKey(block)
  if (!store[key]) {
    store[key] = {}
  }
  return store[key]
}

const initBlocks = () => {
  const entry = activeEntry()
  if (!entry) return
  ;(entry.page.blocks || []).forEach(block => {
    if (block.type === 'FORM') {
      const key = blockKey(block)
      store[key] = {}
      ;(block.fields || []).forEach(field => {
        store[key][field.key] = ''
      })
      loadForm(block, key)
    }
    if (block.type === 'TABLE') {
      delete tableData[blockKey(block)]
      loadTable(block)
    }
  })
}

const tableRows = block => tableData[blockKey(block)] || []

const loadPlugins = async () => {
  loading.value = true
  try {
    const response = await http.listPlugins()
    const result = []
    ;(response.data || []).forEach(plugin => {
      if (plugin.state !== 'ENABLED') return
      ;(plugin.pages || []).forEach(page => {
        result.push({
          key: `${plugin.id}:${page.id}`,
          pluginId: plugin.id,
          pluginName: plugin.name || plugin.id,
          page
        })
      })
    })
    pages.value = result
    const requested = `${route.params.pluginId || ''}:${route.params.pageId || ''}`
    activeKey.value = result.some(item => item.key === requested) ? requested : (result[0]?.key || '')
  } finally {
    loading.value = false
  }
}

const activeEntry = () => pages.value.find(item => item.key === activeKey.value) || null

const loadForm = async (block, key) => {
  const entry = activeEntry()
  if (!entry || !block.source) return
  try {
    const response = await http.callPlugin(entry.pluginId, block.source, null, 'GET')
    const data = parseData(response.data)
    if (data && typeof data === 'object' && !Array.isArray(data)) {
      Object.entries(data).forEach(([name, value]) => {
        store[key][name] = value == null ? '' : String(value)
      })
    }
  } catch {
    // 表单初始值读取失败时保留空值，用户仍可填写并保存。
  }
}

const loadTable = async block => {
  const entry = activeEntry()
  if (!entry || !block.source) return
  const key = blockKey(block)
  loadingBlock.value = key
  try {
    const response = await http.callPlugin(entry.pluginId, block.source, null, 'GET')
    const data = parseData(response.data)
    tableData[key] = Array.isArray(data) ? data : (Array.isArray(data?.items) ? data.items : [])
  } catch {
    tableData[key] = []
  } finally {
    loadingBlock.value = ''
  }
}

const submitForm = async block => {
  const entry = activeEntry()
  if (!entry || !block.source) return
  const key = blockKey(block)
  submitting.value = key
  try {
    const response = await http.callPlugin(entry.pluginId, block.source, formValues(block), 'POST')
    ElMessage.success(response.message || t('saved'))
  } finally {
    submitting.value = ''
  }
}

const runAction = async (block, action) => {
  const entry = activeEntry()
  if (!entry || !action.source) return
  if (action.confirm) {
    try {
      await ElMessageBox.confirm(action.confirmText || t('areyousure'), t('confirm'), {type: 'warning'})
    } catch {
      return
    }
  }
  submitting.value = actionKey(block, action)
  try {
    const response = await http.callPlugin(entry.pluginId, action.source, {}, action.method || 'POST')
    ElMessage.success(response.message || t('executed'))
    pages.value.filter(item => item.key === activeKey.value)
        .flatMap(item => item.page.blocks || [])
        .filter(item => item.type === 'TABLE')
        .forEach(item => loadTable(item))
  } finally {
    submitting.value = ''
  }
}

watch(activeKey, () => initBlocks())

// KeepAlive 会缓存页面，路由在插件页面之间切换时重新定位。
watch(() => [route.params.pluginId, route.params.pageId], () => {
  const requested = `${route.params.pluginId || ''}:${route.params.pageId || ''}`
  if (pages.value.some(item => item.key === requested)) {
    activeKey.value = requested
  }
})

onMounted(loadPlugins)
</script>

<style scoped>
.plugin-page-select {
  width: min(320px, 60vw);
}

.plugin-page-scrollbar {
  height: 100%;
}

.plugin-page-content {
  display: grid;
  gap: 12px;
  min-height: 120px;
}

.plugin-page-block :deep(.el-card__body) {
  display: grid;
  gap: 10px;
}

.plugin-page-block-title {
  display: block;
  font-size: 14px;
  line-height: 1.4;
}

.plugin-page-text {
  display: block;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.plugin-page-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0 12px;
}

.plugin-page-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.plugin-page-table {
  width: 100%;
}
</style>

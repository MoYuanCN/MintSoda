<template>
  <template v-if="notificationConfig['notificationType'] === 'CUSTOM_REQUEST'">
    <SettingsItem label="Method">
      <el-select v-model:model-value="notificationConfig['webHookMethod']">
        <el-option v-for="item in ['POST','PUT','PATCH','GET','DELETE']"
                   :key="item"
                   :label="item"
                   :value="item"/>
      </el-select>
    </SettingsItem>
    <SettingsItem label="URL">
      <el-input v-model:model-value="notificationConfig['webHookUrl']" type="textarea"
                autosize
                placeholder="https://www.xxx.com?subgroup=${subgroup}"/>
    </SettingsItem>
    <SettingsItem label="Header">
      <el-input
          type="textarea"
          :autosize="{ minRows: 2}"
          v-model="notificationConfig['webHookHeader']"
          :placeholder="'Authorization: Bearer xxx\nCookie: 123456'"/>
    </SettingsItem>
    <SettingsItem :label="$t('bodysource')">
      <el-radio-group v-model="bodyMode" @change="bodyModeChanged">
        <el-radio-button value="manual">{{ $t('custombody') }}</el-radio-button>
        <el-radio-button value="template">{{ $t('usetemplatelibrary') }}</el-radio-button>
      </el-radio-group>
    </SettingsItem>
    <template v-if="bodyMode === 'template'">
      <SettingsItem :label="$t('templateasbody')">
        <div class="notification-template-select">
          <el-select v-model="notificationConfig.notificationTemplateId" clearable
                     :placeholder="$t('selectatemplateasthebody')">
            <el-option v-for="template in templates"
                       :key="template.id"
                       :label="`${template.name || $t('untitledtemplate')} · ${template.type === 'JSON' ? 'JSON' : $t('text')}`"
                       :value="template.id"/>
          </el-select>
          <el-text size="small" type="info">
            {{ $t('placeholderslikeinthetemplatearereplacedwith') }}
          </el-text>
        </div>
      </SettingsItem>
      <SettingsItem v-if="selectedTemplate" :label="$t('templatepreview')">
        <pre class="notification-template-preview">{{ selectedTemplate.content || '' }}</pre>
      </SettingsItem>
      <el-alert v-else :closable="false" show-icon type="warning"
                :title="$t('notemplateselectedanemptybodywillbesent')"/>
    </template>
    <template v-else>
      <SettingsItem label="Content-Type">
        <el-input v-model.trim="notificationConfig['webHookContentType']" placeholder="application/json"/>
      </SettingsItem>
      <SettingsItem label="Body">
        <el-input v-model="notificationConfig['webHookBody']"
                  type="textarea"
                  :autosize="{ minRows: 4}"
                  :placeholder="bodyPlaceholder"/>
        <el-alert v-if="isJson && manualBody && !validJson" :closable="false" show-icon type="error"
                  :title="$t('thebodytemplateisnotvalidjsonputplaceholdersinsidejsonstringv')"/>
        <el-alert v-else-if="isJson" :closable="false" show-icon type="info"
                  :title="$t('templatevariablesinjsonareescapedautomatical')"/>
      </SettingsItem>
    </template>
  </template>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {computed, ref, watch} from "vue";
const {t} = useI18n()
let props = defineProps(['notificationConfig', 'config', 'templates'])

// 模板即 Body，因此文本模板与 JSON 模板都可以选。
const templates = computed(() => props.templates || [])

const selectedTemplate = computed(() =>
    templates.value.find(template => template.id === props.notificationConfig['notificationTemplateId']))

// 有模板 ID 直接视为模板模式，其余情况都按手写处理。
const bodyMode = ref(props.notificationConfig['notificationTemplateId'] ? 'template' : 'manual')

// 每次打开弹窗都会替换配置对象，按新对象重新决定 Body 来源。
watch(() => props.notificationConfig, config => {
  bodyMode.value = config?.['notificationTemplateId'] ? 'template' : 'manual'
})

const bodyModeChanged = mode => {
  if (mode === 'template') {
    // 模板即 Body，选定模板后忽略手写内容。
    props.notificationConfig['webHookContentType'] =
        selectedTemplate.value?.type === 'JSON' ? 'application/json' : 'text/plain'
    return
  }
  props.notificationConfig['notificationTemplateId'] = ''
}

watch(selectedTemplate, template => {
  if (!template) return
  props.notificationConfig['webHookContentType'] = template.type === 'JSON' ? 'application/json' : 'text/plain'
})

const manualBody = computed(() => props.notificationConfig['webHookBody'] || '')
const isJson = computed(() => /application\/json|\+json/i.test(props.notificationConfig['webHookContentType'] || ''))
const bodyPlaceholder = computed(() => isJson.value
    ? '{"subgroup":"${subgroup}","event":"${text}"}'
    : t('subgroup') + ': ${subgroup} ${text}')
const validJson = computed(() => {
  try {
    JSON.parse(manualBody.value)
    return true
  } catch {
    return false
  }
})
</script>

<style scoped>
.notification-template-select {
  display: grid;
  gap: 4px;
}

.notification-template-preview {
  width: 100%;
  max-height: 160px;
  margin: 0;
  padding: 8px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

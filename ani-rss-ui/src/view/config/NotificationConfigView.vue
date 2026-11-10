<template>
  <el-dialog v-model="dialogVisible" center title="修改通知">
    <el-scrollbar :always="true" class="notification-config-scrollbar">
      <SettingsItem label="通知类型">
        <el-select v-model="notificationConfig['notificationType']">
          <el-option v-for="it in notificationTypeList"
                     :key="it.name"
                     :label="it.label"
                     :value="it.name"
          />
        </el-select>
      </SettingsItem>
      <SettingsItem label="备注">
        <el-input v-model="notificationConfig['comment']" placeholder="无备注"/>
      </SettingsItem>
      <SettingsItem label="消息模板">
        <div class="notification-template-select">
          <el-select v-model="notificationConfig.notificationTemplateId" clearable placeholder="使用单条通知模板"
                     @change="templateSelectionChanged">
            <el-option v-for="template in availableTemplates"
                       :key="template.id" :label="`${template.name || '未命名模板'} · ${template.type === 'JSON' ? 'JSON' : '文本'}`"
                       :value="template.id"/>
          </el-select>
          <el-text size="small" type="info">Telegram 使用文本模板；自定义请求可选文本或 JSON。未选择时使用渠道模板。</el-text>
        </div>
      </SettingsItem>
      <SettingsItem label="通知状态">
        <el-checkbox-group v-model:model-value="notificationConfig['statusList']">
          <el-checkbox label="开始下载" value="DOWNLOAD_START"/>
          <el-checkbox label="下载完成" value="DOWNLOAD_END"/>
          <el-checkbox label="缺集" value="OMIT"/>
          <el-checkbox label="错误" value="ERROR"/>
          <el-checkbox label="订阅完结" value="COMPLETED"/>
        </el-checkbox-group>
      </SettingsItem>
      <EmbyRefreshNotificationView v-model:notification-config="notificationConfig" v-model:config="props.config"/>
      <TelegramNotificationView v-model:notification-config="notificationConfig" v-model:config="props.config"/>
      <WebhookNotificationView v-model:notification-config="notificationConfig" v-model:config="props.config"/>
      <SettingsItem label="顺序">
        <div>
          <el-input-number
              v-model="notificationConfig['sort']"
              class="notification-input-width"
          />
        </div>
      </SettingsItem>
      <SettingsItem label="重试">
        <el-input-number
            :min="0"
            :max="100"
            v-model="notificationConfig['retry']"
            class="notification-input-width"
        />
      </SettingsItem>
      <SettingsItem label="开启">
        <el-switch v-model="notificationConfig['enable']"/>
      </SettingsItem>
    </el-scrollbar>
    <div class="flex notification-config-footer">
      <el-button bg text @click="messageTest" icon="Odometer" :loading="messageTestLoading">测试
      </el-button>
      <el-button @click="dialogVisible = false" text bg icon="Check" type="primary">确定
      </el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {computed, ref, watch} from "vue";
import EmbyRefreshNotificationView from "./notification/EmbyRefreshNotificationView.vue";
import TelegramNotificationView from "./notification/TelegramNotificationView.vue";
import WebhookNotificationView from "./notification/WebhookNotificationView.vue";
import {notificationTypeList} from "@/js/notification-type.js";
import {ElMessage} from "element-plus";
import {testNotification} from "@/js/http.js";


let notificationConfig = ref({
  "comment": "无备注",
  "notificationTemplate": "${text}",
  "notificationType": "TELEGRAM",
  "telegramBotToken": "",
  "telegramChatId": "",
  "telegramTopicId": -1,
  "telegramApiHost": "https://api.telegram.org",
  "telegramImage": true,
  "telegramFormat": "",
  "webHookMethod": "POST",
  "webHookUrl": "",
  "webHookHeader": "",
  "webHookBody": "",
  "webHookContentType": "application/json",
  "embyRefresh": false,
  "embyApiKey": "",
  "embyRefreshViewIds": [],
  "embyDelayed": 0,
  "statusList": [
    "DOWNLOAD_START",
    "OMIT",
    "ERROR"
  ]
})

const messageTestLoading = ref(false)
const availableTemplates = computed(() => {
  const type = notificationConfig.value.notificationType
  const templates = props.config.notificationTemplateList || []
  if (type === 'TELEGRAM') return templates.filter(template => template.type !== 'JSON')
  if (type === 'CUSTOM_REQUEST') return templates
  return []
})

watch(() => notificationConfig.value.notificationType, () => {
  const selectedId = notificationConfig.value.notificationTemplateId
  const selectedTemplate = availableTemplates.value.find(template => template.id === selectedId)
  if (selectedId && !selectedTemplate) {
    notificationConfig.value.notificationTemplateId = ''
    return
  }
  if (selectedTemplate && notificationConfig.value.notificationType === 'CUSTOM_REQUEST') {
    notificationConfig.value.webHookContentType = selectedTemplate.type === 'JSON' ? 'application/json' : 'text/plain'
  }
})

const templateSelectionChanged = id => {
  const template = availableTemplates.value.find(item => item.id === id)
  if (!template || notificationConfig.value.notificationType !== 'CUSTOM_REQUEST') return
  notificationConfig.value.webHookContentType = template.type === 'JSON' ? 'application/json' : 'text/plain'
}

const messageTest = () => {
  messageTestLoading.value = true

  let config = JSON.parse(JSON.stringify(notificationConfig.value))
  config.embyDelayed = 0
  const selectedTemplate = (props.config.notificationTemplateList || [])
      .find(template => template.id === config.notificationTemplateId)
  if (selectedTemplate) {
    const templateType = selectedTemplate.type === 'JSON' ? 'JSON' : 'TEXT'
    const content = selectedTemplate.content ?? (templateType === 'JSON'
        ? selectedTemplate.customRequestBody
        : selectedTemplate.telegramTemplate) ?? ''
    config.notificationTemplateId = ''
    if (templateType === 'JSON') {
      try {
        JSON.parse(content)
      } catch {
        ElMessage.error('当前 JSON 模板格式无效，请先修正模板内容')
        messageTestLoading.value = false
        return
      }
      config.webHookBody = content
      config.webHookContentType = 'application/json'
    } else {
      config.notificationTemplate = content
      config.webHookBody = content
      if (config.notificationType === 'CUSTOM_REQUEST') config.webHookContentType = 'text/plain'
    }
  }

  testNotification(config)
      .then(res => {
        ElMessage.success(res.message)
      })
      .finally(() => {
        messageTestLoading.value = false
      })
}

let dialogVisible = ref(false)

let props = defineProps(['config'])

let show = (newNotificationConfig) => {
  notificationConfig.value = newNotificationConfig
  const selectedId = notificationConfig.value.notificationTemplateId
  if (selectedId && !availableTemplates.value.some(template => template.id === selectedId)) {
    notificationConfig.value.notificationTemplateId = ''
  }
  dialogVisible.value = true
}

defineExpose({
  show
})

</script>

<style scoped>
.notification-config-scrollbar {
  height: 530px;
  padding: 15px;
}

.notification-config-footer {
  justify-content: space-between;
  width: 100%;
  margin-top: 8px;
}

.notification-template-select {
  display: grid;
  gap: 4px;
}
</style>

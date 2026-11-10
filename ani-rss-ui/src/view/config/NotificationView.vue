<template>
  <el-collapse>
    <el-collapse-item title="可复用通知模板">
      <div class="notification-template-list">
        <el-card v-for="(template, index) in templateList" :key="template.id" shadow="never">
          <div class="notification-template-heading">
            <el-input v-model="template.name" placeholder="模板名称"/>
            <el-button circle text type="danger" icon="Delete" aria-label="删除模板"
                       @click="removeTemplate(index)"/>
          </div>
          <el-form label-position="top">
            <el-form-item label="Telegram 消息模板">
              <el-input v-model="template.telegramTemplate" type="textarea"
                        :autosize="{ minRows: 2, maxRows: 6 }" placeholder="${notification}"/>
            </el-form-item>
            <el-form-item label="自定义请求 JSON 模板">
              <el-input v-model="template.customRequestBody" type="textarea"
                        :autosize="{ minRows: 2, maxRows: 6 }"
                        placeholder='{"text":"${notification}"}'/>
              <el-alert v-if="invalidJson(template.customRequestBody)" class="template-json-error"
                        :closable="false" show-icon type="error"
                        title="请求体模板不是有效 JSON；请将变量写在 JSON 字符串值内。"/>
            </el-form-item>
          </el-form>
        </el-card>
        <el-button class="notification-template-add" icon="Plus" bg text type="primary" @click="addTemplate">
          添加模板
        </el-button>
      </div>
      <el-alert class="notification-template-help" :closable="false" show-icon type="info"
                title="创建多个模板后，可在每条通知配置中选择。JSON 中的变量会自动转义。"/>
    </el-collapse-item>
  </el-collapse>
  <el-collapse>
    <el-collapse-item title="通知模板">
      <el-input v-model="props.config['notificationTemplate']" :autosize="{ minRows: 4, maxRows: 10}"
                placeholder="${emoji} ${action}\n${title} S${seasonFormat}E${episodeFormat}\n${text}" type="textarea"/>
      <el-alert class="notification-template-help" :closable="false" show-icon type="info"
                title="支持 title、text、notification、season、episode、subgroup、tmdburl、bgmUrl、downloadPath、image 等变量。自定义请求的 JSON 请求体会自动转义字符串变量。"/>
      <div class="flex notification-template-link">
        <el-link type="primary" href="https://github.com/MoYuanCN/ani-rss-Yuri" target="_blank">
          通知模版示例
        </el-link>
      </div>
    </el-collapse-item>
  </el-collapse>
  <div class="notification-container">
    <div>
      <el-space wrap class="flex flex-wrap gap-4" size="small">
        <el-card v-for="it in props.config['notificationConfigList']" shadow="never" class="notification-card">
          <div class="flex notification-card-content">
            <div class="notification-card-main">
              <el-tooltip :content="getLabel(it['notificationType'])" placement="top">
                <p class="notification-card-title">
                  {{ getLabel(it['notificationType']) }}
                </p>
              </el-tooltip>
              <el-text line-clamp="1" size="small" class="notification-card-text" truncated>
                {{ it['comment'] ? it['comment'] : '无备注' }}
              </el-text>
            </div>
            <div>
              <el-dropdown trigger="click">
                <el-button circle icon="MoreFilled" size="large" text type="primary"/>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="notificationConfigRef?.show(it)">
                      <el-text type="primary">
                        <el-icon>
                          <Edit/>
                        </el-icon>
                        编辑
                      </el-text>
                    </el-dropdown-item>
                    <el-dropdown-item @click="del(it)">
                      <el-text type="danger">
                        <el-icon>
                          <Delete/>
                        </el-icon>
                        删除
                      </el-text>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </el-card>
      </el-space>
    </div>
    <el-button
        icon="FolderAdd"
        bg text
        type="primary"
        @click="add"
        class="notification-add-button"
        :loading="addLoading"
    >
      添加通知
    </el-button>
  </div>
  <NotificationConfigView ref="notificationConfigRef" v-model:config="props.config"/>
</template>

<script setup>
import NotificationConfigView from "./NotificationConfigView.vue";
import {computed, ref} from "vue";
import {ElMessage} from "element-plus";

import {getLabel} from "@/js/notification-type.js";
import {Delete, Edit} from "@element-plus/icons-vue";
import {newNotification} from "@/js/http.js";

let addLoading = ref(false)
const templateList = computed(() => {
  if (!Array.isArray(props.config.notificationTemplateList)) props.config.notificationTemplateList = []
  return props.config.notificationTemplateList
})

const addTemplate = () => {
  templateList.value.push({
    id: globalThis.crypto?.randomUUID?.() || `template-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    name: `模板 ${templateList.value.length + 1}`,
    telegramTemplate: '${notification}',
    customRequestBody: '{"text":"${notification}"}'
  })
}

const invalidJson = body => {
  if (!body?.trim()) return false
  try {
    JSON.parse(body)
    return false
  } catch {
    return true
  }
}

const removeTemplate = index => {
  const id = templateList.value[index]?.id
  templateList.value.splice(index, 1)
  for (const notification of props.config.notificationConfigList || []) {
    if (notification.notificationTemplateId === id) notification.notificationTemplateId = ''
  }
  ElMessage.info('已删除模板；使用该模板的通知将改用各自的备用模板')
}

let add = () => {
  addLoading.value = true
  newNotification()
      .then((res) => {
        props.config['notificationConfigList'].push(res.data)
        notificationConfigRef.value?.show(res.data)
      })
      .finally(() => {
        addLoading.value = false
      })
}

let del = (it) => {
  props.config['notificationConfigList'] = props.config['notificationConfigList'].filter(item => item !== it)
}

let notificationConfigRef = ref()

let props = defineProps(['config'])
</script>

<style scoped>
.notification-template-list {
  display: grid;
  gap: 10px;
}

.notification-template-heading {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.notification-template-add {
  justify-self: start;
}

.template-json-error {
  margin-top: 6px;
}

.notification-template-link {
  width: 100%;
  justify-content: end;
}

.notification-container {
  margin-top: 8px;
  padding: 0 4px;
}

.notification-card {
  width: 190px;
  height: 88px;
}

.notification-card :deep(.el-card__body) {
  height: 100%;
  box-sizing: border-box;
}

.notification-card-content {
  height: 100%;
  align-items: center;
  justify-content: space-between;
}

.notification-card-main {
  min-width: 0;
  flex: 1;
}

.notification-card-title {
  height: 22px;
  margin: 0 0 4px;
  overflow: hidden;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-card-text {
  max-width: 120px;
}

.notification-add-button {
  margin-top: 12px;
}
</style>

<style>
/* 通知组件通用样式 - 非scoped以便子组件使用 */
.notification-input-width {
  width: 160px !important;
}

.notification-flex-between {
  justify-content: space-between;
  width: 100%;
}

.notification-margin-top-right {
  margin-top: 4px;
  margin-right: 4px;
}

.notification-margin-top-center {
  margin-top: 4px;
  align-items: center;
}

.notification-margin-left {
  margin-left: 4px;
}

.notification-flex-end {
  display: flex;
  justify-content: end;
}
</style>

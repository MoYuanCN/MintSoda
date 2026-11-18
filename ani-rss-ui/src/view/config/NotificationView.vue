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
            <el-form-item label="模板类型">
              <el-select v-model="template.type" class="template-type-select">
                <el-option label="普通文本" value="TEXT"/>
                <el-option label="JSON" value="JSON"/>
              </el-select>
            </el-form-item>
            <el-form-item :label="template.type === 'JSON' ? 'JSON 内容' : '文本内容'">
              <el-input v-model="template.content" type="textarea"
                        :autosize="{ minRows: 2, maxRows: 6 }"
                        :placeholder="templatePlaceholder(template)"/>
              <el-alert v-if="template.type === 'JSON' && invalidJson(template.content)" class="template-json-error"
                        :closable="false" show-icon type="error"
                        title="模板内容不是有效 JSON；请将变量写在 JSON 字符串值内。"/>
            </el-form-item>
          </el-form>
        </el-card>
        <el-button class="notification-template-add" icon="Plus" bg text type="primary" @click="addTemplate">
          添加模板
        </el-button>
      </div>
      <el-alert class="notification-template-help" :closable="false" show-icon type="info"
                title="可创建多个命名模板；Telegram 使用普通文本，自定义请求可使用普通文本或 JSON。模板直接填写 ${title}、${subgroup}、${text} 等字段变量；JSON 字符串变量会自动转义。"/>
    </el-collapse-item>
  </el-collapse>
  <div class="notification-container">
    <div>
      <div class="notification-grid">
        <el-card v-for="it in props.config['notificationConfigList']" shadow="never" class="notification-card"
                 :class="{ 'notification-card-disabled': !it['enable'] }">
          <div class="flex notification-card-content">
            <div class="notification-card-main">
              <div class="notification-card-heading">
                <!-- 状态点与标题同行，实心和空心区分状态，并为键盘及读屏提供说明。 -->
                <el-tooltip :content="it['enable'] ? '已启用' : '已禁用'" placement="top">
                  <span class="notification-status" :class="{ 'is-enabled': it['enable'] }"
                        role="img" :aria-label="it['enable'] ? '已启用' : '已禁用'" tabindex="0"/>
                </el-tooltip>
                <el-tooltip :content="getLabel(it['notificationType'])" placement="top">
                  <p class="notification-card-title">
                    {{ getLabel(it['notificationType']) }}
                  </p>
                </el-tooltip>
              </div>
              <el-text line-clamp="1" size="small" class="notification-card-text" truncated>
                {{ it['comment'] ? it['comment'] : '无备注' }}
              </el-text>
            </div>
            <div>
              <el-dropdown trigger="click">
                <el-button circle icon="MoreFilled" text class="notification-more-button" aria-label="通知操作"/>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="toggleNotification(it)">
                      <el-text type="warning" v-if="it['enable']">
                        <el-icon>
                          <SwitchButton/>
                        </el-icon>
                        禁用
                      </el-text>
                      <el-text type="success" v-else>
                        <el-icon>
                          <SwitchButton/>
                        </el-icon>
                        启用
                      </el-text>
                    </el-dropdown-item>
                    <el-dropdown-item @click="edit(it)">
                      <el-text type="primary">
                        <el-icon>
                          <Edit/>
                        </el-icon>
                        编辑
                      </el-text>
                    </el-dropdown-item>
                    <el-dropdown-item @click="del(it)" divided>
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
      </div>
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
  <NotificationConfigView ref="notificationConfigRef" v-model:config="props.config"
                         @confirm="onConfirm"/>
</template>

<script setup>
import NotificationConfigView from "./NotificationConfigView.vue";
import {computed, ref} from "vue";
import {ElMessage} from "element-plus";

import {getLabel} from "@/js/notification-type.js";
import {Delete, Edit, SwitchButton} from "@element-plus/icons-vue";
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
    type: 'TEXT',
    content: '${title} S${seasonFormat}E${episodeFormat}\n字幕组: ${subgroup}\n${text}'
  })
}

const templatePlaceholder = template => template.type === 'JSON'
    ? '{"subgroup":"${subgroup}","event":"${text}"}'
    : '${title} S${seasonFormat}E${episodeFormat}\n字幕组: ${subgroup}\n${text}'

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

// 新增通知先编辑草稿，点击确定才写入列表；编辑则替换原项。
let pendingAdd = false
let editingNotification = null

let add = () => {
  addLoading.value = true
  newNotification()
      .then((res) => {
        pendingAdd = true
        editingNotification = null
        notificationConfigRef.value?.show(res.data)
      })
      .finally(() => {
        addLoading.value = false
      })
}

let edit = (notification) => {
  pendingAdd = false
  editingNotification = notification
  notificationConfigRef.value?.show(notification)
}

let onConfirm = (notification) => {
  const list = props.config['notificationConfigList'] || (props.config['notificationConfigList'] = [])
  if (pendingAdd) {
    pendingAdd = false
    list.push(notification)
    return
  }
  const index = list.indexOf(editingNotification)
  if (index > -1) list.splice(index, 1, notification)
  editingNotification = null
}

// 与编辑弹窗共用配置状态，切换后沿用设置页的保存流程。
const toggleNotification = (notification) => {
  notification.enable = !notification.enable
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

.template-type-select {
  width: 180px;
}

.template-json-error {
  margin-top: 6px;
}

.notification-container {
  margin-top: 8px;
  padding: 0 4px;
}

/* 按容器宽度自动排列通知卡片，每列至少 220px 并均分剩余空间。 */
.notification-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 8px;
}

.notification-card {
  min-width: 0;
  height: 88px;
}

.notification-card :deep(.el-card__body) {
  height: 100%;
  box-sizing: border-box;
  padding: 16px;
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

.notification-card-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

/* 实心绿点表示启用，空心灰点表示禁用，不增加卡片高度。 */
.notification-status {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  box-sizing: border-box;
  border: 1px solid var(--el-text-color-placeholder);
  border-radius: 50%;
}

.notification-status.is-enabled {
  border-color: var(--el-color-success);
  background: var(--el-color-success);
  box-shadow: 0 0 0 3px var(--el-color-success-light-9);
}

.notification-card-title {
  height: 22px;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  line-height: 22px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-card-text {
  display: block;
  margin-left: 16px;
  color: var(--el-text-color-secondary);
}

/* 禁用时只弱化标题，保留备注和操作按钮的可读性。 */
.notification-card-disabled .notification-card-title {
  color: var(--el-text-color-secondary);
}

.notification-more-button {
  margin-left: 4px;
  color: var(--el-text-color-secondary);
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

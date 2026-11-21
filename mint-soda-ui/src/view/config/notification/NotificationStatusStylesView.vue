<template>
  <div class="notification-status-styles">
    <el-alert :closable="false" show-icon type="info" :title="$t('notificationstatusstyleshelp')">
      <template #default>
        <div class="notification-status-tokens">
          <span v-for="token in tokens" :key="token.name" class="notification-status-token">
            <code>{{ token.name }}</code>
            <span class="notification-status-token-hint">{{ token.hint }}</span>
          </span>
        </div>
      </template>
    </el-alert>
    <div class="notification-status-list">
      <div v-for="status in statusList" :key="status.name" class="notification-status-row">
        <span class="notification-status-name">{{ $t(status.label) }}</span>
        <el-input v-model="statusStyles[status.name].emoji" class="notification-status-emoji"
                  :placeholder="$t('stateemoji')" maxlength="8"/>
        <el-input v-model="statusStyles[status.name].label" class="notification-status-label"
                  :placeholder="$t('statelabel')"/>
        <el-input v-model="statusStyles[status.name].template" class="notification-status-template"
                  :placeholder="$t('stateeventtemplate')"/>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed} from "vue"
import {useI18n} from "vue-i18n"

const props = defineProps({
  config: {type: Object, required: true}
})

const {t} = useI18n()

// 占位符属于模板语法而不是界面文案，直接以代码形式展示，避免 i18n 插值转义。
const tokens = computed(() => [
  {name: '${name}', hint: t('resourcenameorepisode')},
  {name: '${source}', hint: t('standbyrssmarker')},
  {name: '${title}', hint: t('subscriptiontitle')},
  {name: '${subgroup}', hint: t('subgroup')}
])

const statusList = [
  {name: 'DOWNLOAD_START', label: 'downloadstarted'},
  {name: 'DOWNLOAD_END', label: 'downloadfinished'},
  {name: 'OMIT', label: 'missingepisodes'},
  {name: 'ERROR', label: 'error'},
  {name: 'COMPLETED', label: 'subscriptioncompleted'}
]

// 缺省值由后端补齐；这里只保证对象结构存在，避免未保存时渲染报错。
const statusStyles = computed(() => {
  const styles = props.config.notificationStatusStyles
      || (props.config.notificationStatusStyles = {})
  for (const status of statusList) {
    if (!styles[status.name]) styles[status.name] = {emoji: '', label: '', template: ''}
  }
  return styles
})
</script>

<style scoped>
.notification-status-styles {
  display: grid;
  gap: 10px;
}

.notification-status-tokens {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  margin-top: 6px;
}

.notification-status-token {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.notification-status-tokens code {
  padding: 1px 6px;
  font-size: 12px;
  border-radius: 4px;
  background: var(--el-fill-color);
}

.notification-status-token-hint {
  margin-right: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.notification-status-list {
  display: grid;
  gap: 8px;
}

.notification-status-row {
  display: grid;
  grid-template-columns: 96px 88px 170px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.notification-status-name {
  font-size: 13px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .notification-status-row {
    grid-template-columns: 76px 76px minmax(0, 1fr);
  }

  .notification-status-template {
    grid-column: 1 / -1;
  }
}
</style>

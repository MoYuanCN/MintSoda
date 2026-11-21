<template>
  <div v-if="notificationConfig.notificationType === 'PLUGIN'">
    <SettingsItem :label="$t('pluginchannel')">
      <el-select v-model="notificationConfig.pluginChannelId" clearable :placeholder="$t('selectachannelprovidedbyaplugin')"
                 class="plugin-channel-select">
        <el-option v-for="channel in channels" :key="channel.id"
                   :label="channel.name || channel.id" :value="channel.id"/>
      </el-select>
    </SettingsItem>
    <SettingsItem v-for="field in activeFields" :key="field.key" :label="field.label || field.key">
      <el-input v-if="field.type === 'PASSWORD'" v-model="options[field.key]" type="password" show-password
                :placeholder="field.placeholder || ''" class="plugin-channel-input"/>
      <el-input v-else-if="field.type !== 'SWITCH' && field.type !== 'NUMBER' && field.type !== 'SELECT'"
                v-model="options[field.key]" :placeholder="field.placeholder || ''" class="plugin-channel-input"/>
      <el-switch v-else-if="field.type === 'SWITCH'"
                 :model-value="options[field.key] === 'true'"
                 @update:model-value="value => options[field.key] = value ? 'true' : 'false'"/>
      <el-input-number v-else-if="field.type === 'NUMBER'"
                       :model-value="Number(options[field.key] || 0)"
                       @update:model-value="value => options[field.key] = String(value)"/>
      <el-select v-else v-model="options[field.key]" class="plugin-channel-input">
        <el-option v-for="option in field.options" :key="option" :label="option" :value="option"/>
      </el-select>
    </SettingsItem>
    <el-text v-if="!channels.length" size="small" type="info">
      {{ $t('nopluginprovidesnotificationchannelsyetenabl') }}
    </el-text>
  </div>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {computed, onMounted, ref, watch} from "vue";
import * as pluginApi from "@/js/plugin.js";

const props = defineProps({
  notificationConfig: {type: Object, required: true}
})

const channels = ref([])
const options = computed(() => {
  if (!props.notificationConfig.pluginOptions) {
    props.notificationConfig.pluginOptions = {}
  }
  return props.notificationConfig.pluginOptions
})

const activeFields = computed(() => {
  const channel = channels.value.find(it => it.id === props.notificationConfig.pluginChannelId)
  return channel?.fields || []
})

const loadChannels = () => {
  pluginApi.listPlugins()
      .then(res => {
        channels.value = (res.data || [])
            .filter(plugin => plugin.state === 'ENABLED')
            .flatMap(plugin => plugin.channels || [])
      })
      .catch(() => {
      })
}

// 切换渠道时清理上一个渠道遗留的字段值，避免保存无关配置。
watch(() => props.notificationConfig.pluginChannelId, () => {
  const keys = new Set(activeFields.value.map(field => field.key))
  Object.keys(options.value).forEach(key => {
    if (!keys.has(key)) delete options.value[key]
  })
  activeFields.value.forEach(field => {
    if (options.value[field.key] === undefined) options.value[field.key] = ''
  })
})

onMounted(loadChannels)
</script>

<style scoped>
.plugin-channel-select,
.plugin-channel-input {
  width: 100%;
  max-width: 360px;
}
</style>

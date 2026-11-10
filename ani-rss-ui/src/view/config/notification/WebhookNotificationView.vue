<template>
  <template v-if="notificationConfig['notificationType'] === 'CUSTOM_REQUEST'">
    <SettingsItem label="Content-Type">
      <el-input v-model.trim="notificationConfig['webHookContentType']" placeholder="application/json"/>
    </SettingsItem>
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
    <SettingsItem label="Body">
      <el-input v-model="notificationConfig['webHookBody']"
                type="textarea"
                :autosize="{ minRows: 2}"
                placeholder='{"subgroup":"${subgroup}","event":"${text}"}'/>
      <el-alert v-if="isJson && body && !validJson" :closable="false" show-icon type="error"
                title="请求体模板不是有效 JSON。占位符请放在 JSON 字符串值中，例如 {&quot;subgroup&quot;:&quot;${subgroup}&quot;}。"/>
      <el-alert v-else-if="isJson" :closable="false" show-icon type="info"
                title="JSON 中的模板变量会自动转义。可用 title、text、action、episode、subgroup、tmdburl、bgmUrl、downloadPath、image 等字段。"/>
    </SettingsItem>
  </template>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {computed} from "vue";

let props = defineProps(['notificationConfig', 'config'])
const body = computed(() => props.notificationConfig['webHookBody'] || '')
const isJson = computed(() => /application\/json|\+json/i.test(props.notificationConfig['webHookContentType'] || ''))
const validJson = computed(() => {
  try { JSON.parse(body.value); return true } catch { return false }
})
</script>

<template>
  <div class="settings-stack">
    <SettingsCard :title="$t('maintenance')">
      <SettingsItem :label="$t('autobackupsettings')">
        <div class="inline-fields">
          <el-switch v-model="props.config.configBackup"/>
          <el-input-number v-model="props.config.configBackupDay" :min="1">
            <template #suffix><span>{{ $t('days') }}</span></template>
          </el-input-number>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('backup')">
        <div class="inline-fields">
          <el-button bg icon="Upload" @click="exportBackup">{{ $t('exportsettings') }}</el-button>
          <el-button bg icon="Download" @click="importBackup">{{ $t('importsettings') }}</el-button>
          <UploadView ref="uploadRef" url="api/importBackup" :extensions="['zip']" :callback="callback"/>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('cache')">
        <div class="inline-fields">
          <el-button :loading="clearCacheLoading" bg icon="Delete" @click="clearCache">
            {{ $t('clean') }}
          </el-button>
          <el-text size="small" type="info">{{ $t('cleanunusedcachenow') }}</el-text>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('autocleancache')">
        <div class="stack">
          <div class="inline-fields">
            <el-switch v-model="props.config.cacheAutoClear"/>
            <el-input-number v-model="props.config.cacheAutoClearHours" :min="1" :max="720"
                             :disabled="!props.config.cacheAutoClear">
              <template #suffix><span>{{ $t('hours') }}</span></template>
            </el-input-number>
          </div>
          <el-text size="small" type="info">{{ $t('whenenabledunreferencedcoverandpreviewcaches') }}</el-text>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('copyics')">
        <el-button icon="DocumentCopy" bg @click="copyIcs">{{ $t('copyics') }}</el-button>
      </SettingsItem>
      <SettingsItem :label="$t('startonboot')">
        <el-switch v-model="props.config.autoStart"/>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('logginganddebug')">
      <SettingsItem :label="$t('maxlogentries')">
        <el-select v-model="props.config.logsMax" class="settings-select">
          <el-option v-for="it in [128,256,512]" :key="it" :label="it" :value="it"/>
        </el-select>
      </SettingsItem>
      <SettingsItem label="DEBUG">
        <el-switch v-model="props.config.debug"/>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('updates')">
      <SettingsItem :label="$t('autoupdate')">
        <div class="stack">
          <el-switch v-model="props.config.autoUpdate"/>
          <el-text size="small" type="info">{{ $t('updatetheappdailyat0600') }}</el-text>
        </div>
      </SettingsItem>
    </SettingsCard>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {ElMessage, ElMessageBox} from "element-plus"
import {markRaw, ref} from "vue"
import {WarnTriangleFilled} from "@element-plus/icons-vue"
import SettingsItem from "@/view/custom/SettingsItem.vue"
import SettingsCard from "@/view/custom/SettingsCard.vue"
import UploadView from "@/view/custom/UploadView.vue"
import {authorization, getBaseUrl} from "@/js/global.js"
import * as http from "@/js/http.js"

const {t} = useI18n()
const props = defineProps({config: {type: Object, required: true}})

const uploadRef = ref()
const clearCacheLoading = ref(false)

const importBackup = () => {
  ElMessageBox.confirm(
      `<strong style="color: var(--el-color-danger);">`
      + t('thisoverwritesexistingsettingssubscriptionsanddownloadrecords')
      + `</strong>`,
      t('warning'),
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: t('continue'),
        cancelButtonClass: 'is-text is-has-bg',
        cancelButtonText: t('cancel'),
        type: 'warning',
        icon: markRaw(WarnTriangleFilled),
      }
  ).then(() => uploadRef.value.selectAndUpload()).catch(() => {})
}

const callback = res => {
  const {code, message} = res
  if (code !== 200) {
    ElMessage.error(message)
    return
  }
  ElMessage.success(message)
  setTimeout(() => location.reload(), 1000)
}

const exportBackup = () => {
  const element = document.createElement('a')
  element.href = `api/exportBackup?s=${authorization.value}`
  document.body.appendChild(element)
  element.click()
  document.body.removeChild(element)
}

const clearCache = () => {
  clearCacheLoading.value = true
  http.clearCache()
      .then(res => ElMessage.success(res.message))
      .finally(() => { clearCacheLoading.value = false })
}

const copyIcs = () => {
  const url = `${getBaseUrl()}api/calendar.ics?api-key=${props.config.apiKey}`
  const input = document.createElement('input')
  input.value = url
  document.body.appendChild(input)
  input.select()
  document.execCommand('copy')
  document.body.removeChild(input)
  ElMessage.success(t('copied'))
}
</script>

<style scoped>
.stack {
  display: grid;
  gap: 6px;
  width: 100%;
  justify-items: start;
}

.inline-fields {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  flex-wrap: wrap;
}
</style>

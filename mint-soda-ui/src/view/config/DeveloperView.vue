<template>
  <div class="experimental-settings full-width">
    <el-alert class="experimental-alert" :closable="false" show-icon type="warning"
              :title="$t('experimentalfeatureswarning')"/>

    <SettingsItem :label="$t('experimentalfeatures')" :label-width="150">
      <div class="experimental-switch">
        <el-switch v-model="props.config.developerOptionsEnabled"/>
        <el-text size="small" type="info" class="experimental-hint">
          {{ $t('experimentalfeatureshint') }}
        </el-text>
      </div>
    </SettingsItem>

    <template v-if="props.config.developerOptionsEnabled">
      <SettingsItem :label="$t('customtorrenturl')" :label-width="150">
        <el-input v-model.trim="props.config.customTorrentUrl" type="textarea"
                  :autosize="{ minRows: 2, maxRows: 4 }"
                  placeholder="https://torrent.example.com/api/public/<info_hash>"/>
        <el-alert :closable="false" show-icon type="info"
                  :title="$t('onceaninfohashisknownthetorrentisfetchedfrom')"/>
      </SettingsItem>

      <SettingsItem label="User-Agent" :label-width="150">
        <el-input v-model.trim="props.config.customUserAgent" clearable maxlength="256"
                  :placeholder="$t('leaveblanktousemintsodaversion')"/>
        <el-alert :closable="false" show-icon type="info"
                  :title="$t('onlyaffectsrequestsmadebythisappsomesitesvar')"/>
      </SettingsItem>

      <SettingsItem :label="$t('requestlog')" :label-width="150">
        <el-switch v-model="props.config.devHttpTrace"/>
        <el-text size="small" type="info" class="experimental-hint">
          {{ $t('logsthemethodurlstatusanddurationofoutboundr') }}
        </el-text>
      </SettingsItem>

      <SettingsItem :label="$t('diagnostics')" :label-width="150">
        <div class="experimental-actions">
          <el-button bg text icon="Refresh" :loading="checking" @click="checkNetwork">{{ $t('testconnection') }}</el-button>
          <el-button bg text icon="Document" @click="openLogs">{{ $t('viewlogs') }}</el-button>
          <el-button bg text icon="Delete" @click="clearLogs">{{ $t('clearlogs') }}</el-button>
        </div>
        <el-text v-if="networkResult" size="small" :type="networkResult.ok ? 'success' : 'danger'"
                 class="experimental-hint">
          {{ networkResult.message }}
        </el-text>
      </SettingsItem>
    </template>

    <div class="experimental-footer">
      <el-button bg text :icon="RefreshLeft" @click="resetDefaults">
        {{ $t('resetexperimentalsettings') }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {RefreshLeft} from "@element-plus/icons-vue";
import * as http from "@/js/http.js";
const {t} = useI18n()
let props = defineProps(['config'])
const emit = defineEmits(['request-save'])

const checking = ref(false)
const networkResult = ref(null)

const checkNetwork = () => {
  checking.value = true
  networkResult.value = null
  http.devNetworkCheck()
      .then(res => {
        networkResult.value = res.data
      })
      .catch(error => {
        networkResult.value = {ok: false, message: error?.message || t('checkfailed')}
      })
      .finally(() => { checking.value = false })
}

const openLogs = () => {
  const base = window.location.href.split('#')[0]
  window.open(`${base}#/logs`, '_blank')
}

const clearLogs = () => {
  ElMessageBox.confirm(t('cleartheinmemorylogbufferlogfilesondiskareke'), t('clearlogs'),
      {type: 'warning', confirmButtonText: t('clear'), cancelButtonText: t('cancel')})
      .then(() => http.clearLogs())
      .then(res => ElMessage.success(res.message || t('cleared')))
      .catch(() => {})
}

// 一键回到出厂值：重新开启实验性功能，并清空自定义地址、UA 与请求日志。
// 与其他设置一样写入编辑副本，随后立即触发保存。
const resetDefaults = () => {
  ElMessageBox.confirm(t('resetexperimentalsettingsconfirm'), t('resetexperimentalsettings'), {
    confirmButtonText: t('resetexperimentalsettings'),
    cancelButtonText: t('cancel'),
    type: 'warning'
  })
      .then(() => {
        props.config.developerOptionsEnabled = true
        props.config.customTorrentUrl = ''
        props.config.customUserAgent = ''
        props.config.devHttpTrace = false
        ElMessage.success(t('experimentalsettingsreset'))
        emit('request-save')
      })
      .catch(() => {})
}
</script>

<style scoped>
.experimental-alert {
  margin-bottom: 18px;
}

.experimental-switch {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.experimental-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.experimental-hint {
  display: block;
  margin-top: 6px;
}

.experimental-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>

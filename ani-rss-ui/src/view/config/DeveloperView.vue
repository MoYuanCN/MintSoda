<template>
  <div class="developer-settings full-width">
    <el-alert class="developer-alert" :closable="false" show-icon type="warning"
              :title="$t('developeroptionsareadvancedverifyyouraccount')"/>
    <SettingsItem :label="$t('status')">
      <div class="developer-status">
        <el-tag :type="enabled ? 'success' : 'info'" effect="plain">
          {{ enabled ? $t('enabled') : $t('disabled') }}
        </el-tag>
        <el-button v-if="!enabled" type="primary" bg text @click="openUnlock">{{ $t('enabledeveloperoptions') }}</el-button>
        <el-button v-else type="danger" bg text @click="disable">{{ $t('disabledeveloperoptions') }}</el-button>
      </div>
    </SettingsItem>
    <template v-if="enabled">
      <SettingsItem :label="$t('customtorrenturl')">
        <el-input v-model.trim="props.config.customTorrentUrl" type="textarea"
                  :autosize="{ minRows: 2, maxRows: 4 }"
                  placeholder="https://torrent.example.com/api/public/<info_hash>"/>
        <el-alert :closable="false" show-icon type="info"
                  :title="$t('onceaninfohashisknownthetorrentisfetchedfrom')"/>
      </SettingsItem>
      <SettingsItem label="User-Agent">
        <el-input v-model.trim="props.config.customUserAgent" clearable maxlength="256"
                  :placeholder="$t('leaveblanktouseanirssyuriversion')"/>
        <el-alert :closable="false" show-icon type="info"
                  :title="$t('onlyaffectsrequestsmadebythisappsomesitesvar')"/>
      </SettingsItem>
      <SettingsItem :label="$t('requestlog')">
        <el-switch v-model="props.config.devHttpTrace"/>
        <el-text size="small" type="info" class="developer-hint">
          {{ $t('logsthemethodurlstatusanddurationofoutboundr') }}
        </el-text>
      </SettingsItem>
      <SettingsItem :label="$t('diagnostics')">
        <div class="developer-actions">
          <el-button bg text icon="Refresh" :loading="checking" @click="checkNetwork">{{ $t('testconnection') }}</el-button>
          <el-button bg text icon="Document" @click="openLogs">{{ $t('viewlogs') }}</el-button>
          <el-button bg text icon="Delete" @click="clearLogs">{{ $t('clearlogs') }}</el-button>
        </div>
        <el-text v-if="networkResult" size="small" :type="networkResult.ok ? 'success' : 'danger'"
                 class="developer-hint">
          {{ networkResult.message }}
        </el-text>
      </SettingsItem>
      <SettingsItem :label="$t('backupcontents')">
        <el-text size="small" type="info">
          {{ $t('exportedbackupsincludepluginsandtheirprivate') }}
        </el-text>
      </SettingsItem>
    </template>

    <el-dialog v-model="dialogVisible" center :title="$t('enabledeveloperoptions')" width="420">
      <el-alert class="developer-alert" :closable="false" show-icon type="info"
                :title="$t('enteryourcurrentusernameandpasswordtoconfirm')"/>
      <el-form label-position="top" @submit.prevent @keyup.enter="unlock">
        <el-form-item :label="$t('username')">
          <el-input v-model.trim="credentials.username" autocomplete="username"/>
        </el-form-item>
        <el-form-item :label="$t('password')">
          <el-input v-model="credentials.password" type="password" show-password autocomplete="current-password"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button bg text @click="dialogVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="loading" @click="unlock">{{ $t('enable') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {computed, reactive, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import * as http from "@/js/http.js";
const {t} = useI18n()
let props = defineProps(['config'])

const enabled = computed(() => Boolean(props.config.developerOptionsEnabled))
const dialogVisible = ref(false)
const loading = ref(false)
const checking = ref(false)
const networkResult = ref(null)
const credentials = reactive({username: '', password: ''})

const openUnlock = () => {
  credentials.username = ''
  credentials.password = ''
  dialogVisible.value = true
}

const settings = () => ({
  username: credentials.username,
  password: credentials.password,
  customTorrentUrl: props.config.customTorrentUrl || '',
  customUserAgent: props.config.customUserAgent || '',
  devHttpTrace: Boolean(props.config.devHttpTrace)
})

const unlock = () => {
  if (!credentials.username || !credentials.password) {
    ElMessage.error(t('enteryourcurrentusernameandpassword'))
    return
  }
  loading.value = true
  http.verifyDeveloperCredentials(credentials)
      .then(() => http.setDeveloperSettings(settings()))
      .then(res => {
        props.config.developerOptionsEnabled = true
        dialogVisible.value = false
        credentials.password = ''
        ElMessage.success(res.message || t('developeroptionsenabled'))
      })
      .catch(error => {
        if (error !== 'cancel' && error !== 'close') {
          ElMessage.error(error?.message || t('failedtoenabledeveloperoptions'))
        }
      })
      .finally(() => { loading.value = false })
}

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

// 与其他设置一样只改内存状态，点击底部“保存设置”后才会关闭并失效。
const disable = () => {
  ElMessageBox.confirm(t('customtorrenturlandrequestloggingwillstopwor'), t('disabledeveloperoptions'), {
    confirmButtonText: t('close'),
    cancelButtonText: t('cancel'),
    type: 'warning'
  })
      .then(() => {
        props.config.developerOptionsEnabled = false
        ElMessage.success(t('developeroptionsdisabledsaveyoursettings'))
      })
      .catch(() => {})
}
</script>

<style scoped>
.developer-alert {
  margin-bottom: 12px;
}

.developer-status {
  display: flex;
  align-items: center;
  gap: 12px;
}

.developer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.developer-hint {
  display: block;
  margin-top: 6px;
}
</style>

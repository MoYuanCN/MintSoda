<template>
  <div class="developer-settings full-width">
    <el-alert class="developer-alert" :closable="false" show-icon type="warning"
              title="开发者选项属于高级功能，开启前需要验证当前登录账号，关闭后其中的设置会被保留但不再生效。"/>
    <SettingsItem label="状态">
      <div class="developer-status">
        <el-tag :type="enabled ? 'success' : 'info'" effect="plain">
          {{ enabled ? '已开启' : '已关闭' }}
        </el-tag>
        <el-button v-if="!enabled" type="primary" bg text @click="openUnlock">开启开发者选项</el-button>
        <el-button v-else type="danger" bg text @click="disable">关闭开发者选项</el-button>
      </div>
    </SettingsItem>
    <template v-if="enabled">
      <SettingsItem label="自定义种子地址">
        <el-input v-model.trim="props.config.customTorrentUrl" type="textarea"
                  :autosize="{ minRows: 2, maxRows: 4 }"
                  placeholder="https://torrent.example.com/api/public/<info_hash>"/>
        <el-alert :closable="false" show-icon type="info"
                  title="读取到 info_hash 后会请求此地址获取种子。支持 <info_hash> 或 ${info_hash}；未写占位符时会自动追加哈希。留空则使用来源站点的种子地址。"/>
      </SettingsItem>
      <SettingsItem label="User-Agent">
        <el-input v-model.trim="props.config.customUserAgent" clearable maxlength="256"
                  placeholder="留空使用内置标识 ani-rss-Yuri/<版本>"/>
        <el-alert :closable="false" show-icon type="info"
                  title="仅影响本应用发起的请求；部分站点会按 UA 返回不同内容，排查兼容性时可临时修改。"/>
      </SettingsItem>
      <SettingsItem label="请求日志">
        <el-switch v-model="props.config.devHttpTrace"/>
        <el-text size="small" type="info" class="developer-hint">
          记录出站请求的方法、地址、状态码与耗时，不记录请求头与请求体。
        </el-text>
      </SettingsItem>
      <SettingsItem label="诊断">
        <div class="developer-actions">
          <el-button bg text icon="Refresh" :loading="checking" @click="checkNetwork">检测网络</el-button>
          <el-button bg text icon="Document" @click="openLogs">查看日志</el-button>
          <el-button bg text icon="Delete" @click="clearLogs">清空日志</el-button>
        </div>
        <el-text v-if="networkResult" size="small" :type="networkResult.ok ? 'success' : 'danger'"
                 class="developer-hint">
          {{ networkResult.message }}
        </el-text>
      </SettingsItem>
      <SettingsItem label="备份内容">
        <el-text size="small" type="info">
          导出的备份同时包含插件与插件私有数据；导入会整体替换插件目录，安装记录会按目录重建。
        </el-text>
      </SettingsItem>
    </template>

    <el-dialog v-model="dialogVisible" center title="开启开发者选项" width="420">
      <el-alert class="developer-alert" :closable="false" show-icon type="info"
                title="请输入当前登录账号与密码以确认身份。"/>
      <el-form label-position="top" @submit.prevent @keyup.enter="unlock">
        <el-form-item label="用户名">
          <el-input v-model.trim="credentials.username" autocomplete="username"/>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="credentials.password" type="password" show-password autocomplete="current-password"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button bg text @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="unlock">确认开启</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {computed, reactive, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import * as http from "@/js/http.js";

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
    ElMessage.error('请输入当前登录账号和密码')
    return
  }
  loading.value = true
  http.verifyDeveloperCredentials(credentials)
      .then(() => http.setDeveloperSettings(settings()))
      .then(res => {
        props.config.developerOptionsEnabled = true
        dialogVisible.value = false
        credentials.password = ''
        ElMessage.success(res.message || '开发者选项已开启')
      })
      .catch(error => {
        if (error !== 'cancel' && error !== 'close') {
          ElMessage.error(error?.message || '开启开发者选项失败')
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
        networkResult.value = {ok: false, message: error?.message || '检测失败'}
      })
      .finally(() => { checking.value = false })
}

const openLogs = () => {
  const base = window.location.href.split('#')[0]
  window.open(`${base}#/logs`, '_blank')
}

const clearLogs = () => {
  ElMessageBox.confirm('清空当前会话的日志缓存？磁盘日志不会被删除。', '清空日志',
      {type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消'})
      .then(() => http.clearLogs())
      .then(res => ElMessage.success(res.message || '已清空'))
      .catch(() => {})
}

// 与其他设置一样只改内存状态，点击底部“保存设置”后才会关闭并失效。
const disable = () => {
  ElMessageBox.confirm('关闭后自定义种子地址与请求日志将不再生效，保存设置后生效，是否继续？', '关闭开发者选项', {
    confirmButtonText: '关闭',
    cancelButtonText: '取消',
    type: 'warning'
  })
      .then(() => {
        props.config.developerOptionsEnabled = false
        ElMessage.success('已关闭开发者选项，请保存设置')
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

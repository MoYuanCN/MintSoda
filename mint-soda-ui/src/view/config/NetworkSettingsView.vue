<template>
  <div class="settings-stack">
    <SettingsCard :title="$t('proxysettings')">
      <SettingsItem :label="$t('enable-3')">
        <el-switch v-model="props.config.proxy"/>
      </SettingsItem>
      <SettingsItem label="IP">
        <el-input v-model.trim="props.config.proxyHost" :disabled="!props.config.proxy"
                  placeholder="192.168.0.x" class="settings-wide"/>
      </SettingsItem>
      <SettingsItem :label="$t('port')">
        <el-input-number v-model="props.config.proxyPort" :disabled="!props.config.proxy"
                         :min="1" :max="65535"/>
      </SettingsItem>
      <SettingsItem :label="$t('username')">
        <el-input v-model.trim="props.config.proxyUsername" :disabled="!props.config.proxy"
                  :placeholder="$t('optional')" class="settings-wide"/>
      </SettingsItem>
      <SettingsItem :label="$t('password')">
        <el-input v-model="props.config.proxyPassword" :disabled="!props.config.proxy"
                  :placeholder="$t('optional')" show-password class="settings-wide"/>
      </SettingsItem>
      <SettingsItem :label="$t('proxylist')">
        <el-input class="settings-wide" type="textarea" :autosize="{ minRows: 3, maxRows: 6}"
                  v-model="props.config.proxyList" :disabled="!props.config.proxy"/>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('testproxy')">
      <SettingsItem :label="$t('testproxy')">
        <div class="proxy-test">
          <div class="proxy-test-controls">
            <el-select v-model="url" class="proxy-test-select" filterable allow-create
                       default-first-option clearable :placeholder="$t('testurl')" :disabled="testLoading">
              <el-option v-for="item in urls" :key="item" :value="item" :label="formatUrlLabel(item)">
                <div class="proxy-test-option">
                  <span>{{ formatUrlLabel(item) }}</span>
                  <el-text truncated size="small" type="info">{{ item }}</el-text>
                </div>
              </el-option>
            </el-select>
            <el-button bg text type="primary" :disabled="!url" :loading="testLoading"
                       @click="test" icon="Odometer">{{ $t('test') }}</el-button>
          </div>
          <div v-if="resultVisible" class="proxy-test-result"
               :class="{'is-success': resultOk, 'is-error': !resultOk}">
            <div class="proxy-test-result-main">
              <el-icon class="proxy-test-result-icon">
                <CircleCheck v-if="resultOk"/>
                <Warning v-else/>
              </el-icon>
              <div class="proxy-test-result-content">
                <div class="proxy-test-result-title">{{ resultTitle }}</div>
                <el-text class="proxy-test-result-url" truncated size="small" type="info">
                  {{ testedUrl }}
                </el-text>
              </div>
            </div>
            <div class="proxy-test-metrics">
              <el-tag :type="statusTagType" size="small">{{ statusLabel }}</el-tag>
              <el-tag type="info" size="small">{{ timeLabel }}</el-tag>
            </div>
          </div>
        </div>
      </SettingsItem>
    </SettingsCard>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed, onMounted, ref} from "vue"
import {ElMessage} from "element-plus"
import {CircleCheck, Warning} from "@element-plus/icons-vue"
import {testProxy} from "@/js/http.js"
import {base64Encode} from "@/js/global.js"
import SettingsItem from "@/view/custom/SettingsItem.vue"
import SettingsCard from "@/view/custom/SettingsCard.vue"

const {t} = useI18n()
const props = defineProps({config: {type: Object, required: true}})

const urls = ref([
  'https://mikanani.me',
  'https://mikanime.tv',
  'https://nyaa.si',
  'https://acg.rip',
  'https://github.com',
  'https://www.google.com',
  'https://bgm.tv',
  'https://www.themoviedb.org'
])
const url = ref('')
const testResult = ref(null)
const testError = ref('')
const testedUrl = ref('')
const testLoading = ref(false)

onMounted(() => { url.value = urls.value[0] })

const resultVisible = computed(() => Boolean(testResult.value || testError.value))
const resultStatus = computed(() => Number(testResult.value?.status || 0))
const hasStatus = computed(() => resultStatus.value > 0)
const resultOk = computed(() => !testError.value && hasStatus.value && resultStatus.value < 400)
const statusTagType = computed(() => resultOk.value ? 'success' : 'danger')
const resultTitle = computed(() => {
  if (testError.value) return testError.value
  if (!hasStatus.value) return t('connectionfailed')
  return testResult.value?.title || (resultOk.value ? t('connected') : t('unexpectedresponse'))
})
const statusLabel = computed(() => hasStatus.value ? `HTTP ${resultStatus.value}` : t('nostatus'))
const timeLabel = computed(() => {
  const time = testResult.value?.time
  return Number.isFinite(Number(time)) ? `${time} ms` : '-- ms'
})

const formatUrlLabel = value => {
  try {
    return new URL(normalizeUrl(value)).hostname
  } catch {
    return value
  }
}

const normalizeUrl = value => {
  const text = (value || '').trim()
  if (!text) return ''
  return /^https?:\/\//i.test(text) ? text : `https://${text}`
}

const test = () => {
  const target = normalizeUrl(url.value)
  if (!target) return
  testLoading.value = true
  testResult.value = null
  testError.value = ''
  url.value = target
  testedUrl.value = target
  testProxy(base64Encode(target), props.config)
      .then(res => {
        testResult.value = res.data || {}
        if (resultOk.value) {
          ElMessage.success(t('testsuccessful'))
          return
        }
        ElMessage.warning(t('testerror'))
      })
      .catch(error => {
        testError.value = error?.message || t('testfailed')
      })
      .finally(() => {
        testLoading.value = false
      })
}
</script>

<style scoped>
.proxy-test {
  display: grid;
  gap: 10px;
  width: 100%;
}

.proxy-test-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.proxy-test-select {
  flex: 1 1 240px;
  max-width: 360px;
}

.proxy-test-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.proxy-test-result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.proxy-test-result.is-success {
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
}

.proxy-test-result.is-error {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
}

.proxy-test-result-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.proxy-test-result-icon {
  flex-shrink: 0;
  font-size: 18px;
}

.is-success .proxy-test-result-icon {
  color: var(--el-color-success);
}

.is-error .proxy-test-result-icon {
  color: var(--el-color-danger);
}

.proxy-test-result-content {
  min-width: 0;
}

.proxy-test-result-title {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.proxy-test-result-url {
  display: block;
}

.proxy-test-metrics {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
</style>

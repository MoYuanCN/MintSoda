<template>
  <div class="settings-stack">
    <SettingsCard :title="$t('accountsecurity')">
      <SettingsItem :label="$t('username')">
        <el-input v-model="props.config.login.username" autocomplete="new-password"
                  class="settings-wide"/>
      </SettingsItem>
      <SettingsItem :label="$t('password')">
        <div class="stack">
          <el-input v-model="props.config.login.password" type="password" show-password
                    :placeholder="$t('leaveblanktokeepthecurrentpassword')"
                    autocomplete="new-password" class="settings-wide"/>
          <el-text size="small" type="info">
            {{ $t('changingtheusernameorpasswordsignsoutallsess') }}
          </el-text>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('sessionvalidity')">
        <el-input-number v-model="props.config.loginEffectiveHours" :min="0">
          <template #suffix><span>{{ $t('hours') }}</span></template>
        </el-input-number>
      </SettingsItem>
      <SettingsItem :label="$t('other')">
        <div class="stack">
          <el-checkbox v-model="props.config.multiLoginForbidden" :label="$t('forbidconcurrentsessions')"/>
          <el-checkbox v-model="props.config.verifyLoginIp" :label="$t('invalidatesessioniftheipchanges')"/>
          <el-checkbox v-model="props.config.limitLoginAttempts" :label="$t('limitloginattempts')"/>
        </div>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('accesscontrol')">
      <SettingsItem :label="$t('other')">
        <div class="stack">
          <el-checkbox v-model="props.config.innerIP" :label="$t('blockpublicaccess')"/>
          <el-checkbox v-model="props.config.allowCors" :label="$t('allowcors')"/>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('ipallowlist')">
        <div class="stack">
          <el-switch v-model="props.config.ipWhitelist"/>
          <el-input class="settings-wide" type="textarea" :autosize="{ minRows: 2, maxRows: 6}"
                    :disabled="!props.config.ipWhitelist"
                    placeholder="127.0.0.1&#10;192.168.1.0/24"
                    v-model="props.config.ipWhitelistStr"/>
          <el-text size="small" type="info">{{ $t('skipauthenticationfortheseipsoneperline') }}</el-text>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('trustedreverseproxyips')">
        <div class="stack">
          <el-checkbox v-model="props.config.reverseProxyTrustIpListEnabled" :label="$t('enable-3')"/>
          <el-input-tag v-model="props.config.reverseProxyTrustIpList" class="settings-wide"/>
        </div>
      </SettingsItem>
      <SettingsItem label="Api Key">
        <div class="inline-fields">
          <el-input v-model="props.config.apiKey" readonly/>
          <el-button bg text @click="createApiKey">{{ $t('generate') }}</el-button>
          <el-button bg text @click="copy(props.config.apiKey)">{{ $t('copy') }}</el-button>
        </div>
      </SettingsItem>
    </SettingsCard>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {ElMessage} from "element-plus"
import SettingsItem from "@/view/custom/SettingsItem.vue"
import SettingsCard from "@/view/custom/SettingsCard.vue"

const {t} = useI18n()
const props = defineProps({config: {type: Object, required: true}})

const generateRandomString = length => {
  const charset = 'abcdefghijklmnopqrstuvwxyz0123456789'
  let value = ''
  for (let i = 0; i < length; i++) {
    value += charset[Math.floor(Math.random() * charset.length)]
  }
  return value
}

const createApiKey = () => {
  props.config.apiKey = generateRandomString(64)
}

const copy = value => {
  const input = document.createElement('input')
  input.value = value
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

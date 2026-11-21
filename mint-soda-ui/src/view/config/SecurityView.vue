<template>
  <SettingsItem :label="$t('username')">
    <el-input v-model:model-value="props.config.login.username" autocomplete="new-password">
      <template #prefix>
        <el-icon class="el-input__icon">
          <User/>
        </el-icon>
      </template>
    </el-input>
  </SettingsItem>
  <SettingsItem :label="$t('password')">
    <div class="full-width">
      <el-input v-model:model-value="props.config.login.password" type="password" show-password
                :placeholder="$t('leaveblanktokeepthecurrentpassword')" autocomplete="new-password">
        <template #prefix>
          <el-icon class="el-input__icon">
            <Key/>
          </el-icon>
        </template>
      </el-input>
      <el-text class="mx-1" size="small">
        {{ $t('changingtheusernameorpasswordsignsoutallsess') }}
      </el-text>
      <br>
      <el-text class="mx-1" size="small" type="info">
        {{ $t('usernamemustbe332lettersdigitsunderscoresorh-2') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('sessionvalidity')">
    <el-input-number v-model:model-value="props.config.loginEffectiveHours" :min="0">
      <template #suffix>
        <span>{{ $t('hours') }}</span>
      </template>
    </el-input-number>
  </SettingsItem>
  <SettingsItem :label="$t('other')">
    <el-checkbox v-model="props.config['multiLoginForbidden']" :label="$t('forbidconcurrentsessions')"/>
    <el-checkbox v-model="props.config.innerIP" :label="$t('blockpublicaccess')"/>
    <el-checkbox v-model="props.config.verifyLoginIp" :label="$t('invalidatesessioniftheipchanges')"/>
    <el-checkbox v-model="props.config.limitLoginAttempts" :label="$t('limitloginattempts')"/>
    <el-checkbox v-model="props.config.allowCors" :label="$t('allowcors')"/>
  </SettingsItem>
  <SettingsItem :label="$t('ipallowlist')">
    <div class="full-width">
      <div>
        <el-switch v-model:model-value="config['ipWhitelist']"/>
      </div>
      <div class="full-width">
        <el-input class="full-width" type="textarea"
                  :autosize="{ minRows: 2}"
                  :disabled="!config['ipWhitelist']"
                  :placeholder="'127.0.0.1\n192.168.1.0/24'" v-model:model-value="config['ipWhitelistStr']"/>
        <br>
        <el-text class="mx-1" size="small">
          {{ $t('skipauthenticationfortheseipsoneperline') }}
        </el-text>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('trustedreverseproxyips')">
    <div class="full-width">
      <el-checkbox :label="$t('enable-3')" v-model="config.reverseProxyTrustIpListEnabled"/>
      <br>
      <el-input-tag v-model="config.reverseProxyTrustIpList"/>
    </div>
  </SettingsItem>
  <SettingsItem label="Api Key">
    <div class="flex full-width">
      <el-input v-model:model-value="props.config.apiKey" readonly/>
      <div class="login-api-key-buttons flex">
        <el-button bg text @click="createApiKey">{{ $t('generate') }}</el-button>
        <el-button bg text @click="copy(props.config.apiKey)">{{ $t('copy') }}</el-button>
      </div>
    </div>
  </SettingsItem>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ElMessage, ElText} from "element-plus";
import {Key, User} from "@element-plus/icons-vue";
const {t} = useI18n()
let generateRandomString = (length) => {
  const charset = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let randomString = '';
  for (let i = 0; i < length; i++) {
    const randomIndex = Math.floor(Math.random() * charset.length);
    randomString += charset[randomIndex];
  }
  return randomString;
}

let createApiKey = () => {
  props.config.apiKey = generateRandomString(64);
}

let copy = (v) => {
  const input = document.createElement('input');
  input.value = v
  document.body.appendChild(input);
  input.select();
  document.execCommand('copy');
  document.body.removeChild(input);
  ElMessage.success(t('copied'))
}

let props = defineProps(['config'])
</script>

<style scoped>
.login-api-key-buttons {
  margin-left: 12px;
}
</style>

<template>
  <BangumiMeView ref="bangumiMeRef"/>
  <SettingsItem label="BgmApi">
    <el-input v-model:model-value="props.config['bgmApi']" placeholder="https://api.bgm.tv"/>
  </SettingsItem>
  <SettingsItem :label="$t('fetchmethod')">
    <el-radio-group v-model="props.config['bgmTokenType']">
      <el-radio :label="$t('entermanually')" value="INPUT"/>
      <el-radio :label="$t('fetchautomatically')" value="AUTO"/>
    </el-radio-group>
  </SettingsItem>
  <template v-if="props.config['bgmTokenType'] === 'INPUT'">
    <SettingsItem label="Token">
      <div class="full-width">
        <el-input
            v-model="props.config.bgmToken"
            placeholder="ABCDEFGHIJKLMNOPQRS"
        />
        <div>
          <el-text class="mx-1" size="small">
            {{ $t('youcan') }}&nbsp;
            <el-link
                type="primary"
                class="text-extra-small"
                href="https://next.bgm.tv/demo/access-token"
                target="_blank">
              https://next.bgm.tv/demo/access-token
            </el-link>
            &nbsp;{{ $t('generateanaccesstoken') }}
          </el-text>
        </div>
      </div>
    </SettingsItem>
  </template>
  <template v-if="props.config['bgmTokenType'] === 'AUTO'">
    <SettingsItem label="App ID">
      <el-input
          v-model="props.config['bgmAppID']"
          placeholder="bgm123456789"
      />
    </SettingsItem>
    <SettingsItem label="App Secret">
      <el-input
          v-model="props.config['bgmAppSecret']"
          placeholder="abcdefghijklm"
      />
    </SettingsItem>
        <SettingsItem :label="$t('callbackurl')">
      <div class="full-width">
        <el-input v-model="props.config['bgmRedirectUri']" :placeholder="defaultRedirectUri"/>
        <el-button bg icon="Refresh"
                   class="mt-6"
                   @click="setRedirectUri"/>
      </div>
    </SettingsItem>
    <div class="flex justify-space-between">
      <el-text class="mx-1" size="small">
        {{ $t('automaticfetchenablestokenrefresh') }}
        <br>
        {{ $t('goto') }}&nbsp;
        <el-link
            class="text-extra-small"
            type="primary"
            target="_blank"
            href="https://bgm.tv/dev/app">
          {{ $t('bangumideveloperplatform') }}
        </el-link>
        &nbsp;{{ $t('createyourownapp') }}
      </el-text>
      <div>
        <el-button bg
                   type="primary"
                   :disabled="!props.config['bgmAppSecret'] || !props.config['bgmAppID']"
                   @click="start"
                   :loading="loading"
        >
          {{ $t('authorize') }}
        </el-button>
        <el-button
            bg
            type="success"
            @click="bangumiMeRef?.show">
          {{ $t('viewauthorization') }}
        </el-button>
      </div>
    </div>
  </template>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ElMessageBox, ElText} from "element-plus";
import BangumiMeView from "./BangumiMeView.vue";
import {computed, ref} from "vue";
import {setConfig} from "@/js/http.js";
import {getBaseUrl} from "@/js/global.js";
const {t} = useI18n()
let bangumiMeRef = ref()

let props = defineProps(['config'])

const defaultRedirectUri = computed(() => `${getBaseUrl()}bgm-oauth-callback`)

let setRedirectUri = () => {
  props.config['bgmRedirectUri'] = defaultRedirectUri.value
}

let loading = ref(false);

let start = async () => {
  try {
    await ElMessageBox.confirm(
        t('bangumiauthorizationneedsthispagesappsetting'),
        t('authorize'),
        {confirmButtonText: t('saveandcontinue'), cancelButtonText: t('cancel'), type: 'info'}
    )
  } catch {
    return
  }
  loading.value = true;
  if (!props.config['bgmRedirectUri']) {
    setRedirectUri()
  }
  setConfig(props.config)
      .then(async res => {
        let redirect = window.encodeURI(props.config['bgmRedirectUri'])
        let url = `https://bgm.tv/oauth/authorize?client_id=${props.config['bgmAppID']}&response_type=code&redirect_uri=${redirect}`
        window.open(url)
        location.reload()
      })
      .finally(() => {
        loading.value = false;
      })
}

</script>

<style scoped>
.text-extra-small {
  font-size: var(--el-font-size-extra-small);
}

.mt-6 {
  margin-top: 6px;
}

.justify-space-between {
  justify-content: space-between;
}

.justify-start {
  justify-content: start;
}
</style>

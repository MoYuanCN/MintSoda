<template>
  <div class="flex-center about-container">
    <div class="flex about-header">
      <img alt="MintSoda" height="80" :src="appLogo" width="80" @click="developerTap"/>
      <div>
        <h1>MintSoda</h1>
        <el-tooltip
            :content="`${props.config.gitInfo.branch} ${props.config.gitInfo.shortCommitId}`"
            placement="right">
          <el-text class="mx-1 cursor-pointer" size="small" role="button" tabindex="0"
                    :title="$t('tapseventimestoopendeveloperoptions')" @click.stop="developerTap"
                    @keydown.enter.stop="developerTap" @keydown.space.prevent.stop="developerTap">
            &nbsp;v{{ props.config.version }}
          </el-text>
        </el-tooltip>
      </div>
    </div>
    <div class="flex button-list">
      <el-button :icon="Github" bg text type="info" @click="openUrl('https://github.com/MoYuanCN/MintSoda')">
        GitHub
      </el-button>
    </div>
    <div v-loading.fullscreen.lock="actionLoading" class="button-list">
      <PopconfirmView :title="$t('signout')" @confirm="logout">
        <template #reference>
          <el-button type="danger" bg text icon="Back">
            {{ $t('signout-2') }}
          </el-button>
        </template>
      </PopconfirmView>
      <PopconfirmView :title="$t('restart')" @confirm="stop(0)">
        <template #reference>
          <el-button bg icon="RefreshRight" text type="warning">{{ $t('restart-2') }}</el-button>
        </template>
      </PopconfirmView>
      <PopconfirmView :title="$t('shutdown')" @confirm="stop(1)">
        <template #reference>
          <el-button bg icon="SwitchButton" text type="danger">{{ $t('close') }}</el-button>
        </template>
      </PopconfirmView>
      <el-badge :hidden="!about.update" class="item" value="new">
        <el-button :loading="about.version.length < 1" bg icon="Top" text type="success" @click="dialogVisible = true">
          {{ $t('update') }}
        </el-button>
      </el-badge>
    </div>
  </div>
  <el-dialog v-model="developerDialogVisible" center :title="$t('enabledeveloperoptions')" width="520">
    <el-form label-position="top" @submit.prevent>
      <el-form-item :label="$t('account')">
        <el-input v-model="developerCredentials.username" autocomplete="username"/>
      </el-form-item>
      <el-form-item :label="$t('loginpassword')">
        <el-input v-model="developerCredentials.password" type="password" show-password
                  autocomplete="current-password" @keyup.enter="unlockDeveloperMode"/>
      </el-form-item>
      <div class="developer-dialog-actions">
        <el-button type="primary" :loading="developerLoading" @click="unlockDeveloperMode">{{ $t('verifyandenable') }}</el-button>
      </div>
    </el-form>
  </el-dialog>
  <el-dialog v-if="dialogVisible" v-model="dialogVisible" align-center center :title="$t('versionupdate')"
             class="about-dialog">
    <div v-if="about.update">
      <div>
        <SettingsItem :label="$t('version')">
          <el-link type="default" :href="`https://github.com/MoYuanCN/MintSoda/releases/tag/v${about.latest}`"
                   target="_blank">
            {{ about.latest }}
          </el-link>
        </SettingsItem>
        <SettingsItem :label="$t('publishedat')">
          {{ about.date }}
        </SettingsItem>
        <SettingsItem :label="$t('size')">
          {{ about['formatSize'] }}
        </SettingsItem>
        <SettingsItem :label="$t('releasenotes')">
          <el-scrollbar class="about-scrollbar" :always="true">
            <div class="markdown-body about-markdown" v-html="md.render(about.markdownBody)"></div>
            <el-alert
                show-icon
                :closable="false"
                class="about-alert"
                :title="$t('updatesrelyongithubandrequirenetworkaccess')"
                type="info"
            />
          </el-scrollbar>
        </SettingsItem>
      </div>
    </div>
    <div v-else>
      <el-empty :description="$t('noupdates')"></el-empty>
    </div>
    <div class="flex about-dialog-footer">
      <el-button bg text icon="Tickets"
                 @click="openUrl('https://github.com/MoYuanCN/MintSoda/releases')"
                 type="primary">
        {{ $t('updatehistory') }}
      </el-button>
      <div>
        <el-button :disabled="!about.update" bg text icon="Check"
                   type="success" @click="update">
          {{ $t('startupdate') }}
        </el-button>
        <el-button bg icon="Close" text @click="dialogVisible = false">{{ $t('cancel') }}</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {computed, onMounted, ref} from "vue";
import {ElMessage, ElMessageBox, ElText} from "element-plus";
import PopconfirmView from "@/view/custom/PopconfirmView.vue";
import {Github} from "@vicons/fa";

import markdownit from 'markdown-it'
import MarkdownItGitHubAlerts from 'markdown-it-github-alerts'
import 'markdown-it-github-alerts/styles/github-colors-light.css'
import 'markdown-it-github-alerts/styles/github-colors-dark-media.css'
import 'markdown-it-github-alerts/styles/github-base.css'

import {authorization} from "@/js/global.js";
import * as http from "@/js/http.js";
import {uiCustomization, uiAssetUrl} from "@/js/global.js";
const {t} = useI18n()
let md = markdownit({
  html: true,
  linkify: true
})

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  token.attrSet('target', '_blank') // 强制添加属性
  return self.renderToken(tokens, idx, options)
}

md.use(MarkdownItGitHubAlerts)

const actionLoading = ref(false)
const appLogo = computed(() => uiCustomization.value.appLogoImage
    ? uiAssetUrl(uiCustomization.value.appLogoImage)
    : '/icon.png')
const developerDialogVisible = ref(false)
const developerLoading = ref(false)
const developerCredentials = ref({username: '', password: ''})
let developerTapCount = 0
let developerTapTimer

const developerTap = () => {
  developerTapCount++
  clearTimeout(developerTapTimer)
  if (developerTapCount >= 7) {
    developerTapCount = 0
    developerDialogVisible.value = true
    return
  }
  developerTapTimer = setTimeout(() => developerTapCount = 0, 3000)
}

const unlockDeveloperMode = () => {
  const credentials = {
    username: (developerCredentials.value.username || '').trim(),
    password: developerCredentials.value.password || ''
  }
  if (!credentials.username || !credentials.password) {
    ElMessage.error(t('enteryourcurrentusernameandpassword'))
    return
  }
  developerLoading.value = true
  http.verifyDeveloperCredentials(credentials)
      .then(() => ElMessageBox.confirm(
          t('credentialsverifiedenablingdeveloperoptionsr'),
          t('enabledeveloperoptions-2'),
          {confirmButtonText: t('enableandreload'), cancelButtonText: t('cancel'), type: 'warning'}
      ))
      .then(() => http.setDeveloperSettings({
        username: credentials.username,
        password: credentials.password,
        customTorrentUrl: props.config.customTorrentUrl || ''
      }))
      .then(res => {
        ElMessage.success(res.message || t('developeroptionsenabledthepagewillreload'))
        developerDialogVisible.value = false
        developerCredentials.value = {username: '', password: ''}
        window.$skipUnsavedGuard = true
        setTimeout(() => window.location.reload(), 500)
      })
      .catch(error => {
        if (error !== 'cancel' && error !== 'close') {
          ElMessage.error(error?.message || t('failedtoenabledeveloperoptions'))
        }
      })
      .finally(() => { developerLoading.value = false })
}

const stop = (status) => {
  actionLoading.value = true
  http.stop(status)
      .then(res => {
        ElMessage.success(res.message)
        setTimeout(() => {
          authorization.value = ''
          location.reload()
        }, 5000)
      })
      .finally(() => {
        actionLoading.value = false
      })
}

const update = async () => {
  let sleep = ms => {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  actionLoading.value = true
  http.update()
      .then(async res => {
        ElMessage.success(res.message)
        for (let i = 0; i < 24; i++) {
          await sleep(5000)
          try {
            let pingRes = await http.ping()
            if (pingRes.code === 200) {
              authorization.value = ''
              location.reload()
              return
            }
          } catch (e) {
          }
        }
        ElMessage.error(t("errorwhilerestarting"))
      })
      .finally(() => {
        actionLoading.value = false
      })
}

const about = ref({
  'version': '',
  'latest': '',
  'update': false,
  'markdownBody': ''
})

onMounted(() => {
  http.about()
      .then(res => {
        about.value = res.data
      })
})

let logout = () => {
  authorization.value = ''
  location.reload()
}

let openUrl = (url) => window.open(url)

let dialogVisible = ref(false)
let props = defineProps(['config'])

</script>

<style scoped>
.about-container {
  width: 100%;
  flex-flow: column;
}

.about-header {
  margin-bottom: 12px;
  align-items: end;
}

.cursor-pointer {
  cursor: pointer;
}

.about-dialog {
  max-width: 500px;
}

.about-scrollbar {
  margin-bottom: 16px;
  max-height: 400px;
}

.about-markdown {
  width: 800px;
}

.about-alert {
  margin-top: 8px;
}

.about-dialog-footer {
  width: 100%;
  justify-content: space-between;
}

.button-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  max-width: 100%;
  gap: 12px;
  margin-bottom: 12px;

  .el-button {
    margin: 0;
  }
}

</style>

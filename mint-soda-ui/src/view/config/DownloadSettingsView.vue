<template>
  <div class="settings-stack">
    <SettingsCard :title="$t('downloaderconnection')">
      <SettingsItem :label="$t('downloader')">
        <el-tag type="primary">qBittorrent</el-tag>
      </SettingsItem>
      <SettingsItem :label="$t('address')">
        <el-input v-model.trim="props.config.downloadToolHost" placeholder="http://192.168.1.x:8080"
                  autocomplete="off" class="settings-wide"/>
      </SettingsItem>
      <SettingsItem :label="$t('qbittorrentauthentication')">
        <el-select v-model="props.config.qbAuthType" class="settings-select">
          <el-option label="ApiKey" value="API_KEY"/>
          <el-option :label="$t('usernameandpassword')" value="USER_PASSWORD"/>
        </el-select>
      </SettingsItem>
      <SettingsItem v-if="props.config.qbAuthType !== 'USER_PASSWORD'" label="ApiKey">
        <div class="stack">
          <el-input v-model.trim="props.config.downloadToolPassword"
                    :placeholder="$t('savedleaveblanktokeep')"
                    show-password autocomplete="new-password" class="settings-wide"/>
          <el-alert v-if="props.config.downloadToolPassword && !props.config.downloadToolPassword.startsWith('qbt_')"
                    show-icon type="warning" :closable="false"
                    :title="$t('apikeyisnotconfiguredcorrectly')"/>
        </div>
      </SettingsItem>
      <template v-else>
        <SettingsItem :label="$t('username')">
          <el-input v-model.trim="props.config.qbUsername" autocomplete="username" class="settings-wide"/>
        </SettingsItem>
        <SettingsItem :label="$t('password')">
          <el-input v-model="props.config.qbPassword" type="password" show-password
                    :placeholder="$t('savedleaveblanktokeep')" autocomplete="new-password"
                    class="settings-wide"/>
        </SettingsItem>
      </template>
      <SettingsItem>
        <el-button bg text icon="Odometer" :loading="testing" @click="testLogin">{{ $t('test') }}</el-button>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('taskexecution')">
      <SettingsItem :label="$t('concurrentdownloadlimit')">
        <div class="stack">
          <el-input-number v-model="props.config.downloadCount" :min="0"/>
          <el-text size="small" type="info">{{ $t('setto0fornolimit') }}</el-text>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('delayeddownload')">
        <el-input-number v-model="props.config.delayedDownload" :min="0">
          <template #suffix><span>{{ $t('minutes') }}</span></template>
        </el-input-number>
      </SettingsItem>
      <SettingsItem :label="$t('retryattempts')">
        <el-input-number v-model="props.config.downloadRetry" :min="3" :max="100"/>
      </SettingsItem>
      <SettingsItem :label="$t('customtags')">
        <CustomTagsView :config="props.config"/>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('cleanuprules')">
      <SettingsItem :label="$t('autodelete')">
        <div class="stack">
          <el-switch v-model="props.config.delete"/>
          <el-text size="small" type="info">{{ $t('autodeletecompletedtasks') }}</el-text>
          <el-checkbox v-model="props.config.awaitStalledUP" :disabled="!props.config.delete"
                       :label="$t('waitforseedingtofinish')"/>
          <el-checkbox v-model="props.config.deleteStandbyRSSOnly" :disabled="!props.config.delete"
                       :label="$t('deletestandbyrssonlyafterthemainrssupdates')"/>
        </div>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('prioritykeep')">
      <SettingsItem :label="$t('enable-3')">
        <div class="stack">
          <el-switch v-model="props.config.priorityKeywordsEnable"/>
          <el-text size="small" type="info">{{ $t('enableprioritykeepfilteringformultifiletorre') }}</el-text>
          <PrioKeysView v-if="props.config.priorityKeywordsEnable"
                        v-model:keywords="props.config.priorityKeywords"
                        :import-global="false" :show-text="true"/>
        </div>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('qbadvanced')">
      <QBittorrentView :config="props.config"/>
    </SettingsCard>
  </div>
</template>

<script setup>
import {ElMessage} from "element-plus"
import {ref} from "vue"
import SettingsItem from "@/view/custom/SettingsItem.vue"
import SettingsCard from "@/view/custom/SettingsCard.vue"
import QBittorrentView from "@/view/config/download/qBittorrentView.vue"
import PrioKeysView from "@/view/config/PrioKeysView.vue"
import CustomTagsView from "@/view/config/CustomTagsView.vue"
import * as http from "@/js/http.js"

const props = defineProps({config: {type: Object, required: true}})

const testing = ref(false)
const testLogin = () => {
  testing.value = true
  http.downloadLoginTest(props.config)
      .then(res => ElMessage.success(res.message))
      .finally(() => { testing.value = false })
}
</script>

<style scoped>
.stack {
  display: grid;
  gap: 6px;
  width: 100%;
  justify-items: start;
}
</style>

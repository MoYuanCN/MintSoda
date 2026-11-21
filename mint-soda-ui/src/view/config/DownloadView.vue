<template>
  <div
      class="download-settings-form full-width">
    <section class="settings-section">
      <div class="settings-section-heading">
        <h3>{{ $t('downloaderconnection') }}</h3>
      </div>
      <SettingsItem :label="$t('downloader')">
        <el-tag type="primary">qBittorrent</el-tag>
      </SettingsItem>
      <SettingsItem :label="$t('address')">
        <el-input
            v-model.trim="props.config.downloadToolHost"
            placeholder="http://192.168.1.x:8080"
            autocomplete="new-password"/>
      </SettingsItem>
      <SettingsItem :label="$t('qbittorrentauthentication')">
        <el-select v-model="props.config.qbAuthType" class="width-200">
          <el-option label="ApiKey" value="API_KEY"/>
          <el-option :label="$t('usernameandpassword')" value="USER_PASSWORD"/>
        </el-select>
      </SettingsItem>
      <SettingsItem v-if="props.config.qbAuthType !== 'USER_PASSWORD'" label="ApiKey">
        <el-input
            v-model.trim="props.config.downloadToolPassword"
            :placeholder="$t('savedleaveblanktokeep')"
            show-password
            autocomplete="new-password">
          <template #prefix>
            <el-icon class="el-input__icon">
              <Key/>
            </el-icon>
          </template>
        </el-input>
        <div v-if="props.config.downloadToolPassword && !props.config.downloadToolPassword.startsWith('qbt_')" class="full-width margin-top-4">
          <el-alert show-icon type="warning" :closable="false">
            <template #title>
              {{ $t('apikeyisnotconfiguredcorrectly') }}
            </template>
          </el-alert>
        </div>
      </SettingsItem>
      <template v-else>
        <SettingsItem :label="$t('username')">
          <el-input v-model.trim="props.config.qbUsername" autocomplete="username"/>
        </SettingsItem>
        <SettingsItem :label="$t('password')">
          <el-input v-model="props.config.qbPassword" type="password" show-password
                    :placeholder="$t('savedleaveblanktokeep')" autocomplete="new-password"/>
        </SettingsItem>
      </template>
      <SettingsItem>
        <div class="download-test-button">
          <el-button @click="downloadLoginTest" bg text :loading="downloadLoginTestLoading" icon="Odometer">{{ $t('test') }}
          </el-button>
        </div>
      </SettingsItem>
    </section>

    <section class="settings-section">
      <div class="settings-section-heading">
        <h3>{{ $t('savingandcleanup') }}</h3>
      </div>
      <SettingsItem :label="$t('savepath')">
        <el-input v-model.trim="props.config['downloadPathTemplate']"/>
        <div class="full-width margin-top-4" v-if="!testPathTemplate(props.config['downloadPathTemplate'])">
          <el-alert
              type="warning"
              show-icon
              :closable="false"
          >
            <template #title>
              {{ $t('yoursavepathdoesnotfollowthetemplatedownload') }}
            </template>
          </el-alert>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('moviesavepath')">
        <el-input v-model.trim="props.config['ovaDownloadPathTemplate']"/>
        <div class="full-width margin-top-4" v-if="!testPathTemplate(props.config['ovaDownloadPathTemplate'])">
          <el-alert
              type="warning"
              show-icon
              :closable="false"
          >
            <template #title>
              {{ $t('yourmoviesavepathdoesnotfollowthetemplatedow') }}
            </template>
          </el-alert>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('collectionsavepath')">
        <el-input
            v-model.trim="props.config['collectionDownloadPathTemplate']"
            :placeholder="$t('leaveblanktousethenormalsavepath')"/>
        <div class="full-width margin-top-4">
          <el-text class="mx-1" size="small">
            {{ $t('collectionpathhint') }}
            <br>
            {{ $t('supportsthesamevariablesasthenormalsavepathl') }}
          </el-text>
        </div>
        <div class="full-width margin-top-4"
             v-if="props.config['collectionDownloadPathTemplate'] && !testPathTemplate(props.config['collectionDownloadPathTemplate'])">
          <el-alert
              type="warning"
              show-icon
              :closable="false"
          >
            <template #title>
              {{ $t('thecollectionsavepathcontainsnovariablessoev') }}
            </template>
          </el-alert>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('autodelete')">
        <div>
          <el-switch v-model:model-value="props.config.delete"/>
          <br>
          <el-text class="mx-1" size="small">
            {{ $t('autodeletecompletedtasks') }}
            <br>
            {{ $t('ifstandbyrssisalsoenabledthebetterqualityfilesaredeleted') }}
          </el-text>
          <br>
          <el-checkbox v-model:model-value="props.config.awaitStalledUP"
                       :disabled="!props.config.delete"
                       :label="$t('waitforseedingtofinish')"/>
          <br>
          <el-checkbox v-model:model-value="props.config.deleteStandbyRSSOnly"
                       :disabled="!props.config.delete"
                       :label="$t('deletestandbyrssonlyafterthemainrssupdates')"/>
          <br>
          <el-text class="mx-1" size="small">
            {{ $t('mainrsstasksandfilesareneverdeletedautomatically') }}
          </el-text>
        </div>
      </SettingsItem>
    </section>

    <section class="settings-section">
      <div class="settings-section-heading">
        <h3>{{ $t('taskcontrol') }}</h3>
      </div>
      <SettingsItem :label="$t('retryattempts')">
        <el-input-number v-model:model-value="props.config['downloadRetry']" :max="100" :min="3"/>
      </SettingsItem>
      <SettingsItem :label="$t('concurrentdownloadlimit')">
        <div>
          <el-input-number v-model:model-value="props.config.downloadCount" :min="0"/>
          <br/>
          <el-text class="mx-1" size="small">
            {{ $t('setto0fornolimit') }}
          </el-text>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('delayeddownload')">
        <el-input-number v-model:model-value="props.config.delayedDownload" :min="0">
          <template #suffix>
            <span>{{ $t('minutes') }}</span>
          </template>
        </el-input-number>
      </SettingsItem>
      <SettingsItem :label="$t('prioritykeep')">
        <div class="full-width">
          <el-switch v-model:model-value="props.config.priorityKeywordsEnable"/>
          <div>
            <el-text class="mx-1" size="small">
              {{ $t('enableprioritykeepfilteringformultifiletorre') }}
            </el-text>
          </div>
          <div v-if="props.config.priorityKeywordsEnable">
            <PrioKeysView
                v-model:keywords="props.config.priorityKeywords"
                :import-global="false"
                :show-text="true"
            />
          </div>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('customtags')">
        <CustomTagsView :config="props.config"/>
      </SettingsItem>
    </section>

    <section class="settings-section settings-section-advanced">
      <div class="settings-section-heading">
        <h3>{{ $t('advancedsettings') }}</h3>
      </div>
      <el-collapse v-model="activeName">
        <el-collapse-item name="qBittorrent" :title="$t('qbittorrentsettings')">
          <q-bittorrent-view v-if="activeName.indexOf('qBittorrent') > -1" :config="props.config"/>
        </el-collapse-item>
      </el-collapse>
    </section>

  </div>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ref} from "vue";
import {ElMessage, ElText} from "element-plus";
import {Key} from "@element-plus/icons-vue";
import qBittorrentView from "@/view/config/download/qBittorrentView.vue";
import PrioKeysView from "@/view/config/PrioKeysView.vue";
import CustomTagsView from "@/view/config/CustomTagsView.vue";
import * as http from "@/js/http.js";

const downloadLoginTestLoading = ref(false)
const downloadLoginTest = () => {
  downloadLoginTestLoading.value = true
  http.downloadLoginTest(props.config)
      .then(res => {
        ElMessage.success(res.message)
      })
      .finally(() => {
        downloadLoginTestLoading.value = false
      })
}

let testPathTemplate = (path) => {
  return new RegExp('\\$\{[A-z]+\}').test(path);
}

let activeName = ref([])

let props = defineProps(['config'])
</script>

<style scoped>
.settings-section + .settings-section {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.settings-section-heading {
  margin-bottom: 18px;
}

.settings-section-heading h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 650;
  line-height: 1.4;
}

.download-test-button {
  display: flex;
  width: 100%;
  justify-content: flex-end;
}

.download-danger-text {
  color: var(--el-color-danger);
}

.settings-section-advanced :deep(.el-collapse) {
  border-top: 0;
}

@media (max-width: 700px) {
  .settings-section + .settings-section {
    margin-top: 22px;
    padding-top: 20px;
  }

  .settings-section-heading {
    margin-bottom: 15px;
  }
}
</style>

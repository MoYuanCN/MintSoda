<template>
  <div class="settings-stack">
    <SettingsCard :title="$t('naming')">
      <SettingsItem :label="$t('autorename')">
        <el-switch v-model="props.config.rename"/>
      </SettingsItem>
      <SettingsItem :label="$t('renameinterval')">
        <el-input-number v-model="props.config.renameSleepSeconds" :disabled="!props.config.rename" :min="5">
          <template #suffix><span>{{ $t('seconds') }}</span></template>
        </el-input-number>
      </SettingsItem>
      <SettingsItem :label="$t('renametemplate')">
        <div class="stack">
          <el-input v-model="props.config.renameTemplate"
                    placeholder="${title} S${seasonFormat}E${episodeFormat}"/>
          <el-alert v-if="!testRenameTemplate(props.config.renameTemplate)" type="warning"
                    show-icon :closable="false"
                    :title="$t('keepsseasonformateepisodeformatorsseasoneepi')"/>
          <el-link class="settings-doc-link" type="primary"
                   href="https://github.com/MoYuanCN/MintSoda/blob/main/docs/USER_GUIDE.md"
                   target="_blank">{{ $t('details') }}</el-link>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('maxfilenamelength')">
        <el-input-number v-model="props.config.maxFileNameLength" :min="0"/>
      </SettingsItem>
      <SettingsItem :label="$t('removeyear')">
        <el-switch v-model="props.config.renameDelYear"/>
      </SettingsItem>
      <SettingsItem :label="$t('removetmdbid')">
        <el-switch v-model="props.config.renameDelTmdbId"/>
      </SettingsItem>
      <SettingsItem :label="$t('separatesubtitlefolder')">
        <div class="stack">
          <el-switch v-model="props.config.subtitleIndependentFolderEnabled"/>
          <el-input v-model="props.config.subtitleIndependentFolderName"
                    :disabled="!props.config.subtitleIndependentFolderEnabled"/>
          <el-text size="small" type="info">{{ $t('qbittorrentonly') }}</el-text>
        </div>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('savingpaths')">
      <SettingsItem :label="$t('savepath')">
        <div class="stack">
          <el-input v-model="props.config.downloadPathTemplate"/>
          <el-alert v-if="!testPathTemplate(props.config.downloadPathTemplate)" type="warning"
                    show-icon :closable="false"
                    :title="$t('yoursavepathdoesnotfollowthetemplatedownload')"/>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('moviesavepath')">
        <div class="stack">
          <el-input v-model="props.config.ovaDownloadPathTemplate"/>
          <el-alert v-if="!testPathTemplate(props.config.ovaDownloadPathTemplate)" type="warning"
                    show-icon :closable="false"
                    :title="$t('yourmoviesavepathdoesnotfollowthetemplatedow')"/>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('collectionsavepath')">
        <div class="stack">
          <el-input v-model="props.config.collectionDownloadPathTemplate"
                    :placeholder="$t('leaveblanktousethenormalsavepath')"/>
          <el-text size="small" type="info">
            {{ $t('collectionpathhint') }}
            {{ $t('supportsthesamevariablesasthenormalsavepathl') }}
          </el-text>
          <el-alert v-if="props.config.collectionDownloadPathTemplate
                          && !testPathTemplate(props.config.collectionDownloadPathTemplate)"
                    type="warning" show-icon :closable="false"
                    :title="$t('thecollectionsavepathcontainsnovariablessoev')"/>
        </div>
      </SettingsItem>
    </SettingsCard>
  </div>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue"
import SettingsCard from "@/view/custom/SettingsCard.vue"

const props = defineProps({config: {type: Object, required: true}})

const testRenameTemplate = template => ['S${season}E${episode}', 'S${seasonFormat}E${episodeFormat}']
    .some(pattern => (template || '').includes(pattern))

const testPathTemplate = path => new RegExp('\\$\\{[A-Za-z]+\\}').test(path || '')
</script>

<style scoped>
.stack {
  display: grid;
  gap: 6px;
  width: 100%;
}

.settings-doc-link {
  font-size: var(--el-font-size-extra-small);
  justify-self: start;
}
</style>

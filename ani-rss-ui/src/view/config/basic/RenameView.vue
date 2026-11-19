<template>
  <SettingsItem :label="$t('autorename')">
    <el-switch v-model:model-value="props.config.rename"/>
  </SettingsItem>
  <SettingsItem :label="$t('renameinterval')">
    <el-input-number v-model:model-value="props.config['renameSleepSeconds']"
                     :disabled="!config.rename"
                     :min="5">
      <template #suffix>
        <span>{{ $t('seconds') }}</span>
      </template>
    </el-input-number>
  </SettingsItem>
  <SettingsItem :label="$t('maxfilenamelength')">
    <el-input-number v-model:model-value="props.config.maxFileNameLength" :min="0"/>
  </SettingsItem>
  <SettingsItem :label="$t('renametemplate')">
    <div class="full-width">
      <div>
        <el-input v-model:model-value="props.config.renameTemplate"
                  placeholder="${title} S${seasonFormat}E${episodeFormat}"/>
      </div>
      <div>
        <el-alert
            v-if="!testRenameTemplate(props.config.renameTemplate)"
            class="mt-8"
            type="warning"
            show-icon
            :closable="false"
        >
          <template #title>
            {{ $t('keepsseasonformateepisodeformatorsseasoneepi') }}
          </template>
        </el-alert>
      </div>
      <el-text class="mx-1" size="small">
        <el-link
            class="text-extra-small"
            type="primary"
            href="https://github.com/MoYuanCN/ani-rss-Yuri/blob/main/docs/USER_GUIDE.md"
            target="_blank">{{ $t('details') }}
        </el-link>
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('removeyear')">
    <div>
      <el-switch v-model:model-value="props.config.renameDelYear"/>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('removetheyearwhenrenamingeg2024') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('removetmdbid')">
    <div>
      <el-switch v-model:model-value="props.config.renameDelTmdbId"/>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('removetmdbidwhenrenamingegtmdbid242143') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('separatesubtitlefolder')">
    <div>
      <el-switch v-model:model-value="props.config.subtitleIndependentFolderEnabled"/>
      <br>
      <el-input v-model="config.subtitleIndependentFolderName"/>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('qbittorrentonly') }}
      </el-text>
    </div>
  </SettingsItem>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ElText} from "element-plus";

let testRenameTemplate = renameTemplate => {
  let test = [
    'S${season}E${episode}',
    'S${seasonFormat}E${episodeFormat}'
  ]
  for (let s of test) {
    if (renameTemplate.indexOf(s) > -1) {
      return true;
    }
  }
  return false;
}

let props = defineProps(['config'])
</script>

<style scoped>
.mt-8 {
  margin-top: 8px;
}

.text-extra-small {
  font-size: var(--el-font-size-extra-small);
}
</style>

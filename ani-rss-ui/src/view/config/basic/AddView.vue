<template>
  <SettingsItem :label="$t('onlydownloadthelatestepisode')">
    <el-switch v-model:model-value="props.config.downloadNew"/>
  </SettingsItem>
  <SettingsItem :label="$t('appendyeartotitle')">
    <el-switch v-model:model-value="props.config.titleYear"/>
  </SettingsItem>
  <SettingsItem :label="$t('autoepisodeoffset')">
    <el-switch v-model:model-value="props.config.offset"/>
  </SettingsItem>
  <SettingsItem :label="$t('autooffsettype')">
    <el-select v-model="props.config['offsetType']" class="width-150">
      <el-option value="AUTO_OFFSET" label="Auto"/>
      <el-option value="BGM_OFFSET" label="Bangumi"/>
    </el-select>
  </SettingsItem>
  <SettingsItem :label="$t('bangumijapanesetitle')">
    <el-switch v-model:model-value="props.config['bgmJpName']"/>
  </SettingsItem>
  <SettingsItem label="TMDB ID">
    <div>
      <el-switch v-model="props.config.tmdbId"/>
      <br>
      <el-checkbox
          :disabled="!props.config.tmdbId"
          v-model="props.config.tmdbIdPlexMode"
          label="Plex Mode"
      />
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('fetchtmdbidautomaticallyegtmdbid242143') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('tmdbtitle')">
    <div>
      <el-switch v-model:model-value="props.config.tmdb"/>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('usethetmdbtitleautomatically') }}
      </el-text>
      <br>
      <el-checkbox v-model="props.config['tmdbAnime']" :label="$t('animeonly')"/>
      <el-checkbox v-model="props.config['tmdbOriginalName']" :label="$t('useoriginaltitle')"/>
      <el-checkbox v-model="props.config['tmdbRomaji']" :label="$t('preferromaji')"/>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('tmdblanguage')">
    <el-select v-model:model-value="props.config['tmdbLanguage']" class="width-150">
      <el-option v-for="item in tmdb_i18n" :value="item.i18n_tag"
                 :label="`${item.native_name} (${item.i18n_tag})`"
                 :key="item.i18n_tag">
        <div style="width: 100%;">
          <span class="float-left">{{ item.native_name }}</span>
          <el-text type="info" class="float-right" size="small">
            {{ item.i18n_tag }}
          </el-text>
        </div>
      </el-option>
    </el-select>
  </SettingsItem>
  <SettingsItem :label="$t('enableglobalexclude')">
    <el-switch v-model:model-value="props.config.enabledExclude" :disabled="props.config.importExclude"/>
  </SettingsItem>
  <SettingsItem :label="$t('importglobalexclude')">
    <el-switch v-model:model-value="props.config.importExclude" :disabled="props.config.enabledExclude"/>
  </SettingsItem>
  <SettingsItem :label="$t('enableglobalmatchbydefault')">
    <el-switch v-model="props.config.enabledGlobalMatch" :disabled="props.config.importGlobalMatch"/>
  </SettingsItem>
  <SettingsItem :label="$t('importglobalmatchbydefault')">
    <el-switch v-model="props.config.importGlobalMatch" :disabled="props.config.enabledGlobalMatch"/>
  </SettingsItem>
  <SettingsItem :label="$t('enableglobalreversematchbydefault')">
    <el-switch v-model="props.config.enabledGlobalReverseMatch" :disabled="props.config.importGlobalReverseMatch"/>
  </SettingsItem>
  <SettingsItem :label="$t('importglobalreversematchbydefault')">
    <el-switch v-model="props.config.importGlobalReverseMatch" :disabled="props.config.enabledGlobalReverseMatch"/>
  </SettingsItem>
  <SettingsItem :label="$t('coverquality')">
    <el-select v-model="props.config['bgmImageSize']" class="width-150">
      <el-option v-for="item in ['large','medium','common']" :key="item"
                 :value="item"/>
    </el-select>
  </SettingsItem>
  <SettingsItem :label="$t('customepisoderules')">
    <div class="full-width">
      <div>
        <el-switch v-model="props.config.customEpisode"/>
      </div>
      <div class="flex full-width">
        <el-input v-model="props.config.customEpisodeStr"
                  class="full-width"/>
        <div class="spacer-8"></div>
        <el-input-number v-model="props.config.customEpisodeGroupIndex"/>
      </div>
    </div>
  </SettingsItem>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ElText} from "element-plus";
import {tmdb_i18n} from "@/js/tmdb-i18n.js";

let props = defineProps(['config'])
</script>

<style scoped>
.float-left {
  float: left;
}

.float-right {
  float: right;
}

.spacer-8 {
  width: 8px;
}
</style>

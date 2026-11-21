<template>
  <div class="settings-stack">
    <SettingsCard :title="$t('siteandmetadata')" :description="$t('siteandmetadatahint')">
      <SettingsItem label="Mikan">
        <el-input v-model="props.config.mikanHost" placeholder="https://mikanani.me" class="settings-wide"/>
      </SettingsItem>
      <SettingsItem label="Bangumi API">
        <el-input v-model="props.config.bgmApi" placeholder="https://api.bgm.tv" class="settings-wide"/>
      </SettingsItem>
      <SettingsItem label="Bangumi Token">
        <BangumiView :config="props.config"/>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('tmdbsettings')">
      <SettingsItem :label="$t('tmdbtitle')">
        <div class="stack">
          <el-switch v-model="props.config.tmdb"/>
          <el-text size="small" type="info">{{ $t('usethetmdbtitleautomatically') }}</el-text>
          <div class="inline-checks">
            <el-checkbox v-model="props.config.tmdbAnime" :label="$t('animeonly')"/>
            <el-checkbox v-model="props.config.tmdbOriginalName" :label="$t('useoriginaltitle')"/>
            <el-checkbox v-model="props.config.tmdbRomaji" :label="$t('preferromaji')"/>
          </div>
        </div>
      </SettingsItem>
      <SettingsItem label="TMDB ID">
        <div class="stack">
          <el-switch v-model="props.config.tmdbId"/>
          <el-checkbox v-model="props.config.tmdbIdPlexMode" :disabled="!props.config.tmdbId"
                       label="Plex Mode"/>
          <el-text size="small" type="info">{{ $t('fetchtmdbidautomaticallyegtmdbid242143') }}</el-text>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('tmdblanguage')">
        <el-select v-model="props.config.tmdbLanguage" class="settings-select" filterable>
          <el-option v-for="item in tmdb_i18n" :key="item.i18n_tag" :value="item.i18n_tag"
                     :label="`${item.native_name} (${item.i18n_tag})`"/>
        </el-select>
      </SettingsItem>
      <SettingsItem label="API Key">
        <el-input v-model="props.config.tmdbApiKey" class="settings-wide" placeholder="TMDB API Key"
                  show-password/>
      </SettingsItem>
      <SettingsItem label="API">
        <el-input v-model="props.config.tmdbApi" placeholder="https://api.themoviedb.org"
                  class="settings-wide"/>
      </SettingsItem>
      <SettingsItem label="Image">
        <el-input v-model="props.config.tmdbImage" placeholder="https://image.tmdb.org"
                  class="settings-wide"/>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('githubtoken')">
      <SettingsItem :label="$t('githubtoken')">
        <div class="stack">
          <el-input v-model="props.config.githubToken" clearable
                    :placeholder="$t('enteryourgithubtoken')" class="settings-wide"/>
          <el-button bg :icon="Github" class="settings-self-start"
                     @click="openUrl('https://github.com/login/oauth/authorize?client_id=Ov23li1dD89l7iGKhYa3&redirect_uri=https://github-app.wushuo.top/&scope=read:user')">
            {{ $t('getagithubtoken') }}
          </el-button>
        </div>
      </SettingsItem>
    </SettingsCard>
  </div>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue"
import SettingsCard from "@/view/custom/SettingsCard.vue"
import BangumiView from "@/view/config/basic/BangumiView.vue"
import {Github} from "@vicons/fa"
import {tmdb_i18n} from "@/js/tmdb-i18n.js"

const props = defineProps({config: {type: Object, required: true}})
const openUrl = url => window.open(url)
</script>

<style scoped>
.stack {
  display: grid;
  gap: 6px;
  width: 100%;
  justify-items: start;
}

.inline-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}
</style>

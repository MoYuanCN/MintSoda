<template>
  <div class="settings-stack">
    <SettingsCard :title="$t('subscriptionsandupdates')" :description="$t('rssandupdateshint')">
      <SettingsItem :label="$t('rssswitch')">
        <el-switch v-model="props.config.rss"/>
      </SettingsItem>
      <SettingsItem :label="$t('rssinterval')">
        <el-input-number v-model="props.config.rssSleepSeconds" :disabled="!props.config.rss" :min="1">
          <template #suffix><span>{{ $t('seconds') }}</span></template>
        </el-input-number>
      </SettingsItem>
      <SettingsItem :label="$t('rssconcurrency')">
        <el-input-number v-model="props.config.rssConcurrency" :min="1" :max="16"/>
      </SettingsItem>
      <SettingsItem :label="$t('rsstimeout')">
        <el-input-number v-model="props.config.rssTimeout" :min="1" :max="120">
          <template #suffix><span>{{ $t('seconds') }}</span></template>
        </el-input-number>
      </SettingsItem>
      <SettingsItem :label="$t('refreshmode')">
        <el-text size="small" type="info">{{ $t('eachroundstartsatafixedintervalmeasuredfromt') }}</el-text>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('episodeandcompletion')">
      <SettingsItem :label="$t('autoskip')">
        <div class="stack">
          <el-switch v-model="props.config.fileExist" :disabled="!props.config.rename"/>
          <el-text size="small" type="info">{{ $t('skipalreadydownloadedfilesthisrequiresautore') }}</el-text>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('autoskipx5episodes')">
        <el-switch v-model="props.config.skip5"/>
      </SettingsItem>
      <SettingsItem :label="$t('missingepisodedetection')">
        <div class="stack">
          <el-switch v-model="props.config.omit"/>
          <el-text size="small" type="info">{{ $t('masterswitchsendsanotificationwhenepisodesar') }}</el-text>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('autoupdatetotalepisodes')">
        <div class="stack">
          <el-switch v-model="props.config.updateTotalEpisodeNumber"/>
          <el-checkbox v-model="props.config.forceUpdateTotalEpisodeNumber"
                       :disabled="!props.config.updateTotalEpisodeNumber"
                       class="el-checkbox-danger"
                       :label="$t('forceupdate')"/>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('autodisablesubscription')">
        <div class="stack">
          <el-switch v-model="props.config.autoDisabled"/>
          <el-text size="small" type="info">{{ $t('fetchthetotalepisodecountfrombangumianddisab') }}</el-text>
          <el-checkbox v-model="props.config.completed"
                       :disabled="!props.config.autoDisabled"
                       :label="$t('completedsubscriptionmigration')"/>
          <el-input v-model="props.config.completedPathTemplate"
                    :disabled="!props.config.autoDisabled || !props.config.completed"
                    placeholder="${title}/Season ${season}"/>
        </div>
      </SettingsItem>
      <SettingsItem :label="$t('standbyrss')">
        <div class="stack">
          <el-switch v-model="props.config.standbyRss"/>
          <el-checkbox v-model="props.config.coexist" :disabled="!props.config.standbyRss"
                       :label="$t('multisubgroupmode')"/>
        </div>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('subscriptiondefaults')" :description="$t('defaultruleshint')">
      <SettingsItem :label="$t('onlydownloadthelatestepisode')">
        <el-switch v-model="props.config.downloadNew"/>
      </SettingsItem>
      <SettingsItem :label="$t('appendyeartotitle')">
        <el-switch v-model="props.config.titleYear"/>
      </SettingsItem>
      <SettingsItem :label="$t('autoepisodeoffset')">
        <el-select v-model="props.config.offsetType" class="settings-select" :disabled="!props.config.offset">
          <el-option value="AUTO_OFFSET" label="Auto"/>
          <el-option value="BGM_OFFSET" label="Bangumi"/>
        </el-select>
        <el-checkbox v-model="props.config.offset" class="settings-inline-check"
                     :label="$t('autoepisodeoffset')"/>
      </SettingsItem>
      <SettingsItem :label="$t('bangumijapanesetitle')">
        <el-switch v-model="props.config.bgmJpName"/>
      </SettingsItem>
      <SettingsItem :label="$t('coverquality')">
        <el-select v-model="props.config.bgmImageSize" class="settings-select">
          <el-option v-for="item in ['large','medium','common']" :key="item" :value="item" :label="item"/>
        </el-select>
      </SettingsItem>
      <SettingsItem :label="$t('customepisoderules')">
        <div class="stack">
          <el-switch v-model="props.config.customEpisode"/>
          <div class="inline-fields">
            <el-input v-model="props.config.customEpisodeStr" :disabled="!props.config.customEpisode"/>
            <el-input-number v-model="props.config.customEpisodeGroupIndex"
                             :disabled="!props.config.customEpisode"/>
          </div>
        </div>
      </SettingsItem>
    </SettingsCard>

    <SettingsCard :title="$t('rulesdefaults')" :description="$t('defaultruleshint')">
      <SettingsItem :label="$t('globalrules')">
        <div class="rule-defaults">
          <div class="rule-default-row">
            <span>{{ $t('globalexclude') }}</span>
            <el-switch v-model="props.config.enabledExclude" :disabled="props.config.importExclude"/>
            <el-checkbox v-model="props.config.importExclude" :disabled="props.config.enabledExclude"
                         :label="$t('import')"/>
          </div>
          <div class="rule-default-row">
            <span>{{ $t('globalmatch') }}</span>
            <el-switch v-model="props.config.enabledGlobalMatch" :disabled="props.config.importGlobalMatch"/>
            <el-checkbox v-model="props.config.importGlobalMatch"
                         :disabled="props.config.enabledGlobalMatch"
                         :label="$t('import')"/>
          </div>
          <div class="rule-default-row">
            <span>{{ $t('reversematch') }}</span>
            <el-switch v-model="props.config.enabledGlobalReverseMatch"
                       :disabled="props.config.importGlobalReverseMatch"/>
            <el-checkbox v-model="props.config.importGlobalReverseMatch"
                         :disabled="props.config.enabledGlobalReverseMatch"
                         :label="$t('import')"/>
          </div>
        </div>
      </SettingsItem>
    </SettingsCard>
  </div>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue"
import SettingsCard from "@/view/custom/SettingsCard.vue"

const props = defineProps({config: {type: Object, required: true}})
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
  gap: 8px;
  width: 100%;
}

.rule-defaults {
  display: grid;
  gap: 8px;
  width: 100%;
}

.rule-default-row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) auto auto;
  align-items: center;
  gap: 12px;
}

.rule-default-row span {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

@media (max-width: 700px) {
  .rule-default-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>

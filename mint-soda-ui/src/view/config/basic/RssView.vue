<template>
  <SettingsItem :label="$t('rssswitch')">
    <el-switch v-model:model-value="props.config.rss"/>
  </SettingsItem>
  <SettingsItem :label="$t('rssinterval')">
    <el-input-number v-model:model-value="props.config.rssSleepSeconds" :disabled="!props.config.rss" :min="1">
      <template #suffix>
        <span>{{ $t('seconds') }}</span>
      </template>
    </el-input-number>
  </SettingsItem>
  <SettingsItem :label="$t('rsstimeout')">
    <div>
      <el-input-number v-model:model-value="props.config['rssTimeout']"
                       :max="120" :min="1">
        <template #suffix>
          <span>{{ $t('seconds') }}</span>
        </template>
      </el-input-number>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('maximumwaitperrssrequestontimeoutthelastsucc') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('rssconcurrency')">
    <div>
      <el-input-number v-model:model-value="props.config['rssConcurrency']"
                       :max="16" :min="1">
        <template #suffix>
          <span>{{ $t('items') }}</span>
        </template>
      </el-input-number>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('numberofrssfeedsfetchedperroundkeep24withsho') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('refreshmode')">
    <el-text class="mx-1" size="small">
      {{ $t('eachroundstartsatafixedintervalmeasuredfromt') }}
    </el-text>
  </SettingsItem>
  <SettingsItem :label="$t('autoskip')">
    <div class="full-width">
      <el-switch v-model:model-value="props.config.fileExist" :disabled="!config.rename"/>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('skipalreadydownloadedfilesthisrequiresautore') }}
        &nbsp;
        <el-link
            class="text-extra-small"
            type="primary"
            href="https://github.com/MoYuanCN/MintSoda/blob/main/docs/USER_GUIDE.md"
            target="_blank">
          {{ $t('details') }}
        </el-link>
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('autodisablesubscription')">
    <div class="full-width">
      <el-switch v-model:model-value="props.config.autoDisabled"/>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('fetchthetotalepisodecountfrombangumianddisab') }}
      </el-text>
      <div>
        <el-checkbox v-model="props.config['completed']"
                     :disabled="!props.config.autoDisabled"
                     :label="$t('completedsubscriptionmigration')"/>
      </div>
      <div>
        <el-input v-model="props.config['completedPathTemplate']"
                  :disabled="!props.config.autoDisabled || !props.config['completed']"/>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('autoupdatetotalepisodes')">
    <div class="full-width">
      <el-switch v-model="props.config.updateTotalEpisodeNumber"/>
      <div>
        <el-checkbox v-model="props.config.forceUpdateTotalEpisodeNumber"
                     :disabled="!props.config.updateTotalEpisodeNumber"
                     class="el-checkbox-danger"
                     :label="$t('forceupdate')"/>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('autoskipx5episodes')">
    <el-switch v-model:model-value="props.config.skip5"/>
  </SettingsItem>
  <SettingsItem :label="$t('missingepisodedetection')">
    <div>
      <div>
        <el-switch v-model:model-value="props.config.omit"/>
      </div>
      <el-text class="mx-1" size="small">
        {{ $t('masterswitchsendsanotificationwhenepisodesar') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('standbyrss')">
    <div class="full-width">
      <div>
        <el-switch v-model:model-value="props.config.standbyRss"/>
      </div>
      <div>
        <el-checkbox v-model="props.config['coexist']" :disabled="!props.config.standbyRss"
                     :label="$t('multisubgroupmode')"/>
      </div>
      <div class="flex full-width justify-end">
        <el-link
            type="primary"
            href="https://github.com/MoYuanCN/MintSoda/blob/main/docs/USER_GUIDE.md"
            target="_blank">
          {{ $t('details') }}
        </el-link>
      </div>
    </div>
  </SettingsItem>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ElText} from "element-plus";

let props = defineProps(['config'])
</script>

<style scoped>
.text-extra-small {
  font-size: var(--el-font-size-extra-small);
}

.justify-end {
  justify-content: end;
}
</style>

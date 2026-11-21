<template>
  <SettingsItem :label="$t('downloadlimit')">
    <el-input-number v-model:model-value="props.config['dlLimit']" :min="0">
      <template #suffix>
        <span>KiB/s</span>
      </template>
    </el-input-number>
  </SettingsItem>
  <SettingsItem :label="$t('uploadlimit')">
    <el-input-number v-model:model-value="props.config['upLimit']" :min="0">
      <template #suffix>
        <span>KiB/s</span>
      </template>
    </el-input-number>
  </SettingsItem>
  <SettingsItem :label="$t('shareratio')">
    <div>
      <el-input-number v-model:model-value="props.config.ratioLimit" :min="-2"/>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('1disablesit2usestheglobalsetting') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('totalseedingtime')">
    <div>
      <el-input-number v-model:model-value="props.config.seedingTimeLimit" :min="-2">
        <template #suffix>
          <span>{{ $t('minutes') }}</span>
        </template>
      </el-input-number>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('1disablesit2usestheglobalsetting') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('inactivetime')">
    <div>
      <el-input-number v-model:model-value="props.config.inactiveSeedingTimeLimit" :min="-2">
        <template #suffix>
          <span>{{ $t('minutes') }}</span>
        </template>
      </el-input-number>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('1disablesit2usestheglobalsetting') }}
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('contentlayout')">
    <div>
      <el-select v-model:model-value="props.config.qbContentLayout" class="width-150">
        <el-option :label="$t('original')" value="Original"/>
        <el-option :label="$t('createsubfolder')" value="Subfolder"/>
        <el-option :label="$t('donotcreatesubfolder')" value="NoSubfolder"/>
      </el-select>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('qbittorrentsavepath')">
    <div>
      <el-switch v-model:model-value="props.config.qbUseDownloadPath"
                 :disabled="props.config.downloadToolType !== 'qBittorrent'"/>
      <br>
      <el-text class="mx-1" size="small">
        {{ $t('useqbittorrentstemporarydownloadpaththefinal') }}
      </el-text>
    </div>
  </SettingsItem>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";

let props = defineProps(['config'])
</script>

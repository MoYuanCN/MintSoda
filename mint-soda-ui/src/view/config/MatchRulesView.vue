<template>
  <div class="settings-stack">
    <el-tabs v-model="section" class="rules-tabs">
      <el-tab-pane :label="$t('globalexclude')" name="exclude">
        <SettingsCard :title="$t('globalexclude')" :description="$t('excludeunwantedreleasesinoneplace')">
          <ExcludeView v-model:exclude="props.config.exclude" :show-text="true"/>
        </SettingsCard>
      </el-tab-pane>
      <el-tab-pane :label="$t('globalmatch')" name="match">
        <SettingsCard :title="$t('globalmatch')" :description="$t('requireeveryrsstitletomatchtheserules')">
          <el-alert :closable="false" show-icon type="info"
                    :title="$t('everyglobalmatchrulemusthitamatchtakespreced')"/>
          <ExcludeView v-model:exclude="props.config.globalMatch" :show-text="true"/>
        </SettingsCard>
      </el-tab-pane>
      <el-tab-pane :label="$t('reversematch')" name="reverse">
        <ReverseMatchSettingsView :config="props.config"/>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import {ref} from "vue"
import SettingsCard from "@/view/custom/SettingsCard.vue"
import ExcludeView from "@/view/config/ExcludeView.vue"
import ReverseMatchSettingsView from "@/view/config/ReverseMatchSettingsView.vue"

const props = defineProps({config: {type: Object, required: true}})
const section = ref('exclude')
</script>

<style scoped>
.rules-tabs :deep(.el-tabs__content) {
  overflow: visible;
}
</style>

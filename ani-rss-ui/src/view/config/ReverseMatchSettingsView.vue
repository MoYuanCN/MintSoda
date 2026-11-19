<template>
  <div class="reverse-match-settings">
    <el-alert :closable="false" show-icon type="info"
              :title="$t('priorityreversematchglobalmatchexcludearever')"/>
    <el-alert :closable="false" show-icon type="warning"
              :title="$t('thispagehastwoscopesglobalrulesabovebelongto')"/>
    <el-card shadow="never">
      <div class="section-heading">
        <div>
          <strong>{{ $t('globalreversematchrules') }}</strong>
          <el-text class="section-hint" size="small" type="info">{{ $t('subscriptionsthatenableuseglobalreversematch') }}</el-text>
        </div>
      </div>
      <GlobalReverseMatchView :config="props.config"/>
    </el-card>
    <div class="reverse-match-settings-toolbar">
      <el-select v-model="selectedId" filterable :placeholder="$t('selectsubscription')" :loading="loading">
        <el-option v-for="ani in subscriptions" :key="ani.id"
                   :label="`${ani.title} · ${ani.subgroup || $t('unknownsubgroup')}`" :value="ani.id"/>
      </el-select>
      <el-button type="primary" icon="Check" :disabled="!selectedAni || loading" :loading="saving"
                 @click="save">{{ $t('savesubscriptionrules') }}</el-button>
    </div>
    <el-empty v-if="!selectedAni && !loading" :description="$t('nosubscriptions')"/>
    <ReverseMatchView v-else-if="selectedAni" :ani="selectedAni"/>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed, onMounted, ref} from 'vue'
import {ElMessage} from 'element-plus'
import * as http from '@/js/http.js'
import ReverseMatchView from '@/view/config/ReverseMatchView.vue'
import GlobalReverseMatchView from '@/view/config/GlobalReverseMatchView.vue'
const {t} = useI18n()
const props = defineProps({config: {type: Object, required: true}})

const subscriptions = ref([])
const selectedId = ref('')
const loading = ref(false)
const saving = ref(false)
const selectedAni = computed(() => subscriptions.value.find(ani => ani.id === selectedId.value))

const load = async () => {
  loading.value = true
  try {
    const response = await http.listAni()
    subscriptions.value = (response.data?.weekList || []).flatMap(week => week.items || [])
    selectedId.value = subscriptions.value[0]?.id || ''
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!selectedAni.value) return
  saving.value = true
  try {
    const response = await http.setAni(false, selectedAni.value)
    ElMessage.success(response.message || t('reversematchrulessaved'))
    window.$reLoadList?.()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.reverse-match-settings {
  display: grid;
  gap: 16px;
}

.section-heading { display: grid; gap: 4px; margin-bottom: 12px; }
.section-hint { display: block; margin-top: 4px; }

.reverse-match-settings-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.reverse-match-settings-toolbar :deep(.el-select) {
  flex: 1 1 260px;
  max-width: 520px;
}
</style>

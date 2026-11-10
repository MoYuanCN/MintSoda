<template>
  <div class="reverse-match-settings">
    <el-alert :closable="false" show-icon type="info"
              title="规则优先级：反匹配 > 全局匹配 > 排除。反匹配未命中默认跳过；可在订阅里改为继续普通规则或放行。保存全局规则请使用设置页右上角的保存按钮。"/>
    <el-card shadow="never">
      <div class="section-heading">
        <div>
          <strong>全局反匹配规则</strong>
          <el-text class="section-hint" size="small" type="info">订阅开启“使用全局反匹配”后共享这些规则。任意一条命中即可通过反匹配。</el-text>
        </div>
      </div>
      <GlobalReverseMatchView :config="props.config"/>
    </el-card>
    <div class="reverse-match-settings-toolbar">
      <el-select v-model="selectedId" filterable placeholder="选择订阅" :loading="loading">
        <el-option v-for="ani in subscriptions" :key="ani.id"
                   :label="`${ani.title} · ${ani.subgroup || '未知字幕组'}`" :value="ani.id"/>
      </el-select>
      <el-button type="primary" icon="Check" :disabled="!selectedAni || loading" :loading="saving"
                 @click="save">保存反匹配</el-button>
    </div>
    <el-empty v-if="!selectedAni && !loading" description="没有订阅"/>
    <ReverseMatchView v-else-if="selectedAni" :ani="selectedAni"/>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {ElMessage} from 'element-plus'
import * as http from '@/js/http.js'
import ReverseMatchView from '@/view/config/ReverseMatchView.vue'
import GlobalReverseMatchView from '@/view/config/GlobalReverseMatchView.vue'

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
    ElMessage.success(response.message || '反匹配规则已保存')
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

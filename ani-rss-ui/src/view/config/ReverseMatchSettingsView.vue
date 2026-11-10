<template>
  <div class="reverse-match-settings">
    <el-alert :closable="false" show-icon type="info"
              title="选择订阅后即可集中管理反匹配规则。规则会保存到对应订阅，并在该订阅刷新时使用。"/>
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

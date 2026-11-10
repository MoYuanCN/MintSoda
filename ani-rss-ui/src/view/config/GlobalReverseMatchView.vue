<template>
  <div class="global-reverse-match">
    <ExcludeView v-model:exclude="props.config.globalReverseMatch" :show-text="true"/>
    <el-input v-model="testTitles" :autosize="{minRows: 2, maxRows: 6}"
              placeholder="输入标题，每行一条，测试全局反匹配规则" type="textarea"/>
    <el-input v-model="testSubgroup" placeholder="测试字幕组（用于字幕组限定规则，可留空）"/>
    <div class="test-actions">
      <el-button bg text icon="CircleCheck" type="primary" @click="runTest">测试标题</el-button>
    </div>
    <div v-if="testResults.length" class="test-results">
      <div v-for="result in testResults" :key="`${result.title}-${result.index}`" class="test-result">
        <el-tag :type="result.matched ? 'success' : 'info'" size="small">{{ result.matched ? '命中' : '未命中' }}</el-tag>
        <el-text class="test-title" :title="result.title">{{ result.title }}</el-text>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, ref} from 'vue'
import {ElMessage} from 'element-plus'
import ExcludeView from '@/view/config/ExcludeView.vue'

const props = defineProps({config: {type: Object, required: true}})
if (!Array.isArray(props.config.globalReverseMatch)) props.config.globalReverseMatch = []
const testTitles = ref('')
const testSubgroup = ref('')
const testResults = ref([])
const rules = computed(() => props.config.globalReverseMatch || [])

const runTest = () => {
  if (!rules.value.length) {
    ElMessage.warning('请先添加至少一条全局反匹配规则')
    return
  }
  let patterns
  try {
    patterns = rules.value.map(rule => {
      const scoped = /^\{\{(.+)}}:(.+)$/.exec(rule)
      return {subgroup: scoped?.[1] || '', pattern: new RegExp(scoped?.[2] || rule)}
    })
  } catch {
    ElMessage.error('规则中包含无效的正则表达式，请修正规则后再测试')
    return
  }
  testResults.value = testTitles.value.split(/\r?\n/).map(title => title.trim()).filter(Boolean)
      .map((title, index) => ({
        title,
        index,
        matched: patterns.some(({subgroup, pattern}) => (!subgroup || subgroup === testSubgroup.value) && pattern.test(title))
      }))
  if (!testResults.value.length) ElMessage.info('请先输入用于测试的标题')
}
</script>

<style scoped>
.global-reverse-match { display: grid; gap: 8px; }
.test-actions { display: flex; justify-content: flex-end; }
.test-results { display: grid; gap: 5px; }
.test-result { display: flex; align-items: center; gap: 8px; min-width: 0; }
.test-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>

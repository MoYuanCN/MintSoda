<template>
  <div class="reverse-match full-width">
    <div class="reverse-match-toggle">
      <el-switch v-model="props.ani.reverseMatchEnabled"/>
      <el-text size="small" type="info">使用本订阅维护的反匹配规则</el-text>
    </div>
    <div class="reverse-match-toggle">
      <el-switch v-model="props.ani.globalReverseMatch"/>
      <el-text size="small" type="info">同时使用设置中的全局反匹配规则</el-text>
    </div>
    <div v-if="props.ani.reverseMatchEnabled || props.ani.globalReverseMatch" class="reverse-match-miss">
      <el-text size="small">反匹配未命中时</el-text>
      <el-select v-model="props.ani.reverseMatchMissBehavior" size="small" class="miss-select">
        <el-option value="SKIP" label="跳过，不下载"/>
        <el-option value="NORMAL" label="继续普通匹配规则"/>
        <el-option value="ALLOW" label="直接放行"/>
      </el-select>
    </div>
    <template v-if="props.ani.reverseMatchEnabled">
      <div class="reverse-match-tags">
        <el-tag v-for="(keyword, index) in keywords" :key="`${keyword}-${index}`" closable
                @close="removeKeyword(index)">
          {{ keyword }}
        </el-tag>
        <el-tag v-if="!keywords.length" type="info">添加至少一个关键词</el-tag>
      </div>
      <div class="reverse-match-add">
        <el-input v-model="newKeyword" placeholder="关键词或正则表达式，如：作品名|别名" @keyup.enter="addKeyword"/>
        <el-button bg text icon="Plus" @click="addKeyword">添加</el-button>
      </div>
      <el-text size="small" type="info">任意一条本地规则命中即匹配。命中条目会归入字幕组：{{ props.ani.subgroup || '未知字幕组' }}</el-text>
      <el-input v-model="testTitles" class="reverse-match-test-input" :autosize="{ minRows: 3, maxRows: 7 }"
                placeholder="粘贴 RSS 标题，每行一条，用于测试匹配结果" type="textarea"/>
      <div class="reverse-match-test-actions">
        <el-button bg text icon="CircleCheck" type="primary" @click="runTest">测试标题</el-button>
      </div>
      <div v-if="testResults.length" class="reverse-match-results">
        <div v-for="result in testResults" :key="`${result.title}-${result.index}`" class="reverse-match-result">
          <el-tag :type="result.matched ? 'success' : 'info'" size="small">
            {{ result.matched ? '命中' : '未命中' }}
          </el-tag>
          <el-text class="reverse-match-result-title" :title="result.title">{{ result.title }}</el-text>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import {computed, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'

const props = defineProps({ani: {type: Object, required: true}})
const newKeyword = ref('')
const testTitles = ref('')
const testResults = ref([])
const keywords = computed(() => Array.isArray(props.ani.reverseMatchKeywords) ? props.ani.reverseMatchKeywords : [])

if (!props.ani.reverseMatchMissBehavior) props.ani.reverseMatchMissBehavior = 'SKIP'
if (typeof props.ani.globalReverseMatch !== 'boolean') props.ani.globalReverseMatch = false

watch(() => props.ani.reverseMatchEnabled, enabled => {
  if (enabled && !Array.isArray(props.ani.reverseMatchKeywords)) props.ani.reverseMatchKeywords = []
})

const addKeyword = () => {
  const keyword = newKeyword.value.trim()
  if (!keyword) return
  try {
    new RegExp(keyword)
  } catch {
    ElMessage.error('正则表达式格式无效')
    return
  }
  if (!Array.isArray(props.ani.reverseMatchKeywords)) props.ani.reverseMatchKeywords = []
  if (!keywords.value.includes(keyword)) props.ani.reverseMatchKeywords.push(keyword)
  newKeyword.value = ''
  testResults.value = []
}

const removeKeyword = index => {
  props.ani.reverseMatchKeywords.splice(index, 1)
  testResults.value = []
}

const runTest = () => {
  if (!keywords.value.length) {
    ElMessage.warning('请先添加至少一个匹配关键词')
    return
  }
  let patterns
  try {
    patterns = keywords.value.map(keyword => new RegExp(keyword))
  } catch {
    ElMessage.error('现有规则中包含无效的正则表达式，请修正规则后再测试')
    return
  }
  testResults.value = testTitles.value.split(/\r?\n/).map(title => title.trim()).filter(Boolean)
      .map((title, index) => ({title, index, matched: patterns.some(pattern => pattern.test(title))}))
  if (!testResults.value.length) ElMessage.info('请先输入用于测试的 RSS 标题')
}
</script>

<style scoped>
.reverse-match { display: grid; gap: 8px; }
.reverse-match-toggle { display: flex; align-items: center; gap: 8px; }
.reverse-match-miss { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.miss-select { width: min(100%, 240px); }
.reverse-match-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.reverse-match-add { display: flex; gap: 6px; }
.reverse-match-test-input { margin-top: 4px; }
.reverse-match-test-actions { display: flex; justify-content: flex-end; }
.reverse-match-results { display: grid; gap: 5px; }
.reverse-match-result { display: flex; align-items: center; gap: 8px; min-width: 0; }
.reverse-match-result-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>

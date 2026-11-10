<template>
  <div class="reverse-match full-width">
    <div class="reverse-match-toggle">
      <el-switch v-model="props.ani.reverseMatchEnabled"/>
      <el-text size="small" type="info">使用本订阅的反匹配规则</el-text>
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
      <el-alert :closable="false" show-icon type="info"
                title="每条规则由正则和目标字幕组组成。标题命中后，条目会归入该字幕组；多条命中时按列表顺序采用第一条。"/>
      <div class="reverse-rule-list">
        <el-card v-for="(rule, index) in rules" :key="rule.id || index" shadow="never" class="reverse-rule-card">
          <div class="reverse-rule-fields">
            <el-input v-model="rule.pattern" placeholder="标题正则，例如：\[字幕组A\].*作品名"/>
            <el-input v-model="rule.subgroup" placeholder="命中后归入的字幕组"/>
          </div>
          <div class="reverse-rule-actions">
            <el-button text bg circle icon="Top" :disabled="index === 0" aria-label="上移规则"
                       @click="moveRule(index, -1)"/>
            <el-button text bg circle icon="Bottom" :disabled="index === rules.length - 1" aria-label="下移规则"
                       @click="moveRule(index, 1)"/>
            <el-button text bg circle type="danger" icon="Delete" aria-label="删除规则"
                       @click="removeRule(index)"/>
          </div>
        </el-card>
        <el-empty v-if="!rules.length" description="尚未添加反匹配规则"/>
        <el-button class="add-rule" bg text type="primary" icon="Plus" @click="addRule">添加规则</el-button>
      </div>
      <el-input v-model="testTitles" class="reverse-match-test-input" :autosize="{ minRows: 3, maxRows: 7 }"
                placeholder="粘贴 RSS 标题，每行一条，用于测试规则和目标字幕组" type="textarea"/>
      <div class="reverse-match-test-actions">
        <el-button bg text icon="CircleCheck" type="primary" @click="runTest">测试标题</el-button>
      </div>
      <div v-if="testResults.length" class="reverse-match-results">
        <div v-for="result in testResults" :key="`${result.title}-${result.index}`" class="reverse-match-result">
          <el-tag :type="result.subgroup ? 'success' : 'info'" size="small">
            {{ result.subgroup ? `归入：${result.subgroup}` : '未命中' }}
          </el-tag>
          <el-text class="reverse-match-result-title" :title="result.title">{{ result.title }}</el-text>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import {computed, ref} from 'vue'
import {ElMessage} from 'element-plus'

const props = defineProps({ani: {type: Object, required: true}})
if (!Array.isArray(props.ani.reverseMatchRules)) props.ani.reverseMatchRules = []
if (!props.ani.reverseMatchMissBehavior) props.ani.reverseMatchMissBehavior = 'SKIP'
if (typeof props.ani.globalReverseMatch !== 'boolean') props.ani.globalReverseMatch = false

const rules = computed(() => props.ani.reverseMatchRules)
const testTitles = ref('')
const testResults = ref([])

const addRule = () => {
  rules.value.push({
    id: globalThis.crypto?.randomUUID?.() || `reverse-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    pattern: '',
    subgroup: ''
  })
}

const removeRule = index => {
  rules.value.splice(index, 1)
  testResults.value = []
}

const moveRule = (index, offset) => {
  const target = index + offset
  if (target < 0 || target >= rules.value.length) return
  const [rule] = rules.value.splice(index, 1)
  rules.value.splice(target, 0, rule)
}

const runTest = () => {
  if (!rules.value.length || rules.value.some(rule => !rule.pattern?.trim() || !rule.subgroup?.trim())) {
    ElMessage.warning('请为每条规则填写正则表达式和目标字幕组')
    return
  }
  let patterns
  try {
    patterns = rules.value.map(rule => ({rule, pattern: new RegExp(rule.pattern)}))
  } catch {
    ElMessage.error('规则中包含无效的正则表达式，请修正后再测试')
    return
  }
  testResults.value = testTitles.value.split(/\r?\n/).map(title => title.trim()).filter(Boolean)
      .map((title, index) => ({
        title,
        index,
        subgroup: patterns.find(({pattern}) => {
          pattern.lastIndex = 0
          return pattern.test(title)
        })?.rule.subgroup || ''
      }))
  if (!testResults.value.length) ElMessage.info('请先输入用于测试的 RSS 标题')
}
</script>

<style scoped>
.reverse-match { display: grid; gap: 8px; }
.reverse-match-toggle { display: flex; align-items: center; gap: 8px; }
.reverse-match-miss { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.miss-select { width: min(100%, 240px); }
.reverse-rule-list { display: grid; gap: 8px; }
.reverse-rule-card :deep(.el-card__body) { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.reverse-rule-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.reverse-rule-actions { display: flex; align-items: center; gap: 4px; }
.add-rule { justify-self: start; }
.reverse-match-test-input { margin-top: 4px; }
.reverse-match-test-actions { display: flex; justify-content: flex-end; }
.reverse-match-results { display: grid; gap: 5px; }
.reverse-match-result { display: flex; align-items: center; gap: 8px; min-width: 0; }
.reverse-match-result-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 600px) {
  .reverse-rule-card :deep(.el-card__body) { grid-template-columns: 1fr; }
  .reverse-rule-fields { grid-template-columns: 1fr; }
  .reverse-rule-actions { justify-content: flex-end; }
}
</style>

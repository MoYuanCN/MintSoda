<template>
  <div class="global-reverse-match">
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
      <el-empty v-if="!rules.length" description="尚未添加全局反匹配规则"/>
      <el-button class="add-rule" bg text type="primary" icon="Plus" @click="addRule">添加全局规则</el-button>
    </div>
    <el-input v-model="testTitles" :autosize="{minRows: 2, maxRows: 6}"
              placeholder="输入标题，每行一条，测试匹配结果和目标字幕组" type="textarea"/>
    <div class="test-actions">
      <el-button bg text icon="CircleCheck" type="primary" @click="runTest">测试标题</el-button>
    </div>
    <div v-if="testResults.length" class="test-results">
      <div v-for="result in testResults" :key="`${result.title}-${result.index}`" class="test-result">
        <el-tag :type="result.subgroup ? 'success' : 'info'" size="small">
          {{ result.subgroup ? `归入：${result.subgroup}` : '未命中' }}
        </el-tag>
        <el-text class="test-title" :title="result.title">{{ result.title }}</el-text>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, ref} from 'vue'
import {ElMessage} from 'element-plus'

const props = defineProps({config: {type: Object, required: true}})
if (!Array.isArray(props.config.globalReverseMatchRules)) props.config.globalReverseMatchRules = []
const rules = computed(() => props.config.globalReverseMatchRules)
const testTitles = ref('')
const testResults = ref([])

const addRule = () => {
  rules.value.push({
    id: globalThis.crypto?.randomUUID?.() || `global-reverse-${Date.now()}-${Math.random().toString(16).slice(2)}`,
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
  if (!testResults.value.length) ElMessage.info('请先输入用于测试的标题')
}
</script>

<style scoped>
.global-reverse-match { display: grid; gap: 8px; }
.reverse-rule-list { display: grid; gap: 8px; }
.reverse-rule-card :deep(.el-card__body) { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.reverse-rule-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.reverse-rule-actions { display: flex; align-items: center; gap: 4px; }
.add-rule { justify-self: start; }
.test-actions { display: flex; justify-content: flex-end; }
.test-results { display: grid; gap: 5px; }
.test-result { display: flex; align-items: center; gap: 8px; min-width: 0; }
.test-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 600px) {
  .reverse-rule-card :deep(.el-card__body) { grid-template-columns: 1fr; }
  .reverse-rule-fields { grid-template-columns: 1fr; }
  .reverse-rule-actions { justify-content: flex-end; }
}
</style>

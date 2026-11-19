<template>
  <div class="global-reverse-match">
    <div class="reverse-rule-list">
      <el-card v-for="(rule, index) in rules" :key="rule.id || index" shadow="never" class="reverse-rule-card">
        <div class="reverse-rule-fields">
          <el-input v-model="rule.pattern" :placeholder="$t('titleregexeggroupashowname')"/>
          <el-input v-model="rule.subgroup" :placeholder="$t('subgroupassignedonmatch')"/>
        </div>
        <div class="reverse-rule-actions">
          <el-button text bg circle icon="Top" :disabled="index === 0" :aria-label="$t('moveruleup')"
                     @click="moveRule(index, -1)"/>
          <el-button text bg circle icon="Bottom" :disabled="index === rules.length - 1" :aria-label="$t('moveruledown')"
                     @click="moveRule(index, 1)"/>
          <el-button text bg circle type="danger" icon="Delete" :aria-label="$t('deleterule')"
                     @click="removeRule(index)"/>
        </div>
      </el-card>
      <el-empty v-if="!rules.length" :description="$t('noglobalreversematchrulesyet')"/>
      <el-button class="add-rule" bg text type="primary" icon="Plus" @click="addRule">{{ $t('addglobalrule') }}</el-button>
    </div>
    <el-input v-model="testTitles" :autosize="{minRows: 2, maxRows: 6}"
              :placeholder="$t('entertitlesoneperlinetotestmatchesandtargets')" type="textarea"/>
    <div class="test-actions">
      <el-button bg text icon="CircleCheck" type="primary" @click="runTest">{{ $t('testtitles') }}</el-button>
    </div>
    <div v-if="testResults.length" class="test-results">
      <div v-for="result in testResults" :key="`${result.title}-${result.index}`" class="test-result">
        <el-tag :type="result.subgroup ? 'success' : 'info'" size="small">
          {{ result.subgroup ? $t('assignedto', {subgroup: result.subgroup}) : $t('nomatch') }}
        </el-tag>
        <el-text class="test-title" :title="result.title">{{ result.title }}</el-text>
      </div>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed, ref} from 'vue'
import {ElMessage} from 'element-plus'
const {t} = useI18n()
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
    ElMessage.warning(t('fillinaregexandtargetsubgroupforeveryrule'))
    return
  }
  let patterns
  try {
    patterns = rules.value.map(rule => ({rule, pattern: new RegExp(rule.pattern)}))
  } catch {
    ElMessage.error(t('arulecontainsaninvalidregularexpressionfixit'))
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
  if (!testResults.value.length) ElMessage.info(t('entertitlestotestfirst'))
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

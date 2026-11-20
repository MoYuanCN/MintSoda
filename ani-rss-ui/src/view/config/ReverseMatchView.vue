<template>
  <div class="reverse-match full-width">
    <el-alert :closable="false" show-icon type="info"
              :title="$t('setsubgrouptoreversemarkertorouteitems')"/>
    <div class="reverse-match-toggle">
      <el-switch v-model="props.ani.reverseMatchEnabled"/>
      <el-text size="small" type="info">{{ $t('usethissubscriptionsreversematchrules') }}</el-text>
    </div>
    <div class="reverse-match-toggle">
      <el-switch v-model="props.ani.globalReverseMatch"/>
      <el-text size="small" type="info">{{ $t('alsouseglobalreversematchrulesfromsettings') }}</el-text>
    </div>
    <div v-if="succeededSubgroups.length" class="reverse-match-succeeded">
      <el-text size="small" type="info">{{ $t('reversematchedsubgroups') }}</el-text>
      <el-table :data="succeededSubgroups" size="small">
        <el-table-column prop="subgroup" :label="$t('subgroup')" min-width="120"/>
        <el-table-column prop="episodes" :label="$t('episodes')" min-width="120"/>
        <el-table-column :label="$t('extraepisodeoffset')" width="160">
          <template #default="{row}">
            <el-input-number size="small" :model-value="offsetOf(row.subgroup)"
                             @update:model-value="value => setOffset(row.subgroup, value)"/>
          </template>
        </el-table-column>
      </el-table>
      <el-text size="small" type="info">{{ $t('reversematchedsubgroupoffsethelp') }}</el-text>
    </div>
    <el-text v-if="reverseMarker" size="small" type="success">{{ $t('subgroupisreversemarker') }}</el-text>
    <div v-if="reverseEnabled" class="reverse-match-miss">
      <el-text size="small">{{ $t('whenreversematchdoesnothit') }}</el-text>
      <el-select v-model="props.ani.reverseMatchMissBehavior" size="small" class="miss-select">
        <el-option value="" :label="$t('followglobaldefault')"/>
        <el-option value="SKIP" :label="$t('skipdonotdownload')"/>
        <el-option value="NORMAL" :label="$t('continuewithnormalrules')"/>
        <el-option value="ALLOW" :label="$t('allowasis')"/>
      </el-select>
    </div>
    <template v-if="reverseEnabled">
      <el-alert :closable="false" show-icon type="info"
                :title="$t('eachrulehasaregexandatargetsubgroupwhenatitl')"/>
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
        <el-empty v-if="!rules.length" :description="$t('noreversematchrulesyet')"/>
        <el-button class="add-rule" bg text type="primary" icon="Plus" @click="addRule">{{ $t('addrule') }}</el-button>
      </div>
      <el-input v-model="testTitles" class="reverse-match-test-input" :autosize="{ minRows: 3, maxRows: 7 }"
                :placeholder="$t('pastersstitlesoneperlinetotestrulesandtarget')" type="textarea"/>
      <div class="reverse-match-test-actions">
        <el-button bg text icon="CircleCheck" type="primary" @click="runTest">{{ $t('testtitles') }}</el-button>
      </div>
      <div v-if="testResults.length" class="reverse-match-results">
        <div v-for="result in testResults" :key="`${result.title}-${result.index}`" class="reverse-match-result">
          <el-tag :type="result.subgroup ? 'success' : 'info'" size="small">
            {{ result.subgroup ? $t('assignedto', {subgroup: result.subgroup}) : $t('nomatch') }}
          </el-tag>
          <el-text class="reverse-match-result-title" :title="result.title">{{ result.title }}</el-text>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed, ref} from 'vue'
import {ElMessage} from 'element-plus'
const {t} = useI18n()
const props = defineProps({ani: {type: Object, required: true}})
if (!Array.isArray(props.ani.reverseMatchRules)) props.ani.reverseMatchRules = []
if (props.ani.reverseMatchMissBehavior === undefined || props.ani.reverseMatchMissBehavior === null) {
  props.ani.reverseMatchMissBehavior = ''
}
if (typeof props.ani.globalReverseMatch !== 'boolean') props.ani.globalReverseMatch = false
if (!Array.isArray(props.ani.reverseMatchAssignments)) props.ani.reverseMatchAssignments = []
if (!props.ani.reverseMatchSubgroupOffsets || typeof props.ani.reverseMatchSubgroupOffsets !== 'object') {
  props.ani.reverseMatchSubgroupOffsets = {}
}

const rules = computed(() => props.ani.reverseMatchRules)

// 字幕组填成「反匹配」时同样按反匹配模式展示规则与未命中行为。
const reverseMarker = computed(() => {
  const value = String(props.ani.subgroup || '').trim()
  return value === '反匹配' || value.toUpperCase() === 'REVERSE_MATCH'
})

const reverseEnabled = computed(() => Boolean(props.ani.reverseMatchEnabled)
    || Boolean(props.ani.globalReverseMatch)
    || reverseMarker.value)

// 已命中的字幕组：按字幕组汇总集数与区间，供单独设置偏移。
const succeededSubgroups = computed(() => {
  const map = new Map()
  ;(props.ani.reverseMatchAssignments || []).forEach(item => {
    const subgroup = String(item?.subgroup || '').trim()
    if (!subgroup) return
    const episode = Number(item.episode)
    const entry = map.get(subgroup) || {subgroup, count: 0, min: Infinity, max: -Infinity}
    entry.count += 1
    if (Number.isFinite(episode)) {
      entry.min = Math.min(entry.min, episode)
      entry.max = Math.max(entry.max, episode)
    }
    map.set(subgroup, entry)
  })
  return [...map.values()].map(entry => ({
    subgroup: entry.subgroup,
    episodes: Number.isFinite(entry.min)
        ? (entry.min === entry.max ? String(entry.min) : `${entry.min} - ${entry.max} (${entry.count})`)
        : String(entry.count)
  }))
})

const offsetOf = subgroup => Number(props.ani.reverseMatchSubgroupOffsets?.[subgroup] || 0)

const setOffset = (subgroup, value) => {
  const offsets = props.ani.reverseMatchSubgroupOffsets || (props.ani.reverseMatchSubgroupOffsets = {})
  const number = Number(value)
  if (!Number.isFinite(number) || number === 0) {
    delete offsets[subgroup]
    return
  }
  offsets[subgroup] = number
}
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
  if (!testResults.value.length) ElMessage.info(t('enterrsstitlestotestfirst'))
}
</script>

<style scoped>
.reverse-match { display: grid; gap: 8px; }
.reverse-match-toggle { display: flex; align-items: center; gap: 8px; }
.reverse-match-miss { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.reverse-match-succeeded { display: grid; gap: 6px; }
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

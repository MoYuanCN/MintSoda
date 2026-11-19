<template>
  <div class="subscription-page app-page-layout">
    <AddView ref="addRef"/>
    <CollectionView ref="collectionRef"/>
    <ManageView ref="manageRef"/>
    <PageHeaderView :title="$t('subscriptions-2')" :subtitle="t('nsubscriptions', {n: subscriptionTotal})"/>
    <div class="subscription-body app-page-content app-page-padding">
      <div class="subscription-toolbar">
        <div class="subscription-filters">
          <el-input
              v-model="title"
              class="subscription-search"
              clearable
              :placeholder="$t('search')"
              prefix-icon="Search"
              @clear="changeFilterList"
              @input="changeFilterList"/>
          <el-select
              v-model:model-value="releaseDate"
              class="subscription-select"
              clearable
              :placeholder="$t('date')"
              @change="selectChange">
            <el-option v-for="it in releaseDateList"
                       :key="it"
                       :label="it"
                       :value="it"/>
          </el-select>
          <el-select
              v-model:model-value="enable"
              class="subscription-select"
              @change="selectChange">
            <el-option v-for="selectItem in enableSelect"
                       :key="selectItem.label"
                       :label="selectItem.label"
                       :value="selectItem.label"/>
          </el-select>
          <el-select
              v-model:model-value="sortType"
              class="subscription-select subscription-sort"
              :placeholder="$t('sort')"
              @change="changeSort">
            <el-option v-for="sortItem in sortOptions"
                       :key="sortItem.value"
                       :label="sortItem.label"
                       :value="sortItem.value"/>
          </el-select>
        </div>
        <div class="subscription-actions">
          <el-dropdown trigger="click">
            <el-button :aria-label="$t('add')" type="primary" class="auto-button" icon="Plus">
              {{ $t('add') }}
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="addRef?.show">
                  {{ $t('addsubscription') }}
                </el-dropdown-item>
                <el-dropdown-item @click="collectionRef?.show">
                  {{ $t('addcollection') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button :aria-label="$t('refresh')"
                     :loading="refreshLoading"
                     class="auto-button"
                     icon="Refresh"
                     @click="confirmRefreshAni">
            {{ $t('refresh') }}
          </el-button>
          <el-button :aria-label="$t('manage')" @click="manageRef?.show" class="auto-button" icon="Fold">
            {{ $t('manage') }}
          </el-button>
        </div>
      </div>
      <SubscriptionListView
          ref="listRef"
          :filter="filter"
          :title="title"
          @loaded="listLoaded"/>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {onMounted, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {useLocalStorage} from "@vueuse/core";
import {subscriptionSort} from "@/js/global.js";
import SubscriptionListView from "@/view/home/SubscriptionListView.vue";
import AddView from "@/view/home/AddView.vue";
import CollectionView from "@/view/home/CollectionView.vue";
import ManageView from "@/view/home/ManageView.vue";
import PageHeaderView from "@/view/custom/PageHeaderView.vue";
import * as http from "@/js/http.js";
const {t} = useI18n()
const listRef = ref()
const addRef = ref()
const collectionRef = ref()
const manageRef = ref()
const title = ref('')
const releaseDate = ref('')
const releaseDateList = ref([])
const subscriptionTotal = ref(0)
const refreshLoading = ref(false)
const enable = useLocalStorage('select-enable', t('enabled'))
const sortType = subscriptionSort
const sortOptions = [
  {label: t('byscore'), value: 'SCORE'},
  {label: t('bypinyin'), value: 'PINYIN'},
  {label: t('bytitle'), value: 'TITLE'},
  {label: t('byupdatetime'), value: 'DOWNLOAD_TIME'},
  {label: t('byupdateweekday'), value: 'UPDATE_WEEK'},
  {label: t('bystandbyrsscount'), value: 'STANDBY_RSS'}
]

const changeSort = () => {
  listRef.value?.getList()
}
const enableSelect = [
  {
    label: t('all'),
    fun: () => true
  },
  {
    label: t('enabled'),
    fun: item => item.enable
  },
  {
    label: t('notenabled'),
    fun: item => !item.enable
  }
]
const filter = ref(() => true)

const changeFilterList = () => {
  listRef.value?.changeFilterList(title.value)
}

const selectChange = () => {
  filter.value = it => {
    const selectedEnable = enableSelect.find(item => item.label === enable.value)
    if (selectedEnable && !selectedEnable.fun(it)) {
      return false
    }
    if (!releaseDate.value) {
      return true
    }

    return releaseDate.value === it.releaseDate.replace(/-\d{2}$/, '')
  }
  changeFilterList()
}

const listLoaded = data => {
  releaseDateList.value = data.releaseDateList || []
  subscriptionTotal.value = data.total || 0
}

const refreshAni = () => {
  refreshLoading.value = true
  http.refreshAll()
      .then(res => {
        ElMessage.success(res.message)
        listRef.value?.getList()
      })
      .finally(() => {
        refreshLoading.value = false
      })
}

// 全量刷新可能触发大量订阅请求，执行前通过模态弹窗进行确认。
const confirmRefreshAni = () => {
  ElMessageBox.confirm(
      t('allsubscriptionswillberefreshednowcontinue'),
      t('refreshallsubscriptions'),
      {
        confirmButtonText: t('confirmrefresh'),
        confirmButtonClass: 'is-text is-has-bg el-button--primary',
        cancelButtonText: t('cancel'),
        cancelButtonClass: 'is-text is-has-bg',
        type: 'warning'
      }
  ).then(refreshAni).catch(() => {
  })
}

onMounted(() => {
  selectChange()
})
</script>

<style scoped>
.subscription-toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 10px;
}

.subscription-filters,
.subscription-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.subscription-filters {
  flex: 1;
}

.subscription-actions {
  flex-shrink: 0;
}

.subscription-actions > * {
  margin: 0 !important;
}

.subscription-actions :deep(.el-button) {
  margin: 0;
}

.subscription-search {
  width: 220px;
}

.subscription-select {
  width: 128px;
}

.subscription-sort {
  width: 148px;
}

@media (max-width: 900px) {
  .subscription-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .subscription-filters,
  .subscription-actions {
    width: 100%;
  }

  .subscription-filters {
    flex-wrap: wrap;
  }

  .subscription-actions {
    justify-content: flex-end;
  }

  .subscription-search {
    width: 100%;
  }

  .subscription-select {
    flex: 1 1 120px;
  }
}

@media (max-width: 560px) {
  .subscription-toolbar {
    padding-bottom: 8px;
  }

  .subscription-actions {
    justify-content: flex-end;
    gap: 4px;
  }
}
</style>

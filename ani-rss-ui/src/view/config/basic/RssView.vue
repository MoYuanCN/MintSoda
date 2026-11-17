<template>
  <SettingsItem label="RSS开关">
    <el-switch v-model:model-value="props.config.rss"/>
  </SettingsItem>
  <SettingsItem label="RSS间隔">
    <el-input-number v-model:model-value="props.config.rssSleepSeconds" :disabled="!props.config.rss" :min="1">
      <template #suffix>
        <span>秒</span>
      </template>
    </el-input-number>
  </SettingsItem>
  <SettingsItem label="RSS超时">
    <div>
      <el-input-number v-model:model-value="props.config['rssTimeout']"
                       :max="120" :min="1">
        <template #suffix>
          <span>秒</span>
        </template>
      </el-input-number>
      <br>
      <el-text class="mx-1" size="small">
        单个 RSS 请求的最长等待时间；超时后若已有成功内容会继续沿用旧内容
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem label="RSS并发">
    <div>
      <el-input-number v-model:model-value="props.config['rssConcurrency']"
                       :max="16" :min="1">
        <template #suffix>
          <span>个</span>
        </template>
      </el-input-number>
      <br>
      <el-text class="mx-1" size="small">
        每轮刷新同时拉取的 RSS 数量。间隔很短时建议保持 2-4，避免给 RSS 站点造成压力
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem label="刷新方式">
    <el-text class="mx-1" size="small">
      每轮以固定间隔开始：间隔从上轮开始时计算，处理耗时不会叠加到等待时间。相同 RSS 地址在同一轮只请求一次，并按 ETag 复用未变化的内容。
    </el-text>
  </SettingsItem>
  <SettingsItem label="自动跳过">
    <div class="full-width">
      <el-switch v-model:model-value="props.config.fileExist" :disabled="!config.rename"/>
      <br>
      <el-text class="mx-1" size="small">
        文件已下载自动跳过，此选项必须启用自动重命名。确保下载器路径与本程序配置的保存路径一致。
        &nbsp;
        <el-link
            class="text-extra-small"
            type="primary"
            href="https://github.com/MoYuanCN/ani-rss-Yuri/blob/main/docs/USER_GUIDE.md"
            target="_blank">
          详细说明
        </el-link>
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem label="自动禁用订阅">
    <div class="full-width">
      <el-switch v-model:model-value="props.config.autoDisabled"/>
      <br>
      <el-text class="mx-1" size="small">
        根据 Bangumi 获取总集数 当所有集数都已下载时自动禁用该订阅
      </el-text>
      <div>
        <el-checkbox v-model="props.config['completed']"
                     :disabled="!props.config.autoDisabled"
                     label="订阅完结迁移"/>
      </div>
      <div>
        <el-input v-model="props.config['completedPathTemplate']"
                  :disabled="!props.config.autoDisabled || !props.config['completed']"/>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem label="自动更新总集数">
    <div class="full-width">
      <el-switch v-model="props.config.updateTotalEpisodeNumber"/>
      <div>
        <el-checkbox v-model="props.config.forceUpdateTotalEpisodeNumber"
                     :disabled="!props.config.updateTotalEpisodeNumber"
                     class="el-checkbox-danger"
                     label="强制更新"/>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem label="自动跳过X.5集">
    <el-switch v-model:model-value="props.config.skip5"/>
  </SettingsItem>
  <SettingsItem label="遗漏检测">
    <div>
      <div>
        <el-switch v-model:model-value="props.config.omit"/>
      </div>
      <el-text class="mx-1" size="small">
        总开关 若检测到RSS中集数出现遗漏会发送通知
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem label="备用RSS">
    <div class="full-width">
      <div>
        <el-switch v-model:model-value="props.config.standbyRss"/>
      </div>
      <div>
        <el-checkbox v-model="props.config['coexist']" :disabled="!props.config.standbyRss"
                     label="多字幕组共存模式"/>
      </div>
      <div class="flex full-width justify-end">
        <el-link
            type="primary"
            href="https://github.com/MoYuanCN/ani-rss-Yuri/blob/main/docs/USER_GUIDE.md"
            target="_blank">
          详细说明
        </el-link>
      </div>
    </div>
  </SettingsItem>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ElText} from "element-plus";

let props = defineProps(['config'])
</script>

<style scoped>
.text-extra-small {
  font-size: var(--el-font-size-extra-small);
}

.justify-end {
  justify-content: end;
}
</style>

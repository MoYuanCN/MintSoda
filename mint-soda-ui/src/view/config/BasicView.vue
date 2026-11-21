<template>
  <el-collapse v-model:model-value="activeName" accordion>
    <el-collapse-item name="page" :title="$t('pagesettings')">
      <PageView :config="props.config"/>
    </el-collapse-item>
    <el-collapse-item name="add" :title="$t('addsubscription')">
      <AddView :config="props.config"/>
    </el-collapse-item>
    <el-collapse-item name="rename" :title="$t('renamesettings')">
      <RenameView :config="props.config"/>
    </el-collapse-item>
    <el-collapse-item name="rss" :title="$t('rsssettings')">
      <RssView :config="props.config"/>
    </el-collapse-item>
    <el-collapse-item name="bangumi" title="Bangumi Token">
      <BangumiView :config="props.config"/>
    </el-collapse-item>
    <el-collapse-item name="other" :title="$t('other')">
      <OtherView :config="props.config"/>
    </el-collapse-item>
    <el-collapse-item name="backup" :title="$t('backup')">
      <BackupView :config="props.config"/>
    </el-collapse-item>
  </el-collapse>
</template>

<script setup>
import {ref} from "vue";
import PageView from "@/view/config/basic/PageView.vue";
import AddView from "@/view/config/basic/AddView.vue";
import RenameView from "@/view/config/basic/RenameView.vue";
import RssView from "@/view/config/basic/RssView.vue";
import OtherView from "@/view/config/basic/OtherView.vue";
import BangumiView from "@/view/config/basic/BangumiView.vue";
import BackupView from "./basic/BackupView.vue";

let activeName = ref('page')

let props = defineProps(['config'])
</script>

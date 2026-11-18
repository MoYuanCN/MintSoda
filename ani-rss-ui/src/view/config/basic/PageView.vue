<template>
  <el-dialog v-model="jsDialogVisible" align-center center title="自定义JS" width="800">
    <el-input v-model:model-value="props.config['customJs']" :rows="6"
              type="textarea"/>
    <div class="flex justify-end full-width mt-8">
      <el-link type="primary" href="https://github.com/MoYuanCN/ani-rss-Yuri/tree/main/ani-rss-ui/public/api"
               target="_blank">更多CSS
      </el-link>
    </div>
    <div class="flex justify-end full-width mt-8">
      <el-button bg icon="Close" text @click="jsDialogVisible = false">关闭</el-button>
    </div>
  </el-dialog>
  <el-dialog v-model="cssDialogVisible" align-center center title="自定义CSS" width="800">
    <el-input v-model:model-value="props.config['customCss']" :rows="6"
              type="textarea"/>
    <div class="flex justify-end full-width mt-8">
      <el-button bg icon="Close" text @click="cssDialogVisible = false">关闭</el-button>
    </div>
  </el-dialog>
  <SettingsItem label="外观">
    <el-radio-group v-model="store">
      <el-radio-button label="自动" value="auto">
        <template #default>
          <el-icon>
            <Adjust/>
          </el-icon>
        </template>
      </el-radio-button>
      <el-radio-button label="浅色" value="light">
        <el-icon>
          <Sun/>
        </el-icon>
      </el-radio-button>
      <el-radio-button label="深色" value="dark">
        <el-icon>
          <Moon/>
        </el-icon>
      </el-radio-button>
    </el-radio-group>
  </SettingsItem>
  <SettingsItem label="主题色">
    <div class="theme-color-row">
      <el-color-picker v-model="props.config.accentColor" :predefine="predefineColors"
                       @change="colorChange(props.config.accentColor || '#409eff')"/>
      <el-button bg text @click="resetAccentColor">恢复默认</el-button>
    </div>
    <el-checkbox v-model="props.config.themeDynamicColor" label="从背景图动态取色"/>
    <el-text size="small" type="info">设置主题色后优先使用；未设置且开启动态取色时，从应用或登录背景图提取主色。</el-text>
  </SettingsItem>
  <SettingsItem label="界面质感">
    <div class="theme-preset-row">
      <el-button v-for="preset in qualityPresets" :key="preset.name" bg text
                 @click="applyPreset(preset)">{{ preset.name }}</el-button>
    </div>
  </SettingsItem>
  <SettingsItem label="应用背景图">
    <UiAssetPicker :config="props.config" field="appBackgroundImage"/>
  </SettingsItem>
  <SettingsItem label="登录背景图">
    <UiAssetPicker :config="props.config" field="loginBackgroundImage"/>
  </SettingsItem>
  <SettingsItem label="登录 Logo">
    <UiAssetPicker :config="props.config" field="loginLogoImage"/>
  </SettingsItem>
  <SettingsItem label="应用 Logo">
    <UiAssetPicker :config="props.config" field="appLogoImage"/>
  </SettingsItem>
  <SettingsItem label="卡片透明度">
    <el-slider v-model="props.config.cardOpacity" :min="0" :max="100" show-input/>
  </SettingsItem>
  <SettingsItem label="卡片模糊度">
    <el-slider v-model="props.config.cardBlur" :min="0" :max="40" show-input/>
  </SettingsItem>
  <SettingsItem label="导航栏">
    <div class="theme-slider-pair">
      <div>
        <el-text size="small">不透明度</el-text>
        <el-slider v-model="props.config.navOpacity" :min="0" :max="100" show-input/>
      </div>
      <div>
        <el-text size="small">模糊度</el-text>
        <el-slider v-model="props.config.navBlur" :min="0" :max="40" show-input/>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem label="弹窗">
    <div class="theme-slider-pair">
      <div>
        <el-text size="small">不透明度</el-text>
        <el-slider v-model="props.config.dialogOpacity" :min="0" :max="100" show-input/>
      </div>
      <div>
        <el-text size="small">模糊度</el-text>
        <el-slider v-model="props.config.dialogBlur" :min="0" :max="40" show-input/>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem label="圆角大小">
    <el-slider v-model="props.config.appCornerRadius" :min="0" :max="40" show-input/>
  </SettingsItem>
  <SettingsItem label="默认排序">
    <el-select v-model="props.config['sortType']" class="width-200">
      <el-option value="SCORE" label="评分"/>
      <el-option value="PINYIN" label="拼音"/>
      <el-option value="TITLE" label="标题"/>
      <el-option value="DOWNLOAD_TIME" label="更新时间"/>
      <el-option value="UPDATE_WEEK" label="更新星期"/>
      <el-option value="STANDBY_RSS" label="备用RSS数量"/>
    </el-select>
    <el-text size="small" type="info">订阅页顶部可临时切换排序，不修改此默认值。</el-text>
  </SettingsItem>
  <SettingsItem label="点击封面">
    <el-select v-model="coverClickAction" class="width-150">
      <el-option label="编辑订阅" value="edit"/>
      <el-option label="编辑封面" value="cover"/>
    </el-select>
  </SettingsItem>
  <SettingsItem label="启动页">
    <el-select v-model="startupPage" class="width-150">
      <el-option label="首页" value="/home"/>
      <el-option label="订阅页" value="/subscriptions"/>
    </el-select>
  </SettingsItem>
  <SettingsItem label="最大内容宽度">
    <el-input-number v-model="maxContentWidth"
                     :min="1200">
      <template #suffix>
        <span>px</span>
      </template>
    </el-input-number>
  </SettingsItem>
  <SettingsItem label="其他">
    <el-checkbox v-model="showScore" label="显示评分"/>
    <el-checkbox v-model="showWeek" label="按星期展示"/>
    <el-checkbox v-model="showLastDownloadTime" label="显示更新时间"/>
  </SettingsItem>
  <SettingsItem label="自定义">
    <el-button bg @click="jsDialogVisible = true">
      <template #icon>
        <Js/>
      </template>
      JavaScript
    </el-button>
    <el-button bg @click="cssDialogVisible = true">
      <template #icon>
        <Css3Alt/>
      </template>
      CSS
    </el-button>
  </SettingsItem>
  <SettingsItem label="WebUI">
    <UploadView url="api/webui/upload" :callback="callback" :extensions="['zip']">
      <el-button bg icon="Upload">选择文件并上传</el-button>
    </UploadView>
  </SettingsItem>
</template>

<script setup>
import {ref} from "vue";
import {Adjust, Css3Alt, Js, Moon, Sun} from "@vicons/fa";
import {
  colorChange,
  coverClickAction,
  maxContentWidth,
  showLastDownloadTime,
  showScore,
  showWeek,
  startupPage,
  store,
} from "@/js/global.js";
import {ElMessage} from "element-plus";
import UploadView from "@/view/custom/UploadView.vue";
import SettingsItem from "@/view/custom/SettingsItem.vue";
import UiAssetPicker from "@/view/config/UiAssetPicker.vue";
import {watch} from "vue";
import {applyUiCustomization} from "@/js/global.js";

let predefineColors = ref([
  '#409eff', '#109D58', '#BF3545', '#CB7574',
  '#9AAEC7', '#2EC5B6', '#1C1C1C', '#F7B1A9',
  '#B18874', '#E9BA86', '#F68F6C', '#F0458B',
  '#C35653', '#40494E', '#6F0000', '#8D3647',
  '#E6C5D0', '#2377B3', '#49312D', '#7C9AB6',
  '#A5B18D', '#E8662A', '#AB5D50',
  // 浅色清新主题
  '#6FA8DC', '#F4A6B8', '#7BC8A4', '#F6C177',
  '#9DBFEF', '#E8A0C8', '#8ED1C4', '#B7A6E8'
])

// 浅色清新主题预设：只修改已有的外观字段，不新增配置项、不产生额外文件。
const qualityPresets = [
  {
    name: '蓝白玻璃',
    accentColor: '#5B8FF9',
    cardOpacity: 78, cardBlur: 14,
    navOpacity: 72, navBlur: 18,
    dialogOpacity: 82, dialogBlur: 16,
    appCornerRadius: 16
  },
  {
    name: '樱花粉白',
    accentColor: '#E48FB2',
    cardOpacity: 82, cardBlur: 18,
    navOpacity: 78, navBlur: 20,
    dialogOpacity: 86, dialogBlur: 18,
    appCornerRadius: 18
  },
  {
    name: '薄荷青白',
    accentColor: '#4FB8A8',
    cardOpacity: 88, cardBlur: 10,
    navOpacity: 84, navBlur: 12,
    dialogOpacity: 92, dialogBlur: 10,
    appCornerRadius: 12
  },
  {
    name: '磨砂雾面',
    accentColor: '',
    cardOpacity: 88, cardBlur: 24,
    navOpacity: 86, navBlur: 24,
    dialogOpacity: 90, dialogBlur: 24,
    appCornerRadius: 10
  },
  {
    name: '清透极简',
    accentColor: '#409eff',
    cardOpacity: 100, cardBlur: 0,
    navOpacity: 100, navBlur: 0,
    dialogOpacity: 100, dialogBlur: 0,
    appCornerRadius: 8
  }
]

const applyPreset = preset => {
  Object.keys(preset).forEach(key => {
    if (key !== 'name') props.config[key] = preset[key]
  })
  // 预设里的 accentColor 为空时交回动态取色或默认色。
  props.config.themeDynamicColor = false
  applyUiCustomization(props.config)
}

const resetAccentColor = () => {
  props.config.accentColor = ''
  props.config.themeDynamicColor = false
  colorChange('#409eff')
}

let jsDialogVisible = ref(false)
let cssDialogVisible = ref(false)

let callback = res => {
  let {code, message} = res
  if (code === 200) {
    ElMessage.success(message)
    return
  }
  ElMessage.error(message)
}

let props = defineProps(['config'])

watch(() => [
  props.config.appBackgroundImage,
  props.config.loginBackgroundImage,
  props.config.cardOpacity,
  props.config.cardBlur,
  props.config.appCornerRadius,
  props.config.accentColor,
  props.config.themeDynamicColor,
  props.config.navOpacity,
  props.config.navBlur,
  props.config.dialogOpacity,
  props.config.dialogBlur
], () => applyUiCustomization(props.config), {immediate: true})
</script>

<style scoped>
.theme-color-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.theme-preset-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.theme-slider-pair {
  display: grid;
  gap: 8px;
  width: 100%;
}

.justify-end {
  justify-content: end;
}

.mt-8 {
  margin-top: 8px;
}
</style>

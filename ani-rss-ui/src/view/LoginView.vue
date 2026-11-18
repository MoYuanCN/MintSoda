<template>
  <div class="flex-center content">
    <div id="login-page" class="flex-center">
      <div id="form">
        <div style="text-align: center;">
          <img :src="loginLogo" height="80" width="80" alt="ANI-RSS-Yuri"/>
        </div>
        <h2 class="title-h2">ANI-RSS-Yuri</h2>
        <el-form @submit.prevent
                 @keyup.enter="login">
          <el-form-item>
            <el-input v-model.trim="user.username"
                      placeholder="用户名" autocomplete="username">
              <template #prefix>
                <el-icon class="el-input__icon">
                  <User/>
                </el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-input v-model.trim="user.password" show-password
                      placeholder="密码" autocomplete="current-password">
              <template #prefix>
                <el-icon class="el-input__icon">
                  <Key/>
                </el-icon>
              </template>
            </el-input>
          </el-form-item>
          <div class="flex-center action">
            <el-checkbox v-model:model-value="rememberThePassword.remember">记住密码</el-checkbox>
            <el-button @click="login" :loading="loading" icon="Right">登录</el-button>
          </div>
        </el-form>
      </div>
    </div>
    <div class="footer">
      <el-link type="default"
               href="https://github.com/MoYuanCN/ani-rss-Yuri"
               target="_blank">
        ANI-RSS-Yuri
      </el-link>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from "vue";
import * as http from "@/js/http.js";
import {Key} from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import {authorization, rememberThePassword} from "@/js/global.js";
import {uiCustomization, uiAssetUrl} from "@/js/global.js";

let loading = ref(false)
const loginLogo = computed(() => uiCustomization.value.loginLogoImage
    ? uiAssetUrl(uiCustomization.value.loginLogoImage)
    : '/icon.png')

let user = ref({
  username: '',
  password: ''
})

/**
 * 登录
 */
let login = () => {
  let {username, password} = user.value;

  if (!password || !username) {
    ElMessage.error('请输入账号与密码')
    return
  }

  loading.value = true

  http.login(user.value)
      .then(res => {
        // 记住密码
        if (rememberThePassword.value.remember) {
          rememberThePassword.value.username = username
          rememberThePassword.value.password = password
        } else {
          rememberThePassword.value.username = ''
          rememberThePassword.value.password = ''
        }

        authorization.value = res.data
      })
      .finally(() => {
        loading.value = false
      })
}

/**
 * 测试是否处于白名单
 */
let test = () => {
  if (authorization.value) {
    return
  }
  http.testIpWhitelist()
      .then(res => {
        if (res.code === 200) {
          authorization.value = new Date().getTime() + '';
          return
        }
        authorization.value = ''
      })
}

onMounted(() => {
  test()
  let {remember, username, password} = rememberThePassword.value;
  if (remember && username && password) {
    user.value.username = username
    user.value.password = password
  }
})

</script>

<style scoped>
.content {
  width: 100%;
  height: 100%;
  flex-flow: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
  background-color: var(--body-background-color);
  /* 背景单独成层，模糊与透明度不影响表单本身。 */
  isolation: isolate;
}

.content::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: var(--login-background-image);
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  opacity: var(--app-background-opacity);
  filter: blur(var(--app-background-blur));
  transform: scale(1.04);
  pointer-events: none;
}

#form {
  max-width: 240px;
  padding: 24px 20px;
  border-radius: var(--app-corner-radius);
  background-color: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
  backdrop-filter: blur(var(--app-card-blur));
}

.title-h2 {
  text-align: center;
  margin-bottom: 32px;
}

el-input {
  width: 200px;
}

.action {
  width: 100%;
  justify-content: space-between;
}

.footer {
  margin-bottom: 16px;
}

@media (max-width: 450px) {
  #form {
    width: 80%;
  }
}

#login-page {
  flex: 1;
  width: 100%;
}
</style>

<template>
  <div class="login-shell">
    <div class="login-backdrop" aria-hidden="true"></div>

    <div class="login-locale">
      <LocaleSwitcher/>
    </div>

    <main class="login-main">
      <section class="login-card">
        <header class="login-head">
          <div class="login-logo">
            <img :src="loginLogo" alt="MintSoda"/>
          </div>
          <h1 class="login-title">MintSoda</h1>
          <p class="login-subtitle">{{ $t('loginsubtitle') }}</p>
        </header>

        <el-form class="login-form" @submit.prevent @keyup.enter="login">
          <label class="login-field">
            <span class="login-field-label">{{ $t('username') }}</span>
            <el-input v-model.trim="user.username" size="large"
                      :placeholder="$t('username')" autocomplete="username">
              <template #prefix>
                <el-icon><User/></el-icon>
              </template>
            </el-input>
          </label>

          <label class="login-field">
            <span class="login-field-label">{{ $t('password') }}</span>
            <el-input v-model.trim="user.password" size="large" show-password
                      :placeholder="$t('password')" autocomplete="current-password">
              <template #prefix>
                <el-icon><Lock/></el-icon>
              </template>
            </el-input>
          </label>

          <div class="login-options">
            <el-checkbox v-model="rememberThePassword.remember" class="login-remember">
              {{ $t('rememberpassword') }}
            </el-checkbox>
          </div>

          <el-button class="login-submit" type="primary" size="large"
                     :loading="loading" native-type="submit" @click="login">
            {{ $t('signin') }}
          </el-button>
        </el-form>
      </section>
    </main>

    <footer class="login-footer">
      <el-link type="default" href="https://github.com/MoYuanCN/MintSoda" target="_blank">
        MintSoda
      </el-link>
    </footer>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed, onMounted, ref} from "vue";
import * as http from "@/js/http.js";
import {Lock, User} from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import {authorization, rememberThePassword} from "@/js/global.js";
import {uiCustomization, uiAssetUrl} from "@/js/global.js";
import LocaleSwitcher from "@/view/custom/LocaleSwitcher.vue";
const {t} = useI18n()
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
    ElMessage.error(t('pleaseenteryourusernameandpassword'))
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
.login-shell {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 100%;
  overflow: hidden;
  background-color: var(--body-background-color);
  isolation: isolate;
}

/* 背景单独成层，模糊与透明度只作用于图片。 */
.login-backdrop {
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: var(--login-background-image);
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
  opacity: var(--app-background-opacity);
  filter: blur(var(--app-background-blur));
  transform: scale(1.04);
  pointer-events: none;
}

.login-locale {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 2;
}

.login-main {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 32px 20px;
  box-sizing: border-box;
}

.login-card {
  width: min(380px, 100%);
  padding: 40px 36px 32px;
  border-radius: calc(var(--app-corner-radius) + 6px);
  border: 1px solid color-mix(in srgb, var(--el-border-color) 60%, transparent);
  background-color: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
  backdrop-filter: blur(var(--app-card-blur));
  box-shadow: 0 24px 60px -28px rgba(15, 23, 42, .45);
  box-sizing: border-box;
}

.login-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin-bottom: 28px;
  text-align: center;
}

.login-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 76px;
  height: 76px;
  margin-bottom: 8px;
  border-radius: 22px;
  background: color-mix(in srgb, var(--el-color-primary) 12%, transparent);
}

.login-logo img {
  width: 56px;
  height: 56px;
  object-fit: contain;
}

.login-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: .01em;
  color: var(--el-text-color-primary);
}

.login-subtitle {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.login-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: text;
}

.login-field-label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: .02em;
  color: var(--el-text-color-regular);
}

.login-field :deep(.el-input__wrapper) {
  padding: 4px 14px;
}

/* 选项行与登录按钮各占一行，避免长文案与按钮挤在一起。 */
.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 24px;
}

.login-remember {
  height: auto;
  white-space: normal;
}

.login-submit {
  width: 100%;
  margin-top: 4px;
  font-weight: 600;
  letter-spacing: .02em;
}

.login-footer {
  padding-bottom: 24px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 480px) {
  .login-main {
    padding: 24px 16px;
  }

  .login-card {
    padding: 32px 22px 26px;
  }

  .login-locale {
    top: 14px;
    right: 14px;
  }
}
</style>

<template>
  <div class="flex-center content">
    <div class="flex-center setup-page">
      <div class="setup-form">
        <h2 class="title-h2">初始化登录账号</h2>
        <el-alert class="setup-hint" :closable="false" show-icon type="info"
                  title="首次启动需要设置登录凭据，完成后请妥善保存。"/>
        <el-form label-position="top" @submit.prevent @keyup.enter="submit">
          <el-form-item label="用户名">
            <el-input v-model.trim="form.username" autocomplete="username" placeholder="3-32 位字母、数字、下划线或连字符"/>
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password
                      autocomplete="new-password" :placeholder="`至少 ${passwordMinLength} 位`"/>
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password"/>
          </el-form-item>
          <el-alert v-if="error" class="setup-error" :closable="false" show-icon type="error" :title="error"/>
          <el-text class="setup-tips" size="small">
            密码至少 {{ passwordMinLength }} 位，且需包含大写字母、小写字母、数字、符号中的至少两类。
          </el-text>
          <el-button class="setup-submit" type="primary" :loading="loading" @click="submit">
            完成初始化
          </el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import {onMounted, reactive, ref} from "vue";
import {ElMessage} from "element-plus";
import * as http from "@/js/http.js";

const passwordMinLength = ref(8)
const loading = ref(false)
const error = ref('')

const form = reactive({
  username: '',
  password: '',
  confirm: ''
})

const checkPassword = value => {
  if (!value) return '密码不能为空'
  if (value.length < passwordMinLength.value) return `密码至少需要 ${passwordMinLength.value} 位`
  let types = 0
  if (/[a-z]/.test(value)) types++
  if (/[A-Z]/.test(value)) types++
  if (/\d/.test(value)) types++
  if (/[^A-Za-z0-9]/.test(value)) types++
  if (types < 2) return '密码需包含大写字母、小写字母、数字、符号中的至少两类'
  return ''
}

const submit = () => {
  error.value = ''
  if (!/^[A-Za-z0-9_-]{3,32}$/.test(form.username)) {
    error.value = '用户名需为 3-32 位字母、数字、下划线或连字符'
    return
  }
  const passwordError = checkPassword(form.password)
  if (passwordError) {
    error.value = passwordError
    return
  }
  if (form.password !== form.confirm) {
    error.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  http.setup({username: form.username, password: form.password})
      .then(res => {
        ElMessage.success(res.message)
        setTimeout(() => location.reload(), 600)
      })
      .catch(e => {
        error.value = e.message || '初始化失败'
      })
      .finally(() => {
        loading.value = false
      })
}

onMounted(() => {
  http.setupStatus()
      .then(res => {
        if (!res.data?.required) {
          location.reload()
          return
        }
        passwordMinLength.value = res.data.passwordMinLength || 8
      })
      .catch(() => {})
})
</script>

<style scoped>
.content {
  width: 100%;
  height: 100%;
  flex-flow: column;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background-color: var(--body-background-color);
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

.setup-page {
  width: 100%;
}

.setup-form {
  width: min(360px, 86vw);
  padding: 24px 20px;
  border-radius: var(--app-corner-radius);
  background-color: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
  backdrop-filter: blur(var(--app-card-blur));
}

.title-h2 {
  text-align: center;
  margin-bottom: 16px;
}

.setup-hint {
  margin-bottom: 16px;
}

.setup-error {
  margin-bottom: 8px;
}

.setup-tips {
  display: block;
  margin: 4px 0 16px;
  color: var(--el-text-color-secondary);
}

.setup-submit {
  width: 100%;
}
</style>

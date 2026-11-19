<template>
  <div class="flex-center content">
    <div class="locale-corner">
      <LocaleSwitcher/>
    </div>
    <div class="flex-center setup-page">
      <div class="setup-form">
        <h2 class="title-h2">{{ $t('createadministratoraccount') }}</h2>
        <el-alert class="setup-hint" :closable="false" show-icon type="info"
                  :title="$t('setupyoursignincredentialsonfirstlaunchandke')"/>
        <el-form label-position="top" @submit.prevent @keyup.enter="submit">
          <el-form-item :label="$t('username')">
            <el-input v-model.trim="form.username" autocomplete="username" :placeholder="$t('332lettersdigitsunderscoresorhyphens')"/>
          </el-form-item>
          <el-form-item :label="$t('password')">
            <el-input v-model="form.password" type="password" show-password
                      autocomplete="new-password" :placeholder="t('atleastncharacters', {n: passwordMinLength})"/>
          </el-form-item>
          <el-form-item :label="$t('confirmpassword')">
            <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password"/>
          </el-form-item>
          <el-alert v-if="error" class="setup-error" :closable="false" show-icon type="error" :title="error"/>
          <el-text class="setup-tips" size="small">
            {{ $t('passwordmustbeatleastncharactersandcontainatleasttwooftypes', {n: passwordMinLength}) }}
          </el-text>
          <el-button class="setup-submit" type="primary" :loading="loading" @click="submit">
            {{ $t('finishsetup') }}
          </el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {onMounted, reactive, ref} from "vue";
import {ElMessage} from "element-plus";
import * as http from "@/js/http.js";
import LocaleSwitcher from "@/view/custom/LocaleSwitcher.vue";
const {t} = useI18n()
const passwordMinLength = ref(8)
const loading = ref(false)
const error = ref('')

const form = reactive({
  username: '',
  password: '',
  confirm: ''
})

const checkPassword = value => {
  if (!value) return t('passwordisrequired')
  if (value.length < passwordMinLength.value) return t('passwordmustbeatleastncharacters', {n: passwordMinLength.value})
  let types = 0
  if (/[a-z]/.test(value)) types++
  if (/[A-Z]/.test(value)) types++
  if (/\d/.test(value)) types++
  if (/[^A-Za-z0-9]/.test(value)) types++
  if (types < 2) return t('passwordmustcontainatleasttwoofuppercaselett')
  return ''
}

const submit = () => {
  error.value = ''
  if (!/^[A-Za-z0-9_-]{3,32}$/.test(form.username)) {
    error.value = t('usernamemustbe332lettersdigitsunderscoresorh')
    return
  }
  const passwordError = checkPassword(form.password)
  if (passwordError) {
    error.value = passwordError
    return
  }
  if (form.password !== form.confirm) {
    error.value = t('thepasswordsdonotmatch')
    return
  }

  loading.value = true
  http.setup({username: form.username, password: form.password})
      .then(res => {
        ElMessage.success(res.message)
        setTimeout(() => location.reload(), 600)
      })
      .catch(e => {
        error.value = e.message || t('setupfailed')
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
.locale-corner {
  position: absolute;
  top: 16px;
  right: 16px;
}
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

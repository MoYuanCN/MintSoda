<template>
  <el-alert
      :closable="false"
      show-icon
      :title="$t('alllocalfeaturesareunlockednodonationorderis')"
      type="success"
  />
  <el-text class="donation-note" type="info">
    {{ $t('donationsdonotaffectlocalfeaturesthispagenol') }}
  </el-text>
</template>

<style scoped>
.donation-note { display: block; margin-top: 12px; }
</style>

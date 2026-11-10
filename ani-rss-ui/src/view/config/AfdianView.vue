<template>
  <el-alert
      :closable="false"
      show-icon
      title="ANI-RSS-Yuri 的本地功能已全部解锁，无需填写捐赠订单号。"
      type="success"
  />
  <el-text class="donation-note" type="info">
    捐赠不会改变本地功能状态。此页面不再连接原项目的捐赠验证服务。
  </el-text>
</template>

<style scoped>
.donation-note { display: block; margin-top: 12px; }
</style>

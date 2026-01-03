<template>
  <div class="not-found-container">
    <div class="not-found-content">
      <div class="not-found-icon">
        <el-icon :size="120"><DocumentDelete /></el-icon>
      </div>
      <h1 class="not-found-title">404</h1>
      <p class="not-found-message">抱歉，您访问的页面不存在</p>
      <div class="not-found-actions">
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { useRouter } from 'vue-router'
import { DocumentDelete } from '@element-plus/icons-vue'

export default {
  name: 'NotFound',
  components: {
    DocumentDelete
  },
  setup() {
    const router = useRouter()
    
    const goHome = () => {
      router.push('/')
    }
    
    const goBack = () => {
      if (window.history.length > 1) {
        router.go(-1)
      } else {
        router.push('/')
      }
    }
    
    return {
      goHome,
      goBack
    }
  }
}
</script>

<style scoped>
.not-found-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  /* background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); */
  padding: 20px;
}

.not-found-content {
  text-align: center;
  background: white;
  padding: 60px 40px;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  max-width: 500px;
  width: 100%;
}

.not-found-icon {
  color: #909399;
  margin-bottom: 20px;
}

.not-found-title {
  font-size: 72px;
  font-weight: bold;
  color: #303133;
  margin: 0 0 20px 0;
  line-height: 1;
}

.not-found-message {
  font-size: 18px;
  color: #606266;
  margin: 0 0 40px 0;
}

.not-found-actions {
  display: flex;
  gap: 15px;
  justify-content: center;
}

/* 暗色主题支持 */
.dark-theme .not-found-content {
  background: #1d1e1f;
}

.dark-theme .not-found-title {
  color: #e5eaf3;
}

.dark-theme .not-found-message {
  color: #a3a6ad;
}

.dark-theme .not-found-icon {
  color: #6c6e72;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .not-found-content {
    padding: 40px 20px;
  }
  
  .not-found-title {
    font-size: 48px;
  }
  
  .not-found-message {
    font-size: 16px;
  }
  
  .not-found-actions {
    flex-direction: column;
  }
  
  .not-found-actions .el-button {
    width: 100%;
  }
}
</style>



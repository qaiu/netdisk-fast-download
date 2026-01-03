<template>
  <div class="show-list-page">
    <div class="list-title-wrap">
      <h2 class="list-title">{{ url }} 目录</h2>
      <div class="list-subtitle">
        <a :href="url" target="_blank">原始分享链接</a>
      </div>
    </div>
    <div style="text-align:right;margin-bottom:12px;">
      <DarkMode @theme-change="toggleTheme" style="float: left;"/>
      <el-radio-group v-model="viewMode" size="small" style="margin-left:20px;">
        <el-radio-button label="pane">窗格</el-radio-button>
        <el-radio-button label="tree">目录树</el-radio-button>
      </el-radio-group>
    </div>
    <div v-if="loading" style="text-align:center;margin-top:40px;">加载中...</div>
    <div v-else-if="error" style="color:red;text-align:center;margin-top:40px;">{{ error }}</div>
    <div v-else>
      <DirectoryTree  
        :file-list="directoryData"
        :share-url="url"
        :password="''"
        :view-mode="viewMode"
      />
    </div>
  </div>
</template>

<script>
import axios from 'axios'
import DirectoryTree from '@/components/DirectoryTree'
import DarkMode from '@/components/DarkMode'

export default {
  name: 'ShowList',
  components: { DirectoryTree, DarkMode },
  data() {
    return {
      loading: true,
      error: '',
      directoryData: [],
      url: '',
      viewMode: 'pane'
    }
  },
  methods: {
    async fetchList() {
      this.url = this.$route.query.url
      if (!this.url) {
        this.error = '缺少 url 参数'
        this.loading = false
        return
      }
      try {
        const res = await axios.get('/v2/getFileList', { params: { url: this.url } })
        this.directoryData = res.data.data || []
      } catch (e) {
        this.error = '目录解析失败'
      } finally {
        this.loading = false
      }
    },
    toggleTheme(isDark) {
      const html = document.documentElement;
      const body = document.body;
      if (html && body && html.classList && body.classList) {
        if (isDark) {
          body.classList.add('dark-theme')
          html.classList.add('dark-theme')
        } else {
          body.classList.remove('dark-theme')
          html.classList.remove('dark-theme')
        }
      }
    }
  },
  mounted() {
    this.fetchList()
  }
}
</script>

<style scoped>
.show-list-page {
  max-width: 900px;
  margin: 40px auto;
}
.list-title-wrap {
  text-align: center;
  margin-bottom: 18px;
}
.list-title {
  font-size: 2rem;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 4px;
  word-break: break-all;
}
.list-subtitle {
  font-size: 1.05rem;
  color: #888;
  margin-bottom: 2px;
}
.list-subtitle a {
  color: #409eff;
  text-decoration: underline;
}
.list-subtitle a:hover {
  color: #1867c0;
}
</style> 
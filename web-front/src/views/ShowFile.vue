<template>
  <div class="show-file-page">
    <div v-if="loading" style="text-align:center;margin-top:40px;">加载中...</div>
    <div v-else-if="error" style="color:red;text-align:center;margin-top:40px;">{{ error }}</div>
    <div v-else>
      <div v-if="parseResult.code">
        <div class="file-meta-info-card">
          <div class="file-meta-row">
            <span class="file-meta-label">下载链接：</span>
            <a :href="downloadUrl" target="_blank" class="file-meta-link">点击下载</a>
          </div>
          <div class="file-meta-row">
            <span class="file-meta-label">文件名：</span>{{ fileTypeUtils.extractFileNameAndExt(downloadUrl).name }}
          </div>
          <div class="file-meta-row">
            <span class="file-meta-label">文件类型：</span>{{ fileTypeUtils.getFileTypeClass({ fileName: fileTypeUtils.extractFileNameAndExt(downloadUrl).name }) }}
          </div>
          <div class="file-meta-row" v-if="parseResult.data?.sizeStr">
            <span class="file-meta-label">文件大小：</span>{{ parseResult.data.sizeStr }}
          </div>
          <div class="file-meta-row">
            <span class="file-meta-label">在线预览：</span>
            <a :href="getPreviewLink()" target="_blank" class="preview-btn">点击在线预览</a>
          </div>
        </div>
      </div>
      <div v-else style="text-align:center;margin-top:40px;">未获取到有效解析结果</div>
    </div>
  </div>
</template>

<script>
import axios from 'axios'
import fileTypeUtils from '@/utils/fileTypeUtils'
import { previewBaseUrl } from '@/views/Home.vue'

export default {
  name: 'ShowFile',
  data() {
    return {
      loading: true,
      error: '',
      parseResult: {},
      downloadUrl: '',
      shareUrl: '', // 添加原始分享链接
      fileTypeUtils,
      previewBaseUrl
    }
  },
  methods: {
    // 生成预览链接（WPS 云文档特殊处理）
    getPreviewLink() {
      // 判断 shareKey 是否以 pwps: 开头（WPS 云文档）
      const shareKey = this.parseResult?.data?.shareKey
      if (shareKey && shareKey.startsWith('pwps:')) {
        // WPS 云文档直接使用原始分享链接
        return this.shareUrl
      }
      // 其他类型使用默认预览服务
      return this.previewBaseUrl + encodeURIComponent(this.downloadUrl)
    },
    
    async fetchFile() {
      const url = this.$route.query.url
      if (!url) {
        this.error = '缺少 url 参数'
        this.loading = false
        return
      }
      this.shareUrl = url // 保存原始分享链接
      try {
        const res = await axios.get('/json/parser', { params: { url } })
        this.parseResult = res.data
        this.downloadUrl = res.data.data?.directLink
      } catch (e) {
        this.error = '解析失败'
      } finally {
        this.loading = false
      }
    }
  },
  mounted() {
    this.fetchFile()
  }
}
</script>

<style scoped>
.show-file-page {
  max-width: 600px;
  margin: 40px auto;
}
.file-meta-info-card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  padding: 18px 24px 12px 24px;
  font-size: 1.02rem;
  color: #333;
}
.file-meta-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
  font-size: 1.01em;
}
.file-meta-label {
  min-width: 90px;
  color: #888;
  font-weight: 500;
  margin-right: 8px;
}
.file-meta-link {
  color: #409eff;
  word-break: break-all;
  text-decoration: underline;
}
.preview-btn {
  display: inline-block;
  padding: 4px 16px;
  background: #409eff;
  color: #fff;
  border-radius: 5px;
  text-decoration: none;
  font-weight: 500;
  margin-left: 8px;
  transition: background 0.2s;
}
.preview-btn:hover {
  background: #1867c0;
}
</style> 
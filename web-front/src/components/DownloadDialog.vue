<template>
  <el-dialog
    title="文件下载"
    v-model="dialogVisible"
    width="600px"
    :close-on-click-modal="false"
    @close="$emit('update:visible', false)"
  >
    <div v-if="info" class="download-info-content">
      <div class="download-file-header">
        <i class="fas fa-file" style="margin-right: 8px; color: #409eff;"></i>
        <strong>{{ info.fileName || '未命名文件' }}</strong>
      </div>
      <el-alert
        title="该文件需要特殊请求头才能下载，无法直接通过浏览器下载。请使用以下方式之一："
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px;"
      />
      <el-tabs v-model="activeTab">
        <el-tab-pane label="发送到下载器" name="downloader">
          <div class="downloader-section">
            <template v-if="isThunder">
              <p v-if="thunderNeedsCookie" style="color: #f56c6c; margin-bottom: 12px;">
                <i class="fas fa-exclamation-circle"></i>
                该文件需要 Cookie 认证，迅雷不支持自定义 Cookie，请切换到 Aria2/Motrix/Gopeed
              </p>
              <p v-else-if="thunderNeedsUa" style="color: #e6a23c; margin-bottom: 12px;">
                <i class="fas fa-exclamation-triangle"></i>
                该文件需要特殊 User-Agent 才能下载，迅雷客户端可能不支持自定义 UA，下载可能失败。建议切换到 Aria2/Motrix/Gopeed
              </p>
              <p v-else style="color: #909399; margin-bottom: 12px;">
                <i class="fas fa-bolt"></i>
                迅雷将通过浏览器唤起本地客户端下载
              </p>
            </template>
            <template v-else>
              <p v-if="!connected" style="color: #e6a23c; margin-bottom: 12px;">
                <i class="fas fa-exclamation-triangle"></i>
                未检测到下载器连接，请先在首页配置下载器（Aria2/Motrix/Gopeed/迅雷）
              </p>
              <p v-else style="color: #67c23a; margin-bottom: 12px;">
                <i class="fas fa-check-circle"></i>
                下载器已连接 ({{ downloaderVersion }})
              </p>
            </template>
            <el-button
              type="success"
              @click="sendToDownloader"
              :disabled="(isThunder && thunderNeedsCookie) || (!isThunder && !connected)"
              :loading="sending"
            >
              <i class="fas fa-paper-plane"></i> 发送到下载器
            </el-button>
            <el-button
              v-if="!isThunder"
              size="small"
              @click="doTestConnection"
              style="margin-left: 8px;"
            >
              测试连接
            </el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="Aria2 命令" name="aria2">
          <el-input
            type="textarea"
            :model-value="info.aria2Command"
            :rows="4"
            readonly
            resize="none"
            class="download-command-textarea"
          />
          <div class="download-actions">
            <el-button type="primary" size="small" @click="copyText(info.aria2Command)">
              <i class="fas fa-copy"></i> 复制 Aria2 命令
            </el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="Curl 命令" name="curl">
          <el-input
            type="textarea"
            :model-value="info.curlCommand"
            :rows="4"
            readonly
            resize="none"
            class="download-command-textarea"
          />
          <div class="download-actions">
            <el-button type="primary" size="small" @click="copyText(info.curlCommand)">
              <i class="fas fa-copy"></i> 复制 Curl 命令
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
      <div style="margin-top: 16px; text-align: right;">
        <el-button size="small" type="warning" @click="doDirectDownload">
          直接打开链接（可能失败）
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import { testConnection, addDownload, getConfig, hasCookieHeader, hasCustomUaHeader } from '@/utils/downloaderService'

export default {
  name: 'DownloadDialog',
  props: {
    /** v-model:visible 控制弹窗显示 */
    visible: {
      type: Boolean,
      default: false
    },
    /**
     * 下载信息对象
     * { downloadUrl, fileName, downloadHeaders, aria2Command, curlCommand, aria2JsonRpc, needDownloader }
     */
    downloadInfo: {
      type: Object,
      default: null
    }
  },
  emits: ['update:visible', 'close'],
  data() {
    return {
      activeTab: 'downloader',
      connected: false,
      downloaderVersion: '',
      sending: false
    }
  },
  computed: {
    dialogVisible: {
      get() { return this.visible },
      set(val) { this.$emit('update:visible', val) }
    },
    info() { return this.downloadInfo },
    isThunder() { return getConfig().downloaderType === 'thunder' },
    thunderNeedsCookie() { return this.isThunder && this.info && hasCookieHeader(this.info.downloadHeaders) },
    thunderNeedsUa() { return this.isThunder && this.info && hasCustomUaHeader(this.info.downloadHeaders) }
  },
  watch: {
    visible(val) {
      if (val) {
        this.activeTab = 'downloader'
        this.checkConnection()
      }
    }
  },
  methods: {
    /** 检测下载器连接状态 */
    async checkConnection() {
      const result = await testConnection()
      this.connected = result.connected
      this.downloaderVersion = result.version
    },

    /** 手动测试连接 */
    async doTestConnection() {
      const result = await testConnection()
      this.connected = result.connected
      this.downloaderVersion = result.version
      if (result.connected) {
        this.$message.success(`下载器连接正常 (${result.version})`)
      } else {
        this.$message.error('无法连接到下载器，请检查配置')
      }
    },

    /** 发送到 Aria2/Motrix/Gopeed */
    async sendToDownloader() {
      if (!this.info) return
      this.sending = true
      try {
        const gid = await addDownload(
          this.info.downloadUrl,
          this.info.downloadHeaders,
          this.info.fileName
        )
        this.$message.success('已发送到下载器，任务ID: ' + gid)
        this.dialogVisible = false
      } catch (error) {
        console.error('发送到下载器失败:', error)
        this.$message.error('发送到下载器失败: ' + (error.message || '未知错误'))
      } finally {
        this.sending = false
      }
    },

    /** 直接打开下载链接（可能因缺请求头而失败） */
    doDirectDownload() {
      if (this.info && this.info.downloadUrl) {
        const a = document.createElement('a')
        a.href = this.info.downloadUrl
        a.target = '_blank'
        a.rel = 'noopener noreferrer'
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        this.dialogVisible = false
      }
    },

    /** 复制文本到剪贴板 */
    async copyText(text) {
      if (!text) return
      try {
        await navigator.clipboard.writeText(text)
        this.$message.success('已复制到剪贴板')
      } catch {
        const textarea = document.createElement('textarea')
        textarea.value = text
        textarea.style.position = 'fixed'
        textarea.style.opacity = '0'
        document.body.appendChild(textarea)
        textarea.select()
        document.execCommand('copy')
        document.body.removeChild(textarea)
        this.$message.success('已复制到剪贴板')
      }
    }
  }
}
</script>

<style scoped>
.download-info-content {
  padding: 0 4px;
}
.download-file-header {
  font-size: 16px;
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #f0f7ff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  word-break: break-all;
}
:deep(.dark) .download-file-header,
.dark-theme .download-file-header {
  background: #1a3350;
}
.download-command-textarea :deep(.el-textarea__inner) {
  font-family: 'Courier New', Courier, monospace;
  font-size: 12px;
  background: #f5f5f5;
  color: #333;
}
:deep(.dark) .download-command-textarea :deep(.el-textarea__inner),
.dark-theme .download-command-textarea :deep(.el-textarea__inner) {
  background: #1e1e1e;
  color: #d4d4d4;
}
.download-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
.downloader-section {
  padding: 16px 0;
}
</style>

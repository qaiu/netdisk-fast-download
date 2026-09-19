<template>
  <div>
    <span class="tree-sidebar-count">已勾选 {{ selectedCount }} 个文件</span>
    <div class="tree-sidebar-actions">
      <el-button
        type="primary"
        size="small"
        :disabled="selectedCount === 0 || batchDownloading || treeExpanding || browserDisabled"
        :loading="batchDownloading"
        :title="browserDisabled ? '所选文件需使用下载器下载' : ''"
        @click="$emit('browser-download')"
      >
        浏览器下载
      </el-button>
      <el-button
        type="success"
        size="small"
        :disabled="selectedCount === 0 || batchDownloading || treeExpanding"
        :loading="batchDownloading"
        @click="$emit('send-downloader')"
      >
        发送到下载器
      </el-button>
      <el-button size="small" @click="$emit('cancel')">取消</el-button>
    </div>
    <div v-if="batchDownloading" class="batch-progress-info">
      <el-progress :percentage="progressPercent" :status="progressStatus" />
      <p>{{ progressCurrent }} / {{ progressTotal }}
        <span v-if="progressFailed > 0" style="color:#f56c6c;"> ({{ progressFailed }} 失败)</span>
      </p>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TreeBatchActionBar',
  props: {
    selectedCount: { type: Number, default: 0 },
    batchDownloading: { type: Boolean, default: false },
    treeExpanding: { type: Boolean, default: false },
    browserDisabled: { type: Boolean, default: false },
    progressPercent: { type: Number, default: 0 },
    progressStatus: { type: String, default: '' },
    progressCurrent: { type: Number, default: 0 },
    progressTotal: { type: Number, default: 0 },
    progressFailed: { type: Number, default: 0 }
  },
  emits: ['browser-download', 'send-downloader', 'cancel']
}
</script>

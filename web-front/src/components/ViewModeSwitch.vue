<template>
  <el-radio-group
    :model-value="modelValue"
    size="small"
    class="view-mode-switch"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-radio-button label="pane" title="窗格视图">
      <i class="fas fa-th" aria-hidden="true"></i>
      <span class="view-mode-text">{{ paneLabel }}</span>
    </el-radio-button>
    <el-radio-button label="tree" title="文件树视图">
      <i class="fas fa-sitemap" aria-hidden="true"></i>
      <span class="view-mode-text">{{ treeLabel }}</span>
    </el-radio-button>
  </el-radio-group>
</template>

<script>
export default {
  name: 'ViewModeSwitch',
  props: {
    modelValue: {
      type: String,
      default: 'pane'
    },
    paneLabel: {
      type: String,
      default: '窗格'
    },
    treeLabel: {
      type: String,
      default: '文件树'
    }
  },
  emits: ['update:modelValue']
}
</script>

<style scoped>
.view-mode-switch :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  min-width: 76px;
  padding: 8px 12px;
}
.view-mode-switch i {
  font-size: 14px;
  line-height: 1;
}
.view-mode-text {
  line-height: 1;
}

@media (max-width: 480px) {
  .view-mode-switch :deep(.el-radio-button__inner) {
    min-width: 68px;
    min-height: 40px;
    padding: 10px 12px;
  }
}
</style>

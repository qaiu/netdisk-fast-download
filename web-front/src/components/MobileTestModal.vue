<!-- 移动端测试弹框组件 - 非全屏，动态高度 -->
<template>
  <el-dialog
    v-model="visible"
    :fullscreen="false"
    :show-close="false"
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    class="mobile-test-modal"
    width="90%"
    top="auto"
    align-center
  >
    <div class="modal-content">
      <!-- 测试参数表单 -->
      <el-form :model="localParams" size="default" class="test-form" label-position="top">
        <el-form-item label="分享链接">
          <el-autocomplete
            v-model="localParams.shareUrl"
            :fetch-suggestions="queryUrlHistory"
            placeholder="https://example.com/s/abc"
            clearable
            style="width: 100%;"
            @select="handleUrlSelect"
          >
            <template #prefix>
              <el-icon><Link /></el-icon>
            </template>
          </el-autocomplete>
        </el-form-item>
        
        <el-form-item label="密码（可选）">
          <el-input
            v-model="localParams.pwd"
            placeholder="请输入密码"
            clearable
            size="default"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        
        <el-form-item label="方法">
          <el-radio-group v-model="localParams.method">
            <el-radio label="parse">parse</el-radio>
            <el-radio label="parseFileList">parseFileList</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      
      <!-- 执行按钮 -->
      <el-button
        type="primary"
        :loading="testing"
        @click="handleExecute"
        style="width: 100%;"
      >
        <el-icon v-if="!testing"><CaretRight /></el-icon>
        <span>{{ testing ? '执行中...' : '执行测试' }}</span>
      </el-button>
      
      <!-- 执行结果 - 直接显示 -->
      <transition name="slide-up">
        <div v-if="testResult" class="result-section">
          <el-divider />
          
          <el-alert
            :type="testResult.success ? 'success' : 'error'"
            :title="testResult.success ? '✓ 执行成功' : '✗ 执行失败'"
            :closable="false"
            style="margin-bottom: 12px;"
          />
          
          <!-- 成功结果 -->
          <div v-if="testResult.success && testResult.result" class="result-data">
            <div class="section-title">结果数据：</div>
            <el-input
              type="textarea"
              :model-value="testResult.result"
              readonly
              :autosize="{ minRows: 2, maxRows: 8 }"
              class="result-textarea"
            />
          </div>
          <div v-else-if="testResult.success && !testResult.result" class="result-data">
            <div class="empty-data">（无数据）</div>
          </div>

          <!-- 错误信息 -->
          <div v-if="testResult.error" class="result-error">
            <div class="section-title">错误信息：</div>
            <el-alert type="error" :title="testResult.error" :closable="false" />
            <div v-if="testResult.stackTrace" class="stack-trace">
              <el-collapse>
                <el-collapse-item title="查看堆栈信息" name="stack">
                  <pre>{{ testResult.stackTrace }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>

          <!-- 执行时间 -->
          <div v-if="testResult.executionTime" class="execution-time">
            ⏱ 执行时间：{{ testResult.executionTime }}ms
          </div>
        </div>
      </transition>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue';
import { Link, Lock, Close, CaretRight } from '@element-plus/icons-vue';
import JsonViewer from 'vue3-json-viewer';
import 'vue3-json-viewer/dist/index.css';

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  testParams: {
    type: Object,
    required: true
  },
  testResult: {
    type: Object,
    default: null
  },
  testing: {
    type: Boolean,
    default: false
  },
  urlHistory: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['update:modelValue', 'execute-test', 'update:testParams']);

const visible = ref(props.modelValue);
const localParams = ref({ ...props.testParams });

watch(() => props.modelValue, (val) => {
  visible.value = val;
  if (val) {
    localParams.value = { ...props.testParams };
  }
});

watch(visible, (val) => {
  emit('update:modelValue', val);
});

watch(() => props.testParams, (val) => {
  localParams.value = { ...val };
}, { deep: true });

// URL历史记录查询
const queryUrlHistory = (queryString, cb) => {
  const results = queryString 
    ? props.urlHistory
        .filter(url => url.toLowerCase().includes(queryString.toLowerCase()))
        .map(url => ({ value: url }))
    : props.urlHistory.map(url => ({ value: url }));
  cb(results);
};

// 选择历史URL
const handleUrlSelect = (item) => {
  localParams.value.shareUrl = item.value;
};

// 执行测试
const handleExecute = () => {
  emit('update:testParams', localParams.value);
  emit('execute-test');
};

// 关闭对话框
const handleClose = () => {
  visible.value = false;
};
</script>

<style scoped>
.mobile-test-modal :deep(.el-dialog) {
  margin: 0 !important;
  max-height: 75vh;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
}

.mobile-test-modal :deep(.el-dialog__header) {
  padding: 0;
  margin: 0;
  flex-shrink: 0;
}

.mobile-test-modal :deep(.el-dialog__body) {
  padding: 16px;
  overflow-y: auto;
  flex: 1;
  max-height: calc(75vh - 50px);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color);
  font-size: 16px;
  font-weight: 600;
  background: var(--el-bg-color);
  border-radius: 12px 12px 0 0;
}

.close-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--el-text-color-secondary);
  transition: color 0.2s;
}

.close-btn:hover {
  color: var(--el-text-color-primary);
}

.modal-content {
  padding-top: 5px;
}

.test-form {
  margin-bottom: 10px;
}

.test-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.test-form :deep(.el-form-item__label) {
  font-size: 13px;
  padding-bottom: 4px;
}

.result-section {
  margin-top: 10px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.result-data {
  margin-bottom: 12px;
}

.json-viewer-wrapper {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px;
  background: var(--el-fill-color-blank);
}

.json-viewer-wrapper :deep(.jv-container) {
  font-size: 12px;
}

.result-error {
  margin-bottom: 12px;
}

.stack-trace {
  margin-top: 10px;
}

.stack-trace pre {
  background: var(--el-fill-color-light);
  padding: 10px;
  border-radius: 4px;
  font-size: 11px;
  overflow-x: auto;
  max-height: 150px;
  line-height: 1.4;
}

.execution-time {
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.empty-data {
  color: var(--el-text-color-secondary);
  font-style: italic;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  text-align: center;
  font-size: 13px;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>

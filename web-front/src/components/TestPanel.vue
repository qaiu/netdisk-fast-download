<!-- 测试参数和结果 Tab 面板组件 -->
<template>
  <div class="test-panel">
    <el-tabs v-model="activeTab" class="test-panel-tabs" type="border-card">
      <!-- 测试Tab -->
      <el-tab-pane label="测试" name="test">
        <template #label>
          <span class="tab-label">
            <el-icon><Stopwatch /></el-icon>
            <span style="margin-left: 4px;">测试</span>
          </span>
        </template>
        
        <!-- 测试参数 -->
        <div class="test-params-section">
          <el-form :model="testParams" label-width="0px" size="small">
            <el-form-item label="">
              <el-autocomplete
                v-model="testParams.shareUrl"
                :fetch-suggestions="queryUrlHistory"
                placeholder="请输入分享链接"
                clearable
                style="width: 100%;"
                @select="handleUrlSelect"
              >
                <template #suffix>
                  <el-icon><Link /></el-icon>
                </template>
              </el-autocomplete>
            </el-form-item>
            <el-form-item label="">
              <el-input
                v-model="testParams.pwd"
                placeholder="密码（可选）"
                clearable
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="">
              <el-radio-group v-model="testParams.method" size="small">
                <el-radio label="parse">parse</el-radio>
                <el-radio label="parseFileList">parseFileList</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="testing"
                @click="$emit('execute-test')"
                style="width: 100%"
              >
                执行测试
              </el-button>
            </el-form-item>
          </el-form>
        </div>
        
        <!-- 执行结果 -->
        <div class="test-result-section">
          <div class="section-header">
            <span>执行结果</span>
            <el-button 
              v-if="testResult" 
              text 
              size="small" 
              icon="Delete" 
              @click="$emit('clear-result')"
            >
              清空
            </el-button>
          </div>
          
          <div v-if="testResult" class="result-content">
            <el-alert
              :type="testResult.success ? 'success' : 'error'"
              :title="testResult.success ? '执行成功' : '执行失败'"
              :closable="false"
              style="margin-bottom: 10px"
            />
            
            <div v-if="testResult.success" class="result-section">
              <div class="section-title">结果数据：</div>
              <el-input
                v-if="testResult.result"
                type="textarea"
                :model-value="testResult.result"
                readonly
                :autosize="{ minRows: 2, maxRows: 8 }"
                class="result-textarea"
              />
              <div v-else class="empty-data">（无数据）</div>
            </div>

            <div v-if="testResult.error" class="result-section">
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

            <div v-if="testResult.executionTime" class="result-section">
              <div class="section-title">执行时间：</div>
              <div>{{ testResult.executionTime }}ms</div>
            </div>
          </div>
          <div v-else class="empty-result">
            <el-empty description="暂无执行结果" :image-size="60" />
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 问题Tab -->
      <el-tab-pane name="problems">
        <template #label>
          <span class="tab-label">
            <el-icon><WarningFilled /></el-icon>
            问题
            <el-badge v-if="codeProblems.length > 0" :value="codeProblems.length" style="margin-left: 5px;" />
          </span>
        </template>
        
        <div v-if="codeProblems.length > 0" class="problems-list">
          <div
            v-for="(problem, index) in codeProblems"
            :key="index"
            :class="[
              'problem-item',
              problem.severity === 8 ? 'problem-error' : problem.severity === 4 ? 'problem-warning' : 'problem-info'
            ]"
            @click="$emit('goto-problem', problem)"
          >
            <div class="problem-header">
              <el-icon :size="16">
                <WarningFilled v-if="problem.severity === 8" />
                <Warning v-else-if="problem.severity === 4" />
                <InfoFilled v-else />
              </el-icon>
              <span class="problem-line">行 {{problem.startLineNumber}}</span>
            </div>
            <div class="problem-message">{{ problem.message }}</div>
          </div>
        </div>
        <div v-else class="empty-problems">
          <el-empty description="暂无代码问题" :image-size="60" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { Stopwatch, WarningFilled, Warning, InfoFilled, Link, Lock } from '@element-plus/icons-vue';
import JsonViewer from 'vue3-json-viewer';
import 'vue3-json-viewer/dist/index.css';

const props = defineProps({
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
  codeProblems: {
    type: Array,
    default: () => []
  },
  urlHistory: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['execute-test', 'clear-result', 'goto-problem', 'update:testParams']);

const activeTab = ref('test');

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
  emit('update:testParams', { ...props.testParams, shareUrl: item.value });
};
</script>

<style scoped>
.test-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.test-panel-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.test-panel-tabs :deep(.el-tabs__header) {
  flex-shrink: 0;
  margin-bottom: 0;
}

.test-panel-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.test-panel-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 5px;
}

.test-params-section {
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  margin-bottom: 12px;
}

.test-result-section {
  padding: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.result-content {
  background: var(--el-bg-color);
  padding: 12px;
  border-radius: 4px;
  border: 1px solid var(--el-border-color);
}

.result-section {
  margin-bottom: 15px;
}

.result-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.stack-trace {
  margin-top: 10px;
}

.stack-trace pre {
  background: var(--el-fill-color-light);
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  max-height: 300px;
}

.empty-result,
.empty-problems {
  padding: 40px 20px;
  text-align: center;
}

.problems-list {
  padding: 8px;
}

.problem-item {
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid;
}

.problem-item:hover {
  background: var(--el-fill-color-light);
  transform: translateX(2px);
}

.problem-error {
  border-left-color: var(--el-color-error);
  background: var(--el-color-error-light-9);
}

.problem-warning {
  border-left-color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.problem-info {
  border-left-color: var(--el-color-info);
  background: var(--el-color-info-light-9);
}

.problem-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.problem-line {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.problem-message {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
  word-break: break-word;
}

.empty-data {
  color: var(--el-text-color-secondary);
  font-style: italic;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  text-align: center;
}
</style>

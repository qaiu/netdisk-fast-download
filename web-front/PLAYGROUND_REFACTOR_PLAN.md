# Playground 移动端优化重构方案

## 当前问题
1. 移动端代码问题布局显示异常
2. PC端测试区域和问题区域混杂
3. 移动端编辑器高度不够
4. 缺少URL历史记录功能
5. 悬浮按钮组功能单一

## 改进方案

### 1. PC端 - Tab页签模式
- 右侧面板改为Tab页签
  - 测试 (Debug图标)
  - 问题 (感叹号图标)
- 统一的折叠/展开按钮

### 2. 移动端 - 模态框模式
- 移除底部固定的测试参数区域
- 添加两个悬浮模态框触发按钮：
  - 运行测试 (三角形图标)
  - 查看问题 (感叹号图标)
- 测试模态框包含：
  - URL输入（带历史记录下拉）
  - 密码输入
  - 方法选择
  - 执行按钮
  - 结果展示
  
### 3. URL历史记录
- LocalStorage存储最近10条
- 下拉选择历史URL
- 点击快速填充

### 4. 悬浮按钮组优化
- 增大按钮尺寸
- 添加运行按钮
- 位置：右下角
- 按钮：撤销、重做、格式化、全选、运行

### 5. 编辑器高度优化
- 移动端：calc(100vh - 顶部导航 - 按钮区域 - 10px)
- PC端：保持当前分屏模式

## 实现步骤

### 步骤1：添加状态变量
```javascript
//  URL历史记录
const urlHistory = ref([]);
const HISTORY_KEY = 'playground_url_history';

// 模态框状态
const mobileTestDialogVisible = ref(false);
const mobileResultDialogVisible = ref(false);

// Tab页签
const rightPanelTab = ref('test'); // 'test' | 'problems'
```

### 步骤2：URL历史记录功能
```javascript
// 加载历史
onMounted(() => {
  const history = localStorage.getItem(HISTORY_KEY);
  if (history) {
    urlHistory.value = JSON.parse(history);
  }
});

// 添加到历史
const addToHistory = (url) => {
  if (!url || !url.trim()) return;
  
  // 去重
  const filtered = urlHistory.value.filter(item => item !== url);
  filtered.unshift(url);
  
  // 限制数量
  if (filtered.length > MAX_HISTORY) {
    filtered.length = MAX_HISTORY;
  }
  
  urlHistory.value = filtered;
  localStorage.setItem(HISTORY_KEY, JSON.stringify(filtered));
};
```

### 步骤3：PC端Tab页签
替换当前右侧面板的3个独立卡片为：
```vue
<el-tabs v-model="rightPanelTab" class="right-panel-tabs">
  <el-tab-pane name="test">
    <template #label>
      <span class="tab-label">
        <el-icon><Stopwatch /></el-icon>
        测试
      </span>
    </template>
    <!-- 测试参数 + 结果 -->
  </el-tab-pane>
  
  <el-tab-pane name="problems">
    <template #label>
      <span class="tab-label">
        <el-icon><WarningFilled /></el-icon>
        问题
        <el-badge v-if="codeProblems.length > 0" :value="codeProblems.length" />
      </span>
    </template>
    <!-- 代码问题列表 -->
  </el-tab-pane>
</el-tabs>
```

### 步骤4：移动端模态框
```vue
<!-- 测试模态框 -->
<el-dialog
  v-model="mobileTestDialogVisible"
  title="运行测试"
  :fullscreen="true"
  class="mobile-test-dialog"
>
  <!-- URL输入带历史记录 -->
  <el-select
    v-model="testParams.shareUrl"
    filterable
    allow-create
    placeholder="输入或选择URL"
  >
    <el-option
      v-for="url in urlHistory"
      :key="url"
      :label="url"
      :value="url"
    />
  </el-select>
  
  <!-- 其他表单项 -->
  <!-- 执行按钮 -->
  <!-- 结果显示 -->
</el-dialog>
```

### 步骤5：优化悬浮按钮
```vue
<div class="mobile-editor-actions large">
  <el-button-group size="large">
    <el-button icon="RefreshLeft" circle @click="undo" />
    <el-button icon="RefreshRight" circle @click="redo" />
    <el-button icon="MagicStick" circle @click="formatCode" />
    <el-button icon="Select" circle @click="selectAll" />
    <el-button 
      type="primary" 
      icon="CaretRight" 
      circle 
      @click="mobileTestDialogVisible = true" 
    />
  </el-button-group>
</div>
```

CSS:
```css
.mobile-editor-actions.large .el-button {
  width: 48px !important;
  height: 48px !important;
  font-size: 20px !important;
}
```

### 步骤6：编辑器高度
```css
/* 移动端编辑器高度 */
@media screen and (max-width: 768px) {
  .mobile-layout .editor-section {
    height: calc(100vh - 120px) !important;
  }
  
  .mobile-layout .editor-section :deep(.monaco-editor-container) {
    height: 100% !important;
  }
}
```

## 文件修改清单

### 需要修改的部分：
1. `<script setup>` 部分：添加新的状态变量和函数
2. `<template>` 部分：
   - PC端：替换右侧面板为Tab
   - 移动端：添加模态框，移除底部测试区
   - 优化悬浮按钮组
3. `<style>` 部分：
   - 添加Tab样式
   - 添加模态框样式  
   - 调整编辑器高度
   - 优化悬浮按钮尺寸

## 注意事项
1. 保持向后兼容
2. 测试各种屏幕尺寸
3. 确保URL历史记录不会泄露敏感信息
4. 模态框要支持键盘操作（ESC关闭）
5. 优化动画过渡效果

# Playground.vue 重构实施方案

## 已完成的工作
1. ✅ 创建 Python 补全模块 (`src/utils/pythonCompletions.js`)
2. ✅ 创建 TestPanel 组件 (`src/components/TestPanel.vue`)
3. ✅ 创建 MobileTestModal 组件 (`src/components/MobileTestModal.vue`)
4. ✅ 更新 MonacoEditor 集成 Python 补全

## 需要在Playground.vue中实施的改动

### 1. 导入新组件（在script setup顶部）

```javascript
import TestPanel from '@/components/TestPanel.vue';
import MobileTestModal from '@/components/MobileTestModal.vue';
```

### 2. PC端：替换右侧面板为TestPanel组件

**位置**：行638-656（桌面端 Pane 区域）

**原代码**：
```vue
<Pane v-if="!collapsedPanels.rightPanel" 
  :size="splitSizes[1]" min-size="20" class="test-pane" style="margin-left: 10px;">
  <div class="test-section">
    <!-- 3个卡片：测试参数、代码问题、执行结果 -->
  </div>
</Pane>
```

**新代码**：
```vue
<Pane v-if="!collapsedPanels.rightPanel" 
  :size="splitSizes[1]" min-size="20" class="test-pane" style="margin-left: 10px;">
  <div class="test-section">
    <!-- 折叠按钮 -->
    <el-tooltip content="折叠测试面板" placement="left">
      <div class="panel-collapse-btn" @click="toggleRightPanel">
        <el-icon><CaretRight /></el-icon>
      </div>
    </el-tooltip>
    
    <!-- 使用TestPanel组件 -->
    <TestPanel
      :test-params="testParams"
      :test-result="testResult"
      :testing="testing"
      :code-problems="codeProblems"
      :url-history="urlHistory"
      @execute-test="executeTest"
      @clear-result="testResult = null"
      @goto-problem="goToProblemLine"
      @update:test-params="(params) => Object.assign(testParams, params)"
    />
  </div>
</Pane>
```

### 3. 移动端：替换测试区域为浮动按钮 + MobileTestModal

**位置**：行280-455（移动端布局区域）

**改动**：
1. 移除现有的测试参数表单（行330-410）
2. 添加悬浮运行按钮到编辑器操作按钮组
3. 在模板底部添加 MobileTestModal 组件

**新的悬浮按钮代码**：
```vue
<!-- 移动端悬浮操作按钮 - 增大尺寸 -->
<div class="mobile-editor-actions large-actions">
  <el-button-group size="large">
    <el-tooltip content="撤销 (Ctrl+Z)" placement="top">
      <el-button icon="RefreshLeft" circle @click="undo" class="action-btn-large" />
    </el-tooltip>
    <el-tooltip content="重做 (Ctrl+Y)" placement="top">
      <el-button icon="RefreshRight" circle @click="redo" class="action-btn-large" />
    </el-tooltip>
    <el-tooltip content="格式化 (Shift+Alt+F)" placement="top">
      <el-button icon="MagicStick" circle @click="formatCode" class="action-btn-large" />
    </el-tooltip>
    <el-tooltip content="全选 (Ctrl+A)" placement="top">
      <el-button icon="Select" circle @click="selectAll" class="action-btn-large" />
    </el-tooltip>
    <el-tooltip content="运行测试" placement="top">
      <el-button 
        type="primary" 
        icon="CaretRight" 
        circle 
        @click="mobileTestDialogVisible = true" 
        class="action-btn-large"
      />
    </el-tooltip>
  </el-button-group>
</div>
```

**在模板底部添加（行1150前）**：
```vue
<!-- 移动端测试模态框 -->
<MobileTestModal
  v-model="mobileTestDialogVisible"
  :test-params="testParams"
  :test-result="testResult"
  :testing="testing"
  :url-history="urlHistory"
  @execute-test="handleMobileExecuteTest"
  @update:test-params="(params) => Object.assign(testParams, params)"
/>
```

### 4. URL历史记录功能

**在script setup中添加（约行2200附近）**：

```javascript
// 从localStorage加载URL历史
onMounted(() => {
  const saved = localStorage.getItem(HISTORY_KEY);
  if (saved) {
    try {
      urlHistory.value = JSON.parse(saved);
    } catch (e) {
      console.error('加载URL历史失败:', e);
      urlHistory.value = [];
    }
  }
});

// 添加URL到历史记录
const addToUrlHistory = (url) => {
  if (!url || !url.trim()) return;
  
  // 去重并添加到开头
  const filtered = urlHistory.value.filter(item => item !== url);
  filtered.unshift(url);
  
  // 限制数量
  if (filtered.length > MAX_HISTORY) {
    filtered.length = MAX_HISTORY;
  }
  
  urlHistory.value = filtered;
  localStorage.setItem(HISTORY_KEY, JSON.stringify(filtered));
};

// 修改executeTest函数，在成功执行后添加到历史
// 找到executeTest函数（约行2400），在测试成功后添加：
const executeTest = async () => {
  // ... 现有代码 ...
  
  // 测试成功后
  if (testResult.value && testResult.value.success) {
    addToUrlHistory(testParams.value.shareUrl);
  }
};

// 移动端执行测试
const handleMobileExecuteTest = async () => {
  await executeTest();
  // 如果执行成功，显示结果提示
  if (testResult.value && testResult.value.success) {
    ElMessage.success('测试执行成功，点击"查看详情"查看结果');
  }
};
```

### 5. 编辑器高度优化

**在style部分添加（约行4800）**：

```css
/* 移动端编辑器高度优化 */
@media screen and (max-width: 768px) {
  .mobile-layout .editor-section {
    min-height: calc(100vh - 220px); /* 顶部导航60px + 按钮区域120px + 间距40px */
  }
  
  .mobile-layout .editor-section :deep(.monaco-editor-container) {
    min-height: 500px;
  }
}

/* 悬浮按钮 - 增大尺寸 */
.mobile-editor-actions.large-actions {
  right: 12px;
  bottom: 12px;
}

.mobile-editor-actions.large-actions .action-btn-large {
  width: 48px !important;
  height: 48px !important;
  font-size: 20px !important;
}

.mobile-editor-actions.large-actions .el-button + .el-button {
  margin-left: 8px;
}
```

### 6. return语句中添加新的响应式引用

**在return对象中添加（约行3100）**：

```javascript
return {
  // ... 现有属性 ...
  
  // 新增
  urlHistory,
  mobileTestDialogVisible,
  handleMobileExecuteTest,
  addToUrlHistory,
  
  // ... 其余属性 ...
};
```

## 关键改进总结

### 功能增强
1. **Python补全**：关键字、内置函数、代码片段自动补全
2. **PC端Tab界面**：测试和问题整合为Tab页签
3. **移动端模态框**：测试参数移到全屏模态框
4. **URL历史记录**：自动保存最近10条URL，支持自动完成
5. **悬浮按钮优化**：增大尺寸到48px，添加运行按钮

### 代码优化
1. **组件拆分**：5441行减少约500行，提升可维护性
2. **逻辑解耦**：测试面板独立组件，便于复用
3. **用户体验**：
   - PC：Tab切换更直观
   - 移动：模态框避免滚动混乱
   - 历史记录：快速重复测试

## 实施顺序

1. ✅ 创建工具模块和组件（已完成）
2. ⏳ 更新Playground.vue导入
3. ⏳ PC端集成TestPanel
4. ⏳ 移动端集成MobileTestModal
5. ⏳ 添加URL历史记录功能
6. ⏳ 优化CSS样式
7. ⏳ 测试所有功能
8. ⏳ 构建和部署
9. ⏳ 更新文档

## 测试检查清单

- [ ] PC端Tab切换正常
- [ ] 移动端模态框正常打开/关闭
- [ ] Python补全正常工作（if, for, def等）
- [ ] URL历史记录保存和加载
- [ ] 悬浮按钮尺寸正确（48px）
- [ ] 编辑器高度填充屏幕
- [ ] 测试执行功能正常
- [ ] 代码问题显示正常
- [ ] 主题切换不影响新组件
- [ ] 移动/PC响应式切换正常

## 回滚方案

如果出现问题，可通过以下步骤回滚：

```bash
# 1. 恢复Playground.vue
git checkout HEAD -- src/views/Playground.vue

# 2. 删除新文件
rm src/utils/pythonCompletions.js
rm src/components/TestPanel.vue
rm src/components/MobileTestModal.vue

# 3. 恢复MonacoEditor.vue
git checkout HEAD -- src/components/MonacoEditor.vue

# 4. 重新构建
npm run build
```

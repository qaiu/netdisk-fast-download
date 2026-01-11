<template>
  <div ref="editorContainer" class="monaco-editor-container"></div>
</template>

<script>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';

export default {
  name: 'MonacoEditor',
  props: {
    modelValue: {
      type: String,
      default: ''
    },
    language: {
      type: String,
      default: 'javascript'
    },
    theme: {
      type: String,
      default: 'vs'
    },
    options: {
      type: Object,
      default: () => ({})
    },
    height: {
      type: String,
      default: '500px'
    }
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const editorContainer = ref(null);
    let editor = null;
    let monaco = null;
    let touchHandlers = { start: null, move: null };

    const defaultOptions = {
      value: props.modelValue,
      language: props.language,
      theme: props.theme,
      automaticLayout: true,
      fontSize: 14,
      minimap: {
        enabled: true
      },
      scrollBeyondLastLine: false,
      wordWrap: 'on',
      lineNumbers: 'on',
      roundedSelection: false,
      readOnly: false,
      cursorStyle: 'line',
      formatOnPaste: true,
      formatOnType: true,
      tabSize: 2,
      insertSpaces: true,
      ...props.options
    };

    const initEditor = async () => {
      try {
        if (!editorContainer.value) {
          console.error('编辑器容器未找到');
          return;
        }

        // 动态导入monaco-editor loader
        let loaderModule;
        try {
          loaderModule = await import('@monaco-editor/loader');
        } catch (importError) {
          console.error('导入@monaco-editor/loader失败:', importError);
          return;
        }
        
        // 获取loader对象
        // @monaco-editor/loader可能使用default导出或named导出
        let loader;
        if (loaderModule.default) {
          loader = loaderModule.default;
        } else if (loaderModule.loader) {
          loader = loaderModule.loader;
        } else {
          loader = loaderModule;
        }
        
        if (!loader) {
          console.error('Monaco Editor loader未找到，loaderModule:', loaderModule);
          return;
        }
        
        if (typeof loader.init !== 'function') {
          console.error('loader.init不是函数，loader对象:', loader);
          return;
        }

        // 配置Monaco Editor使用本地打包的文件，而不是CDN
        if (loader.config) {
          const vsPath = process.env.NODE_ENV === 'production' 
            ? './js/vs'  // 生产环境使用相对路径
            : '/js/vs';  // 开发环境使用绝对路径
          
          loader.config({ 
            paths: { 
              vs: vsPath
            }
          });
        }
        
        // 初始化Monaco Editor
        monaco = await loader.init();
        
        if (!monaco) {
          console.error('loader.init返回null或undefined');
          return;
        }
        
        if (!monaco.editor) {
          console.error('monaco.editor不存在，monaco对象:', monaco);
          return;
        }
        
        editor = monaco.editor.create(editorContainer.value, {
          ...defaultOptions,
          value: props.modelValue
        });

        // 监听内容变化
        editor.onDidChangeModelContent(() => {
          const value = editor.getValue();
          emit('update:modelValue', value);
          emit('change', value);
        });

        // 设置容器高度
        if (editorContainer.value) {
          editorContainer.value.style.height = props.height;
        }
        
        // 移动端：添加触摸缩放来调整字体大小
        if (window.innerWidth <= 768 && editorContainer.value) {
          let initialDistance = 0;
          let initialFontSize = defaultOptions.fontSize || 14;
          const minFontSize = 8;
          const maxFontSize = 24;
          
          const getTouchDistance = (touch1, touch2) => {
            const dx = touch1.clientX - touch2.clientX;
            const dy = touch1.clientY - touch2.clientY;
            return Math.sqrt(dx * dx + dy * dy);
          };
          
          touchHandlers.start = (e) => {
            if (e.touches.length === 2 && editor) {
              initialDistance = getTouchDistance(e.touches[0], e.touches[1]);
              initialFontSize = editor.getOption(monaco.editor.EditorOption.fontSize);
            }
          };
          
          touchHandlers.move = (e) => {
            if (e.touches.length === 2 && editor) {
              e.preventDefault(); // 防止页面缩放
              const currentDistance = getTouchDistance(e.touches[0], e.touches[1]);
              const scale = currentDistance / initialDistance;
              const newFontSize = Math.round(initialFontSize * scale);
              
              // 限制字体大小范围
              const clampedFontSize = Math.max(minFontSize, Math.min(maxFontSize, newFontSize));
              
              if (clampedFontSize !== editor.getOption(monaco.editor.EditorOption.fontSize)) {
                editor.updateOptions({ fontSize: clampedFontSize });
              }
            }
          };
          
          editorContainer.value.addEventListener('touchstart', touchHandlers.start, { passive: false });
          editorContainer.value.addEventListener('touchmove', touchHandlers.move, { passive: false });
        }
      } catch (error) {
        console.error('Monaco Editor初始化失败:', error);
        console.error('错误详情:', error.stack);
        console.error('错误对象:', error);
      }
    };

    const updateTheme = (newTheme) => {
      if (editor) {
        monaco.editor.setTheme(newTheme);
      }
    };

    const formatDocument = () => {
      if (editor) {
        editor.getAction('editor.action.formatDocument').run();
      }
    };

    watch(() => props.modelValue, (newValue) => {
      if (editor && editor.getValue() !== newValue) {
        editor.setValue(newValue);
      }
    });

    watch(() => props.theme, (newTheme) => {
      updateTheme(newTheme);
    });

    // 监听语言变化
    watch(() => props.language, (newLanguage) => {
      if (editor && monaco) {
        const model = editor.getModel();
        if (model) {
          monaco.editor.setModelLanguage(model, newLanguage);
          console.log('[MonacoEditor] 语言已切换为:', newLanguage);
        }
      }
    });

    watch(() => props.height, (newHeight) => {
      if (editorContainer.value) {
        editorContainer.value.style.height = newHeight;
        if (editor) {
          editor.layout();
        }
      }
    });

    onMounted(() => {
      initEditor();
    });

    onBeforeUnmount(() => {
      // 清理触摸事件监听器
      if (editorContainer.value && touchHandlers.start && touchHandlers.move) {
        editorContainer.value.removeEventListener('touchstart', touchHandlers.start);
        editorContainer.value.removeEventListener('touchmove', touchHandlers.move);
      }
      if (editor) {
        editor.dispose();
      }
    });

    return {
      editorContainer,
      formatDocument,
      getEditor: () => editor,
      getMonaco: () => monaco
    };
  }
};
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  /* 允许用户选择文本 */
  -webkit-user-select: text;
  user-select: text;
}

.monaco-editor-container :deep(.monaco-editor) {
  border-radius: 4px;
}

/* 移动端：禁用页面缩放，只允许编辑器字体缩放 */
@media (max-width: 768px) {
  .monaco-editor-container {
    /* 禁用页面级别的缩放，只允许编辑器内部字体缩放 */
    touch-action: pan-x pan-y;
  }
  
  .monaco-editor-container :deep(.monaco-editor) {
    /* 禁用页面级别的缩放 */
    touch-action: pan-x pan-y;
  }
}
</style>


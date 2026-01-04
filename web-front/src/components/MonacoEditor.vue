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
}

.monaco-editor-container :deep(.monaco-editor) {
  border-radius: 4px;
}
</style>


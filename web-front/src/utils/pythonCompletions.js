/**
 * Python 代码补全提供器
 * 提供关键字补全、语法模板、常用代码片段
 */

// Python 关键字列表
const PYTHON_KEYWORDS = [
  'False', 'None', 'True', 'and', 'as', 'assert', 'async', 'await',
  'break', 'class', 'continue', 'def', 'del', 'elif', 'else', 'except',
  'finally', 'for', 'from', 'global', 'if', 'import', 'in', 'is',
  'lambda', 'nonlocal', 'not', 'or', 'pass', 'raise', 'return',
  'try', 'while', 'with', 'yield'
];

// Python 内置函数
const PYTHON_BUILTINS = [
  'abs', 'all', 'any', 'ascii', 'bin', 'bool', 'bytearray', 'bytes',
  'callable', 'chr', 'classmethod', 'compile', 'complex', 'delattr',
  'dict', 'dir', 'divmod', 'enumerate', 'eval', 'exec', 'filter',
  'float', 'format', 'frozenset', 'getattr', 'globals', 'hasattr',
  'hash', 'help', 'hex', 'id', 'input', 'int', 'isinstance',
  'issubclass', 'iter', 'len', 'list', 'locals', 'map', 'max',
  'memoryview', 'min', 'next', 'object', 'oct', 'open', 'ord',
  'pow', 'print', 'property', 'range', 'repr', 'reversed', 'round',
  'set', 'setattr', 'slice', 'sorted', 'staticmethod', 'str', 'sum',
  'super', 'tuple', 'type', 'vars', 'zip'
];

// 代码片段模板
const PYTHON_SNIPPETS = [
  {
    label: 'if',
    kind: 'Snippet',
    insertText: 'if ${1:condition}:\n    ${2:pass}',
    detail: 'if语句',
    documentation: 'if条件语句'
  },
  {
    label: 'ifelse',
    kind: 'Snippet',
    insertText: 'if ${1:condition}:\n    ${2:pass}\nelse:\n    ${3:pass}',
    detail: 'if-else语句',
    documentation: 'if-else条件语句'
  },
  {
    label: 'ifelif',
    kind: 'Snippet',
    insertText: 'if ${1:condition}:\n    ${2:pass}\nelif ${3:condition}:\n    ${4:pass}\nelse:\n    ${5:pass}',
    detail: 'if-elif-else语句',
    documentation: 'if-elif-else条件语句'
  },
  {
    label: 'for',
    kind: 'Snippet',
    insertText: 'for ${1:item} in ${2:iterable}:\n    ${3:pass}',
    detail: 'for循环',
    documentation: 'for循环语句'
  },
  {
    label: 'forrange',
    kind: 'Snippet',
    insertText: 'for ${1:i} in range(${2:10}):\n    ${3:pass}',
    detail: 'for range循环',
    documentation: 'for range循环'
  },
  {
    label: 'forenumerate',
    kind: 'Snippet',
    insertText: 'for ${1:index}, ${2:item} in enumerate(${3:iterable}):\n    ${4:pass}',
    detail: 'for enumerate循环',
    documentation: 'for enumerate循环，同时获取索引和值'
  },
  {
    label: 'while',
    kind: 'Snippet',
    insertText: 'while ${1:condition}:\n    ${2:pass}',
    detail: 'while循环',
    documentation: 'while循环语句'
  },
  {
    label: 'def',
    kind: 'Snippet',
    insertText: 'def ${1:function_name}(${2:args}):\n    """${3:docstring}"""\n    ${4:pass}',
    detail: '函数定义',
    documentation: '定义一个函数'
  },
  {
    label: 'defret',
    kind: 'Snippet',
    insertText: 'def ${1:function_name}(${2:args}):\n    """${3:docstring}"""\n    ${4:pass}\n    return ${5:result}',
    detail: '带返回值的函数',
    documentation: '定义一个带返回值的函数'
  },
  {
    label: 'class',
    kind: 'Snippet',
    insertText: 'class ${1:ClassName}:\n    """${2:docstring}"""\n    \n    def __init__(self${3:, args}):\n        ${4:pass}',
    detail: '类定义',
    documentation: '定义一个类'
  },
  {
    label: 'classinit',
    kind: 'Snippet',
    insertText: 'def __init__(self${1:, args}):\n    ${2:pass}',
    detail: '__init__方法',
    documentation: '类的初始化方法'
  },
  {
    label: 'try',
    kind: 'Snippet',
    insertText: 'try:\n    ${1:pass}\nexcept ${2:Exception} as ${3:e}:\n    ${4:pass}',
    detail: 'try-except',
    documentation: 'try-except异常处理'
  },
  {
    label: 'tryfinally',
    kind: 'Snippet',
    insertText: 'try:\n    ${1:pass}\nexcept ${2:Exception} as ${3:e}:\n    ${4:pass}\nfinally:\n    ${5:pass}',
    detail: 'try-except-finally',
    documentation: 'try-except-finally完整异常处理'
  },
  {
    label: 'with',
    kind: 'Snippet',
    insertText: 'with ${1:expression} as ${2:variable}:\n    ${3:pass}',
    detail: 'with语句',
    documentation: 'with上下文管理器'
  },
  {
    label: 'withopen',
    kind: 'Snippet',
    insertText: 'with open(${1:\'filename\'}, ${2:\'r\'}) as ${3:f}:\n    ${4:content = f.read()}',
    detail: 'with open文件操作',
    documentation: '使用with打开文件'
  },
  {
    label: 'lambda',
    kind: 'Snippet',
    insertText: 'lambda ${1:x}: ${2:x * 2}',
    detail: 'lambda表达式',
    documentation: 'lambda匿名函数'
  },
  {
    label: 'listcomp',
    kind: 'Snippet',
    insertText: '[${1:x} for ${2:x} in ${3:iterable}]',
    detail: '列表推导式',
    documentation: '列表推导式'
  },
  {
    label: 'dictcomp',
    kind: 'Snippet',
    insertText: '{${1:k}: ${2:v} for ${3:k}, ${4:v} in ${5:iterable}}',
    detail: '字典推导式',
    documentation: '字典推导式'
  },
  {
    label: 'setcomp',
    kind: 'Snippet',
    insertText: '{${1:x} for ${2:x} in ${3:iterable}}',
    detail: '集合推导式',
    documentation: '集合推导式'
  },
  {
    label: 'ifmain',
    kind: 'Snippet',
    insertText: 'if __name__ == \'__main__\':\n    ${1:main()}',
    detail: 'if __name__ == __main__',
    documentation: '主程序入口'
  },
  {
    label: 'import',
    kind: 'Snippet',
    insertText: 'import ${1:module}',
    detail: 'import语句',
    documentation: '导入模块'
  },
  {
    label: 'from',
    kind: 'Snippet',
    insertText: 'from ${1:module} import ${2:name}',
    detail: 'from import语句',
    documentation: '从模块导入'
  },
  {
    label: 'async def',
    kind: 'Snippet',
    insertText: 'async def ${1:function_name}(${2:args}):\n    """${3:docstring}"""\n    ${4:pass}',
    detail: '异步函数定义',
    documentation: '定义一个异步函数'
  },
  {
    label: 'await',
    kind: 'Snippet',
    insertText: 'await ${1:coroutine}',
    detail: 'await表达式',
    documentation: '等待异步操作完成'
  },
  {
    label: 'property',
    kind: 'Snippet',
    insertText: '@property\ndef ${1:name}(self):\n    """${2:docstring}"""\n    return self._${1:name}',
    detail: '@property装饰器',
    documentation: '属性装饰器'
  },
  {
    label: 'setter',
    kind: 'Snippet',
    insertText: '@${1:name}.setter\ndef ${1:name}(self, value):\n    self._${1:name} = value',
    detail: '@setter装饰器',
    documentation: '属性setter装饰器'
  },
  {
    label: 'staticmethod',
    kind: 'Snippet',
    insertText: '@staticmethod\ndef ${1:method_name}(${2:args}):\n    """${3:docstring}"""\n    ${4:pass}',
    detail: '@staticmethod装饰器',
    documentation: '静态方法装饰器'
  },
  {
    label: 'classmethod',
    kind: 'Snippet',
    insertText: '@classmethod\ndef ${1:method_name}(cls${2:, args}):\n    """${3:docstring}"""\n    ${4:pass}',
    detail: '@classmethod装饰器',
    documentation: '类方法装饰器'
  },
  {
    label: 'docstring',
    kind: 'Snippet',
    insertText: '"""\n${1:描述}\n\nArgs:\n    ${2:参数}: ${3:说明}\n\nReturns:\n    ${4:返回值说明}\n"""',
    detail: '函数文档字符串',
    documentation: 'Google风格的文档字符串'
  },
  {
    label: 'main',
    kind: 'Snippet',
    insertText: 'def main():\n    """主函数"""\n    ${1:pass}\n\n\nif __name__ == \'__main__\':\n    main()',
    detail: '主函数模板',
    documentation: '完整的主函数模板'
  }
];

/**
 * 注册Python补全提供器
 * @param {Object} monaco Monaco编辑器实例
 */
export function registerPythonCompletionProvider(monaco) {
  if (!monaco || !monaco.languages) {
    console.warn('Monaco未初始化，无法注册Python补全');
    return null;
  }

  // 注册补全提供器
  const provider = monaco.languages.registerCompletionItemProvider('python', {
    triggerCharacters: ['.', ' '],
    
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      };

      const suggestions = [];

      // 添加关键字补全
      PYTHON_KEYWORDS.forEach(keyword => {
        suggestions.push({
          label: keyword,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: keyword,
          range: range,
          detail: 'Python关键字',
          sortText: '1' + keyword // 关键字优先级较高
        });
      });

      // 添加内置函数补全
      PYTHON_BUILTINS.forEach(builtin => {
        suggestions.push({
          label: builtin,
          kind: monaco.languages.CompletionItemKind.Function,
          insertText: builtin + '($0)',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range: range,
          detail: 'Python内置函数',
          sortText: '2' + builtin
        });
      });

      // 添加代码片段
      PYTHON_SNIPPETS.forEach(snippet => {
        const kind = snippet.kind === 'Snippet' 
          ? monaco.languages.CompletionItemKind.Snippet
          : monaco.languages.CompletionItemKind.Text;
        
        suggestions.push({
          label: snippet.label,
          kind: kind,
          insertText: snippet.insertText,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range: range,
          detail: snippet.detail,
          documentation: snippet.documentation,
          sortText: '0' + snippet.label // 代码片段优先级最高
        });
      });

      return { suggestions };
    }
  });

  console.log('✅ Python补全提供器已注册');
  return provider;
}

/**
 * 注销补全提供器
 * @param {Object} provider 提供器实例
 */
export function disposePythonCompletionProvider(provider) {
  if (provider && provider.dispose) {
    provider.dispose();
    console.log('Python补全提供器已注销');
  }
}

export default {
  registerPythonCompletionProvider,
  disposePythonCompletionProvider,
  PYTHON_KEYWORDS,
  PYTHON_BUILTINS,
  PYTHON_SNIPPETS
};

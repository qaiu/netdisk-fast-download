# 浏览器TypeScript编译和Fetch API支持指南

## 概述

本项目实现了**纯前端TypeScript编译 + 后端ES5引擎 + Fetch API适配**的完整方案，允许用户在浏览器中编写TypeScript/ES6+代码，编译为ES5后在后端JavaScript引擎中执行。

## 架构设计

### 1. 浏览器端（前端编译）

```
用户编写TS/ES6+代码 
    ↓
TypeScript.js (浏览器内编译)
    ↓
ES5 JavaScript代码
    ↓
发送到后端执行
```

### 2. 后端（ES5执行环境）

```
接收ES5代码
    ↓
注入fetch polyfill + Promise
    ↓
注入JavaFetch桥接对象
    ↓
Nashorn引擎执行ES5代码
    ↓
fetch() 调用 → JavaFetch → JsHttpClient → Vert.x HTTP Client
```

## 已实现的功能

### ✅ 后端支持

1. **Promise Polyfill** (`fetch-runtime.js`)
   - 完整的Promise/A+实现
   - 支持 `then`、`catch`、`finally`
   - 支持 `Promise.all`、`Promise.race`
   - 支持 `Promise.resolve`、`Promise.reject`

2. **Fetch API Polyfill** (`fetch-runtime.js`)
   - 标准fetch接口实现
   - 支持所有HTTP方法（GET、POST、PUT、DELETE、PATCH）
   - 支持headers、body等选项
   - Response对象支持：
     - `text()` - 获取文本响应
     - `json()` - 解析JSON响应
     - `arrayBuffer()` - 获取字节数组
     - `status` - HTTP状态码
     - `ok` - 请求成功标志
     - `headers` - 响应头访问

3. **Java桥接** (`JsFetchBridge.java`)
   - 将fetch调用转换为JsHttpClient调用
   - 自动处理请求头、请求体
   - 支持代理配置
   - 安全的SSRF防护

4. **自动注入** (`JsParserExecutor.java` & `JsPlaygroundExecutor.java`)
   - 在JavaScript引擎初始化时自动注入fetch runtime
   - 提供`JavaFetch`全局对象
   - 与现有http对象共存

## 使用示例

### ES5风格（当前支持）

```javascript
function parse(shareLinkInfo, http, logger) {
    // 使用fetch API
    fetch("https://api.example.com/data")
        .then(function(response) {
            return response.json();
        })
        .then(function(data) {
            logger.info("数据: " + JSON.stringify(data));
        })
        .catch(function(error) {
            logger.error("错误: " + error.message);
        });
    
    // 或者使用传统的http对象
    var response = http.get("https://api.example.com/data");
    return response.body();
}
```

### TypeScript风格（需要前端编译）

用户在浏览器中编写：

```typescript
async function parse(shareLinkInfo: ShareLinkInfo, http: JsHttpClient, logger: JsLogger): Promise<string> {
    try {
        // 使用标准fetch API
        const response = await fetch("https://api.example.com/data");
        const data = await response.json();
        
        logger.info(`获取到数据: ${data.downloadUrl}`);
        return data.downloadUrl;
    } catch (error) {
        logger.error(`解析失败: ${error.message}`);
        throw error;
    }
}
```

浏览器内编译后的ES5代码（简化示例）：

```javascript
function parse(shareLinkInfo, http, logger) {
    return __awaiter(this, void 0, void 0, function() {
        var response, data;
        return __generator(this, function(_a) {
            switch(_a.label) {
                case 0:
                    return [4, fetch("https://api.example.com/data")];
                case 1:
                    response = _a.sent();
                    return [4, response.json()];
                case 2:
                    data = _a.sent();
                    logger.info("获取到数据: " + data.downloadUrl);
                    return [2, data.downloadUrl];
            }
        });
    });
}
```

## 前端TypeScript编译（待实现）

### 计划实现步骤

#### 1. 添加TypeScript编译器

在前端项目中添加`typescript.js`：

```bash
# 下载TypeScript编译器浏览器版本
cd webroot/static
wget https://cdn.jsdelivr.net/npm/typescript@latest/lib/typescript.js
```

或者在Vue项目中：

```bash
npm install typescript
```

#### 2. 创建编译工具类

`web-front/src/utils/tsCompiler.js`:

```javascript
import * as ts from 'typescript';

export function compileToES5(sourceCode, fileName = 'script.ts') {
  const result = ts.transpileModule(sourceCode, {
    compilerOptions: {
      target: ts.ScriptTarget.ES5,
      module: ts.ModuleKind.None,
      lib: ['es5', 'dom'],
      experimentalDecorators: false,
      emitDecoratorMetadata: false,
      downlevelIteration: true
    },
    fileName: fileName
  });

  return {
    js: result.outputText,
    diagnostics: result.diagnostics,
    sourceMap: result.sourceMapText
  };
}
```

#### 3. 更新Playground组件

在`Playground.vue`中添加编译选项：

```vue
<template>
  <div>
    <!-- 语言选择 -->
    <el-radio-group v-model="language">
      <el-radio label="javascript">JavaScript (ES5)</el-radio>
      <el-radio label="typescript">TypeScript/ES6+</el-radio>
    </el-radio-group>
    
    <!-- 编辑器 -->
    <monaco-editor
      v-model="code"
      :language="language"
      @save="handleSave"
    />
    
    <!-- 运行按钮 -->
    <el-button @click="executeCode">运行</el-button>
  </div>
</template>

<script>
import { compileToES5 } from '@/utils/tsCompiler';

export default {
  data() {
    return {
      language: 'javascript',
      code: ''
    };
  },
  methods: {
    async executeCode() {
      let codeToExecute = this.code;
      
      // 如果是TypeScript，先编译
      if (this.language === 'typescript') {
        const result = compileToES5(this.code);
        
        if (result.diagnostics && result.diagnostics.length > 0) {
          this.$message.error('TypeScript编译错误');
          console.error(result.diagnostics);
          return;
        }
        
        codeToExecute = result.js;
        console.log('编译后的ES5代码:', codeToExecute);
      }
      
      // 发送到后端执行
      const response = await playgroundApi.testScript(
        codeToExecute,
        this.shareUrl,
        this.pwd,
        this.method
      );
      
      this.showResult(response);
    }
  }
};
</script>
```

## Fetch Runtime详解

### Promise实现特性

```javascript
// 基本用法
var promise = new SimplePromise(function(resolve, reject) {
    setTimeout(function() {
        resolve("成功");
    }, 1000);
});

promise.then(function(value) {
    console.log(value); // "成功"
});

// 链式调用
promise
    .then(function(value) {
        return value + " - 第一步";
    })
    .then(function(value) {
        return value + " - 第二步";
    })
    .catch(function(error) {
        console.error(error);
    })
    .finally(function() {
        console.log("完成");
    });
```

### Fetch API特性

```javascript
// GET请求
fetch("https://api.example.com/data")
    .then(function(response) {
        console.log("状态码:", response.status);
        console.log("成功:", response.ok);
        return response.json();
    })
    .then(function(data) {
        console.log("数据:", data);
    });

// POST请求
fetch("https://api.example.com/submit", {
    method: "POST",
    headers: {
        "Content-Type": "application/json"
    },
    body: JSON.stringify({ key: "value" })
})
    .then(function(response) {
        return response.json();
    })
    .then(function(data) {
        console.log("响应:", data);
    });
```

## 兼容性说明

### 支持的特性

- ✅ Promise/A+ 完整实现
- ✅ Fetch API 标准接口
- ✅ async/await（编译后）
- ✅ 所有HTTP方法（GET、POST、PUT、DELETE、PATCH）
- ✅ Request headers配置
- ✅ Request body（string、JSON、FormData）
- ✅ Response.text()、Response.json()
- ✅ 与现有http对象共存

### 不支持的特性

- ❌ Blob对象（返回字节数组替代）
- ❌ FormData对象（使用简单对象替代）
- ❌ Request/Response对象构造函数
- ❌ Streams API
- ❌ Service Worker相关API

## 测试验证

### 1. 创建测试解析器

参考 `parser/src/main/resources/custom-parsers/fetch-demo.js`

### 2. 测试步骤

```bash
# 1. 编译项目
mvn clean package -DskipTests

# 2. 运行服务
java -jar web-service/target/netdisk-fast-download.jar

# 3. 访问演练场
浏览器打开: http://localhost:6401/playground

# 4. 加载fetch-demo.js并测试
```

### 3. 验证fetch功能

在演练场中运行：

```javascript
function parse(shareLinkInfo, http, logger) {
    logger.info("测试fetch API");
    
    var result = null;
    fetch("https://httpbin.org/get")
        .then(function(response) {
            logger.info("状态码: " + response.status);
            return response.json();
        })
        .then(function(data) {
            logger.info("响应: " + JSON.stringify(data));
            result = "SUCCESS";
        })
        .catch(function(error) {
            logger.error("错误: " + error.message);
        });
    
    // 等待完成
    var timeout = 5000;
    var start = Date.now();
    while (result === null && (Date.now() - start) < timeout) {
        java.lang.Thread.sleep(10);
    }
    
    return result || "https://example.com/download";
}
```

## 安全性

### SSRF防护

JsHttpClient已实现SSRF防护：
- 拦截内网IP访问（127.0.0.1、10.x.x.x、192.168.x.x等）
- 拦截云服务元数据API（169.254.169.254等）
- DNS解析检查

### 沙箱隔离

- JavaScript引擎使用SecurityClassFilter
- 禁用Java对象访问
- 限制文件系统访问

## 性能优化

1. **Fetch runtime缓存**
   - 首次加载后缓存在静态变量中
   - 避免重复读取资源文件

2. **Promise异步执行**
   - 使用setTimeout(0)实现异步
   - 避免阻塞主线程

3. **工作线程池**
   - JsParserExecutor使用Vert.x工作线程池
   - JsPlaygroundExecutor使用独立线程池

## 相关文件

### 后端代码
- `parser/src/main/resources/fetch-runtime.js` - Fetch和Promise polyfill
- `parser/src/main/java/cn/qaiu/parser/customjs/JsFetchBridge.java` - Java桥接层
- `parser/src/main/java/cn/qaiu/parser/customjs/JsParserExecutor.java` - 解析器执行器
- `parser/src/main/java/cn/qaiu/parser/customjs/JsPlaygroundExecutor.java` - 演练场执行器

### 示例代码
- `parser/src/main/resources/custom-parsers/fetch-demo.js` - Fetch API演示

### 前端代码（待实现）
- `web-front/src/utils/tsCompiler.js` - TypeScript编译工具
- `web-front/src/views/Playground.vue` - 演练场界面

## 下一步计划

1. ✅ 实现后端fetch polyfill
2. ✅ 实现Promise polyfill
3. ✅ 集成到JsParserExecutor
4. ⏳ 前端添加TypeScript编译器
5. ⏳ 更新Playground UI支持TS/ES6+
6. ⏳ 添加Monaco编辑器类型提示
7. ⏳ 编写更多示例和文档

## 总结

通过这个方案，我们实现了：
1. **无需Node环境** - 纯浏览器编译 + Java后端执行
2. **标准API** - 使用标准fetch和Promise API
3. **向后兼容** - 现有http对象仍然可用
4. **安全可靠** - SSRF防护和沙箱隔离
5. **易于使用** - 简单的API，无需学习成本

用户可以在浏览器中用现代JavaScript/TypeScript编写代码，自动编译为ES5后在后端安全执行，同时享受fetch API的便利性。

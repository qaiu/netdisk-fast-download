# SSRF防护策略说明

## 🛡️ 当前防护策略（已优化）

为了保证功能可用性和安全性的平衡，SSRF防护策略已调整为**宽松模式**，只拦截明确的危险请求。

---

## ✅ 允许的请求

以下请求**不会被拦截**，可以正常使用：

### 1. 外网域名 ✅
```javascript
http.get('https://www.example.com/api/data')  // ✅ 允许
http.get('http://api.github.com/repos')       // ✅ 允许
http.get('https://cdn.jsdelivr.net/file.js')  // ✅ 允许
```

### 2. 公网IP ✅
```javascript
http.get('http://8.8.8.8/api')               // ✅ 允许（公网IP）
http.get('https://1.1.1.1/dns-query')        // ✅ 允许（Cloudflare DNS）
```

### 3. DNS解析失败的域名 ✅
```javascript
// 即使DNS暂时无法解析，也允许继续
http.get('http://some-new-domain.com')       // ✅ 允许（DNS失败不拦截）
```

---

## ❌ 拦截的请求

以下请求**会被拦截**，保护服务器安全：

### 1. 本地回环地址 ❌
```javascript
http.get('http://127.0.0.1:8080/admin')      // ❌ 拦截
http.get('http://localhost/secret')          // ❌ 拦截（解析到127.0.0.1）
http.get('http://[::1]/api')                 // ❌ 拦截（IPv6本地）
```

### 2. 内网IP地址 ❌
```javascript
http.get('http://192.168.1.1/config')        // ❌ 拦截（内网C类）
http.get('http://10.0.0.5/admin')            // ❌ 拦截（内网A类）
http.get('http://172.16.0.1/api')            // ❌ 拦截（内网B类）
```

### 3. 云服务元数据API ❌
```javascript
http.get('http://169.254.169.254/latest/meta-data/')           // ❌ 拦截（AWS/阿里云）
http.get('http://metadata.google.internal/computeMetadata/')   // ❌ 拦截（GCP）
http.get('http://100.100.100.200/latest/meta-data/')          // ❌ 拦截（阿里云）
```

### 4. 解析到内网的域名 ❌
```javascript
// 如果域名DNS解析指向内网IP，会被拦截
http.get('http://internal.company.com')      // ❌ 拦截（如果解析到192.168.x.x）
```

---

## 🔍 检测逻辑

### 防护流程

```
用户请求 URL
    ↓
1. 检查是否为云服务元数据API域名
    ├─ 是 → ❌ 拦截
    └─ 否 → 继续
    ↓
2. 检查Host是否为IP地址格式
    ├─ 是 → 检查是否为内网IP
    │       ├─ 是 → ❌ 拦截
    │       └─ 否 → ✅ 允许
    └─ 否（域名）→ 继续
    ↓
3. 尝试DNS解析域名
    ├─ 解析成功
    │   ├─ IP为内网 → ❌ 拦截
    │   └─ IP为公网 → ✅ 允许
    └─ 解析失败 → ✅ 允许（不阻止）
```

### 内网IP判断规则

使用正则表达式匹配：

```java
^(127\..*|                    // 127.0.0.0/8    - 本地回环
  10\..*|                     // 10.0.0.0/8     - 内网A类
  172\.(1[6-9]|2[0-9]|3[01])\..*|  // 172.16.0.0/12  - 内网B类
  192\.168\..*|               // 192.168.0.0/16 - 内网C类
  169\.254\..*|               // 169.254.0.0/16 - 链路本地
  ::1|                        // IPv6本地回环
  [fF][cCdD].*)               // IPv6唯一本地地址
```

---

## 📊 策略对比

| 场景 | 严格模式（原版） | 宽松模式（当前）✅ |
|------|-----------------|-------------------|
| 外网域名 | 可能被拦截 | ✅ 允许 |
| DNS解析失败 | 被拦截 | ✅ 允许 |
| 公网IP | ✅ 允许 | ✅ 允许 |
| 内网IP | ❌ 拦截 | ❌ 拦截 |
| 本地回环 | ❌ 拦截 | ❌ 拦截 |
| 云服务元数据 | ❌ 拦截 | ❌ 拦截 |
| 解析到内网的域名 | ❌ 拦截 | ❌ 拦截 |

---

## 🧪 测试用例

### 测试1: 正常外网请求 ✅

```javascript
function parse(shareLinkInfo, http, logger) {
    try {
        var response = http.get('https://httpbin.org/get');
        logger.info('✅ 成功访问外网: ' + response.substring(0, 50));
        return 'SUCCESS';
    } catch (e) {
        logger.error('❌ 外网请求被拦截（不应该）: ' + e.message);
        return 'FAILED';
    }
}
```

**期望结果**: ✅ 成功访问

### 测试2: 内网攻击拦截 ❌

```javascript
function parse(shareLinkInfo, http, logger) {
    try {
        var response = http.get('http://127.0.0.1:6400/');
        logger.error('❌ 内网访问成功（不应该）');
        return 'SECURITY_BREACH';
    } catch (e) {
        logger.info('✅ 内网访问被拦截: ' + e.message);
        return 'PROTECTED';
    }
}
```

**期望结果**: ✅ 被拦截，显示"安全拦截: 禁止访问内网IP地址"

### 测试3: 云服务元数据拦截 ❌

```javascript
function parse(shareLinkInfo, http, logger) {
    try {
        var response = http.get('http://169.254.169.254/latest/meta-data/');
        logger.error('❌ 元数据API访问成功（不应该）');
        return 'SECURITY_BREACH';
    } catch (e) {
        logger.info('✅ 元数据API被拦截: ' + e.message);
        return 'PROTECTED';
    }
}
```

**期望结果**: ✅ 被拦截，显示"安全拦截: 禁止访问云服务元数据API"

---

## 🎯 安全建议

### ✅ 当前策略适用于

- 需要访问多种外网API的场景
- 网盘、文件分享等服务
- 需要爬取外网资源
- 对可用性要求较高的环境

### ⚠️ 如需更严格的防护

如果你的应用场景需要更严格的安全控制，可以考虑：

#### 1. 白名单模式

只允许访问特定域名：

```java
private static final String[] ALLOWED_DOMAINS = {
    "api.example.com",
    "cdn.example.com"
};

private void validateUrlSecurity(String url) {
    String host = new URI(url).getHost();
    boolean allowed = false;
    for (String domain : ALLOWED_DOMAINS) {
        if (host.equals(domain) || host.endsWith("." + domain)) {
            allowed = true;
            break;
        }
    }
    if (!allowed) {
        throw new SecurityException("域名不在白名单中");
    }
}
```

#### 2. 协议限制

只允许HTTPS：

```java
String scheme = uri.getScheme();
if (!"https".equalsIgnoreCase(scheme)) {
    throw new SecurityException("仅允许HTTPS协议");
}
```

#### 3. 端口限制

只允许标准端口（80, 443）：

```java
int port = uri.getPort();
if (port != -1 && port != 80 && port != 443) {
    throw new SecurityException("仅允许标准HTTP/HTTPS端口");
}
```

---

## 📝 配置说明

### 修改黑名单

在 `JsHttpClient.java` 中修改：

```java
// 危险域名黑名单
private static final String[] DANGEROUS_HOSTS = {
    "localhost",
    "169.254.169.254",           // AWS/阿里云元数据
    "metadata.google.internal",  // GCP元数据
    "100.100.100.200",          // 阿里云元数据
    // 添加更多...
};
```

### 修改内网IP规则

```java
// 内网IP正则表达式
private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
    "^(127\\..*|10\\..*|172\\.(1[6-9]|2[0-9]|3[01])\\..*|192\\.168\\..*|169\\.254\\..*|::1|[fF][cCdD].*)"
);
```

---

## 🔄 策略变更历史

### v2 - 宽松模式（当前）✅
- **日期**: 2025-11-29
- **变更**: 
  - DNS解析失败不拦截
  - URL格式错误不拦截
  - 只拦截明确的内网攻击
- **原因**: 避免误杀正常外网请求

### v1 - 严格模式
- **日期**: 2025-11-28
- **变更**: 初始实现
- **问题**: 过于严格，导致很多正常请求被拦截

---

## 📞 反馈

如果遇到以下情况，请考虑调整策略：

1. **正常外网请求被拦截** → 检查DNS解析、域名是否在黑名单
2. **内网攻击未被拦截** → 添加更多内网IP段或域名黑名单
3. **性能问题** → 考虑缓存DNS解析结果

---

**最后更新**: 2025-11-29  
**当前版本**: v2 - 宽松模式  
**安全级别**: ⚠️ 中等（建议生产环境根据实际需求调整）


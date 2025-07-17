# 解析接口详解

## 基础解析接口

### 1. 通用解析接口

通用解析接口支持所有类型的网盘分享链接：

```bash
# 基础格式
GET /parser?url={分享链接}&pwd={密码}

# 返回 JSON 格式
GET /json/parser?url={分享链接}&pwd={密码}
```

**示例请求:**
```bash
# 蓝奏云普通分享
curl "http://localhost:6400/json/parser?url=https://lanzoux.com/ia2cntg"

# 奶牛快传分享
curl "http://localhost:6400/json/parser?url=https://cowtransfer.com/s/9a644fe3e3a748"

# 亿方云加密分享
curl "http://localhost:6400/json/parser?url=https://v2.fangcloud.com/sharing/e5079007dc31226096628870c7&pwd=QAIU"
```

### 2. 短链接口

短链接口提供更简洁的调用方式：

```bash
# 基础格式
GET /d/{网盘标识}/{分享key}@{密码}

# 返回 JSON 格式  
GET /json/{网盘标识}/{分享key}@{密码}
```

**示例请求:**
```bash
# 蓝奏云
curl "http://localhost:6400/json/lz/ia2cntg"

# 奶牛快传
curl "http://localhost:6400/json/cow/9a644fe3e3a748"

# 亿方云加密分享
curl "http://localhost:6400/json/fc/e5079007dc31226096628870c7@QAIU"
```

## 响应示例

### 成功响应

```json
{
  "code": 200,
  "msg": "success", 
  "success": true,
  "count": 0,
  "data": {
    "shareKey": "lz:xxx",
    "directLink": "https://vip.d0.baidupan.com/file/?xxx", 
    "cacheHit": true,
    "expires": "2024-09-18 01:48:02",
    "expiration": 1726638482825
  },
  "timestamp": 1726637151902
}
```

### 错误响应

```json
{
  "code": 404,
  "msg": "分享链接不存在或已失效",
  "success": false,
  "data": null,
  "timestamp": 1726637151902
}
```

## 特殊网盘说明

### 移动云云空间

移动云的分享key需要从分享链接中提取 `data` 参数：

```bash
# 原始分享链接
https://www.ecpan.cn/web/share/xxx?data=abcd1234

# 分享key为: abcd1234
curl "http://localhost:6400/json/ec/abcd1234"
```

### 小飞机网盘

小飞机网盘有IP限制，大陆IP可能被拦截，建议配置代理：

```bash
# 普通分享
curl "http://localhost:6400/json/fj/sharekey"

# 加密分享（密码可忽略）
curl "http://localhost:6400/json/fj/sharekey@password"
```

### 国际网盘

Google Drive、OneDrive、Dropbox等国际网盘可能需要代理：

```bash
# Google Drive
curl "http://localhost:6400/json/pgd/file_id"

# OneDrive
curl "http://localhost:6400/json/pod/share_token"
```

## 错误处理

### 常见错误码

| 错误码 | 说明 | 解决方法 |
|--------|------|----------|
| 400 | 请求参数错误 | 检查URL格式和参数 |
| 404 | 分享链接不存在 | 验证分享链接有效性 |
| 429 | 请求频率过高 | 降低请求频率 |
| 500 | 服务器错误 | 重试或联系管理员 |

### 重试策略

建议实现指数退避重试：

```javascript
async function parseWithRetry(url, maxRetries = 3) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      const response = await fetch(`/json/parser?url=${encodeURIComponent(url)}`);
      const data = await response.json();
      
      if (data.success) {
        return data;
      }
      
      if (data.code === 404) {
        // 链接不存在，不需要重试
        throw new Error(data.msg);
      }
      
    } catch (error) {
      if (i === maxRetries - 1) {
        throw error;
      }
      
      // 指数退避
      await new Promise(resolve => setTimeout(resolve, Math.pow(2, i) * 1000));
    }
  }
}
```

## 性能优化

### 缓存机制

系统会自动缓存解析结果，减少重复请求：

- **缓存时间**: 默认30分钟
- **缓存标识**: 通过 `cacheHit` 字段判断
- **过期时间**: 通过 `expires` 字段获取

### 请求优化

1. **URL编码**: 建议对分享链接进行URL编码
2. **批量请求**: 避免短时间内大量请求
3. **连接复用**: 使用HTTP/1.1的Keep-Alive或HTTP/2

```javascript
// URL编码示例
const shareUrl = 'https://pan.baidu.com/s/1xxx';
const encodedUrl = encodeURIComponent(shareUrl);
const apiUrl = `/json/parser?url=${encodedUrl}`;
```

## 安全注意事项

1. **输入验证**: 验证分享链接格式
2. **频率限制**: 实现客户端限流
3. **错误日志**: 记录异常情况便于排查
4. **敏感信息**: 不要在日志中记录密码等敏感信息

::: warning 重要提醒
- 请遵守各网盘服务商的使用条款
- 不要用于商业用途的大规模解析
- 建议在生产环境中配置反向代理和CDN
:::
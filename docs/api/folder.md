# 文件夹解析接口

文件夹解析功能支持解析网盘的文件夹分享链接，目前支持蓝奏云、蓝奏云优享和小飞机网盘。

## 支持的网盘

| 网盘名称 | 标识 | 文件夹支持 | 说明 |
|---------|------|------------|------|
| 蓝奏云 | lz | ✅ | 完全支持 |
| 蓝奏云优享 | iz | ✅ | 完全支持 |
| 小飞机网盘 | fj | ✅ | 需要代理 |

## 接口说明

### 文件夹列表接口

获取文件夹中的文件列表：

```
GET /v2/getFileList?url={分享链接}&pwd={密码}
```

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| url | string | 是 | 文件夹分享链接 |
| pwd | string | 否 | 分享密码（如果有） |

### 响应格式

```json
{
  "code": 200,
  "msg": "success",
  "success": true,
  "data": [
    {
      "fileName": "文件名.zip",
      "fileId": "unique_file_id",
      "fileIcon": null,
      "size": 1024000,
      "sizeStr": "1.0 MB",
      "fileType": "file",
      "filePath": null,
      "createTime": "17 小时前",
      "updateTime": null,
      "createBy": null,
      "description": null,
      "downloadCount": 156,
      "panType": "lz",
      "parserUrl": "https://your-domain.com/d/lz/file_id",
      "extParameters": null
    },
    {
      "fileName": "子文件夹",
      "fileId": "folder_id",
      "fileIcon": null,
      "size": 0,
      "sizeStr": "0 B",
      "fileType": "folder",
      "filePath": null,
      "createTime": "2天前",
      "updateTime": null,
      "createBy": null,
      "description": null,
      "downloadCount": 0,
      "panType": "lz",
      "parserUrl": "https://your-domain.com/v2/getFileList?url=folder_share_url",
      "extParameters": null
    }
  ]
}
```

## 字段说明

### 文件对象字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| fileName | string | 文件或文件夹名称 |
| fileId | string | 文件唯一标识 |
| fileIcon | string | 文件图标URL（通常为null） |
| size | long | 文件大小（字节） |
| sizeStr | string | 格式化的文件大小 |
| fileType | string | 类型：file(文件) 或 folder(文件夹) |
| filePath | string | 文件路径（通常为null） |
| createTime | string | 创建时间 |
| updateTime | string | 更新时间 |
| createBy | string | 创建者 |
| description | string | 文件描述 |
| downloadCount | int | 下载次数 |
| panType | string | 网盘类型标识 |
| parserUrl | string | 解析URL（文件直接下载，文件夹继续解析） |
| extParameters | object | 扩展参数 |

## 使用示例

### 1. 蓝奏云文件夹解析

```bash
# 解析蓝奏云文件夹
curl "http://localhost:6400/v2/getFileList?url=https://lanzoux.com/b12345678"

# 带密码的文件夹
curl "http://localhost:6400/v2/getFileList?url=https://lanzoux.com/b12345678&pwd=1234"
```

### 2. 小飞机网盘文件夹解析

```bash
# 解析小飞机网盘文件夹
curl "http://localhost:6400/v2/getFileList?url=https://www.feijipan.com/s/folder123"
```

### 3. JavaScript 调用示例

```javascript
async function getFileList(shareUrl, password = '') {
  const params = new URLSearchParams({
    url: shareUrl
  });
  
  if (password) {
    params.append('pwd', password);
  }
  
  try {
    const response = await fetch(`/v2/getFileList?${params}`);
    const data = await response.json();
    
    if (data.success) {
      return data.data;
    } else {
      throw new Error(data.msg);
    }
  } catch (error) {
    console.error('文件夹解析失败:', error);
    throw error;
  }
}

// 使用示例
getFileList('https://lanzoux.com/b12345678', '1234')
  .then(files => {
    files.forEach(file => {
      if (file.fileType === 'file') {
        console.log(`文件: ${file.fileName} (${file.sizeStr})`);
        console.log(`下载地址: ${file.parserUrl}`);
      } else {
        console.log(`文件夹: ${file.fileName}`);
        console.log(`子目录地址: ${file.parserUrl}`);
      }
    });
  })
  .catch(error => {
    console.error('解析失败:', error);
  });
```

### 4. Python 调用示例

```python
import requests
import json

def get_file_list(share_url, password=''):
    """获取文件夹文件列表"""
    params = {'url': share_url}
    if password:
        params['pwd'] = password
    
    try:
        response = requests.get(
            'http://localhost:6400/v2/getFileList',
            params=params,
            timeout=30
        )
        data = response.json()
        
        if data['success']:
            return data['data']
        else:
            raise Exception(data['msg'])
            
    except requests.RequestException as e:
        raise Exception(f'请求失败: {e}')

# 使用示例
try:
    files = get_file_list('https://lanzoux.com/b12345678', '1234')
    
    for file_item in files:
        if file_item['fileType'] == 'file':
            print(f"文件: {file_item['fileName']} ({file_item['sizeStr']})")
            print(f"下载地址: {file_item['parserUrl']}")
        else:
            print(f"文件夹: {file_item['fileName']}")
            print(f"子目录地址: {file_item['parserUrl']}")
            
except Exception as e:
    print(f'解析失败: {e}')
```

## 递归解析示例

### JavaScript 递归遍历文件夹

```javascript
async function traverseFolder(shareUrl, password = '', path = '') {
  const files = await getFileList(shareUrl, password);
  const result = [];
  
  for (const file of files) {
    const filePath = path ? `${path}/${file.fileName}` : file.fileName;
    
    if (file.fileType === 'file') {
      result.push({
        path: filePath,
        name: file.fileName,
        size: file.size,
        sizeStr: file.sizeStr,
        downloadUrl: file.parserUrl,
        type: 'file'
      });
    } else if (file.fileType === 'folder') {
      // 递归解析子文件夹
      const subFiles = await traverseFolder(file.parserUrl, '', filePath);
      result.push({
        path: filePath,
        name: file.fileName,
        type: 'folder',
        children: subFiles
      });
    }
  }
  
  return result;
}

// 使用示例
traverseFolder('https://lanzoux.com/b12345678', '1234')
  .then(tree => {
    console.log('文件夹结构:', JSON.stringify(tree, null, 2));
  });
```

## 错误处理

### 常见错误

| 错误码 | 说明 | 解决方法 |
|--------|------|----------|
| 400 | 请求参数错误 | 检查URL格式 |
| 404 | 文件夹不存在或已失效 | 验证分享链接 |
| 403 | 密码错误 | 检查分享密码 |
| 500 | 服务器内部错误 | 重试或联系管理员 |
| 429 | 请求频率过高 | 降低请求频率 |

### 错误响应示例

```json
{
  "code": 404,
  "msg": "分享文件夹不存在或已失效",
  "success": false,
  "data": null,
  "timestamp": 1726637151902
}
```

## 性能考虑

### 1. 缓存机制

- 文件夹列表会被缓存15分钟
- 大文件夹可能需要较长解析时间
- 建议客户端实现loading状态

### 2. 限流建议

- 单个IP建议限制并发为5个
- 递归遍历时添加延时避免被限制
- 大文件夹建议分页处理

### 3. 优化策略

```javascript
// 带延时的递归解析
async function traverseFolderWithDelay(shareUrl, password = '', delay = 1000) {
  const files = await getFileList(shareUrl, password);
  
  for (const file of files) {
    if (file.fileType === 'folder') {
      // 添加延时避免频率限制
      await new Promise(resolve => setTimeout(resolve, delay));
      await traverseFolderWithDelay(file.parserUrl, '', delay);
    }
  }
}
```

## 限制说明

1. **网盘限制**: 仅支持蓝奏云系列和小飞机网盘
2. **深度限制**: 建议递归深度不超过10层
3. **文件数量**: 单个文件夹建议不超过1000个文件
4. **频率限制**: 建议间隔1秒进行递归请求

::: warning 注意事项
- 文件夹解析可能比单文件解析耗时更长
- 大文件夹建议使用异步处理
- 小飞机网盘需要配置代理才能正常解析
- 递归遍历时注意控制请求频率，避免被限制
:::
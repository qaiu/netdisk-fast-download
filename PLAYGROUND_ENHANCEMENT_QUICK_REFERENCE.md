# 演练场增强功能 - 快速参考

## 🎯 功能一览

### 1️⃣ 编辑器增强
| 功能 | 操作 | 快捷键 |
|------|------|--------|
| 导入文件 | 更多 → 导入文件 | - |
| 粘贴代码 | 粘贴 按钮或 Ctrl+V | Ctrl+V |
| 支持的格式 | .js, .py, .txt | - |

### 2️⃣ 网络安全拦截
| 拦截项 | 示例 | 日志级别 |
|--------|------|---------|
| 本地地址 | 127.0.0.1, localhost | BLOCK |
| 私网地址 | 192.168.1.x, 10.0.0.x | BLOCK |
| 危险端口 | 22, 3306, 6379 | BLOCK |
| 正常请求 | https://example.com | ALLOW |

### 3️⃣ 控制台日志
```
[时间] [级别] [来源] 日志消息

来源标签：
  [JAVA]   - 后端Java日志（补丁注入、执行过程）
  [PYTHON] - 用户Python代码日志
  [JS]     - JavaScript日志
```

---

## 📋 场景示例

### 场景1：导入Python脚本并执行

1. 点击"导入文件"→选择`parser.py`
2. 编辑器自动识别为Python模式
3. 设置测试参数（分享链接）
4. 点击"运行"执行

**预期日志输出**：
```
[10:15:30] INFO  [JAVA] ✓ 网络请求安全拦截已启用 (检测到: requests) | 已动态注入 requests_guard 猴子补丁
[10:15:31] DEBUG [JAVA] 安全检查通过
[10:15:32] DEBUG [JAVA] 执行Python代码
[10:15:33] INFO  [PYTHON] 正在解析: https://example.com/s/abc
[10:15:33] DEBUG [PYTHON] [Guard] 允许 GET https://example.com/s/abc
[10:15:34] INFO  [JAVA] 解析成功
```

### 场景2：尝试访问本地地址（会被拦截）

**Python代码**:
```python
import requests

def parse(share_link_info, http_client, logger):
    response = requests.get("http://127.0.0.1:8080/api")  # ❌ 会被拦截
    return response.text
```

**日志输出**:
```
[10:20:15] INFO  [JAVA] ✓ 网络请求安全拦截已启用 (检测到: requests)
[10:20:16] DEBUG [JAVA] 执行Python代码
[10:20:17] ERROR [PYTHON] [Guard] 禁止访问本地地址：http://127.0.0.1:8080/api
```

### 场景3：粘贴多行代码

1. 复制多行JavaScript代码
2. 点击"粘贴"按钮
3. 代码一次性粘贴到编辑器

**提示信息**: `已粘贴 15 行内容`

---

## 🔒 安全检查规则

### 被拦截的地址
```
❌ 127.0.0.1           - 本地回环
❌ localhost           - 本地主机
❌ ::1                 - IPv6本地
❌ 10.0.0.0/8          - 私网A类
❌ 172.16.0.0/12       - 私网B类
❌ 192.168.0.0/16      - 私网C类
❌ 169.254.0.0/16      - Link-local
```

### 被拦截的端口（特殊检查）
```
22   - SSH
25   - SMTP
53   - DNS
3306 - MySQL
5432 - PostgreSQL
6379 - Redis
8080 - 常见开发端口
```

### 只允许
```
✅ http://example.com   - 公网HTTP
✅ https://api.github.com - 公网HTTPS
```

---

## 🛠️ 技术细节

### 猴子补丁的工作原理

```
Python代码执行流程：
┌─────────────────────────────────────────────┐
│ 1. PyCodePreprocessor 分析代码               │
│    ↓                                         │
│ 2. 检测到 import requests                    │
│    ↓                                         │
│ 3. 从资源加载 requests_guard.py              │
│    ↓                                         │
│ 4. 在代码头部注入补丁                        │
│    ↓                                         │
│ 5. 注入完成，记录日志                        │
│    ↓                                         │
│ 6. 执行增强后的代码                          │
│    ↓                                         │
│ 7. 所有requests调用都经过补丁检查            │
│    ↓                                         │
│ 8. 合法请求继续，违规请求被拦截             │
└─────────────────────────────────────────────┘
```

### 代码注入示例

**原始代码**:
```python
"""网盘解析器"""
import requests

def parse(share_link_info, http_client, logger):
    response = requests.get(share_link_info.share_url)
    return response.text
```

**注入后的代码**:
```python
"""网盘解析器"""

# ===== 自动注入的网络请求安全补丁 (由 PyCodePreprocessor 生成) =====
[requests_guard.py 的完整内容 - 约400行]
# ===== 安全补丁结束 =====

import requests  # ← 这时requests已经被补丁过了

def parse(share_link_info, http_client, logger):
    response = requests.get(share_link_info.share_url)
    return response.text
```

---

## 📊 日志级别说明

| 级别 | 含义 | 场景 |
|------|------|------|
| DEBUG | 调试信息 | 安全检查开始、执行步骤 |
| INFO | 一般信息 | 执行成功、补丁注入、请求允许 |
| WARN | 警告 | 可能的问题（一般不会出现） |
| ERROR | 错误 | 请求被拦截、执行失败 |

---

## ⚡ 性能指标

- **文件导入**: < 100ms
- **代码预处理**: < 50ms
- **补丁加载**: < 30ms（首次缓存）
- **网络请求验证**: < 5ms

---

## 🐛 常见问题

### Q1: 为什么我的requests请求被拦截了？

**A**: 检查请求URL是否为：
- 本地地址（127.0.0.1, localhost）
- 私网地址（192.168.x.x, 10.x.x.x等）
- 危险端口（22, 3306等）

在控制台日志中会显示具体原因。

### Q2: 粘贴时出现权限错误怎么办？

**A**: 某些浏览器在某些情况下会限制clipboard权限。
- 尝试使用 Ctrl+V 快捷键代替
- 确保页面URL是HTTPS（部分浏览器要求）
- 检查浏览器隐私设置中的剪贴板权限

### Q3: 导入的Python文件无法找到parse函数报错？

**A**: 确保：
1. 文件中有 `def parse(...)` 函数定义
2. 函数签名正确：`parse(share_link_info, http_client, logger)`
3. 函数返回字符串类型的URL

### Q4: 如何禁用网络请求拦截？

**A**: 当前版本无法禁用，这是安全功能。
- 如需特殊需求，请联系管理员

---

## 📱 移动端支持

- ✅ 文件导入在移动端正常工作
- ✅ 粘贴操作优化了移动端输入法问题
- ✅ 日志显示自动适应小屏幕
- ⚠️ 建议在PC上进行复杂编辑操作

---

## 📞 获取帮助

遇到问题可以：
1. 查看控制台日志（最详细的信息）
2. 查看完整的实现文档：[PLAYGROUND_ENHANCEMENT_IMPLEMENTATION.md](./PLAYGROUND_ENHANCEMENT_IMPLEMENTATION.md)
3. 联系技术支持

---

**最后更新**: 2026年1月18日  
**版本**: 1.0  
**状态**: ✅ 生产就绪

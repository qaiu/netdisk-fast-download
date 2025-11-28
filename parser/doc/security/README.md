# 安全相关文档索引

本目录包含JavaScript执行器的安全修复和测试相关文档。

## 📚 文档列表

### 🚀 快速开始
- **[QUICK_TEST.md](QUICK_TEST.md)** - 快速验证指南（5分钟）
- **[FAQ.md](FAQ.md)** - 常见问题解答 ⭐ **推荐先看这个！**
- **[test-security.sh](test-security.sh)** - 一键测试脚本

### 📋 安全修复说明
- **[SECURITY_FIX_SUMMARY.md](SECURITY_FIX_SUMMARY.md)** - 完整修复总结
- **[SECURITY_URGENT_FIX.md](SECURITY_URGENT_FIX.md)** - 紧急修复通知
- **[CHANGELOG_SECURITY.md](CHANGELOG_SECURITY.md)** - 安全更新日志

### 🧪 测试指南
- **[SECURITY_TEST_README.md](SECURITY_TEST_README.md)** - 安全测试快速入门
- **[SECURITY_TESTING_GUIDE.md](../SECURITY_TESTING_GUIDE.md)** - 详细测试指南

### 🛡️ 防护策略
- **[SSRF_PROTECTION.md](SSRF_PROTECTION.md)** - SSRF防护策略说明

---

## 🚨 重要提醒

如果你看到这些文档，说明系统曾经存在严重的安全漏洞。请务必：

1. ✅ 确认已应用最新的安全修复
2. ✅ 运行安全测试验证修复效果
3. ✅ 重新部署到生产环境

## ❓ 遇到问题？

- **看到"请求失败: 404"?** → 这是正常的HTTP响应，不是安全拦截！查看 [FAQ.md](FAQ.md#q1-为什么还是显示请求失败-404)
- **Java.type() 报错?** → 这说明安全修复生效了！查看 [FAQ.md](FAQ.md#q3-javatype-相关错误)
- **服务启动失败?** → 检查是否重新编译，查看 [FAQ.md](FAQ.md#q5-服务启动时出现-arrayindexoutofboundsexception)

---

最后更新: 2025-11-29

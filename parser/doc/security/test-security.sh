#!/bin/bash

# JavaScript执行器安全测试脚本
# 用于快速执行所有安全测试用例

echo "========================================"
echo "  JavaScript执行器安全测试"
echo "========================================"
echo ""

# 进入parser目录
cd "$(dirname "$0")"

echo "📋 测试用例列表:"
echo "  1. 系统命令执行测试 🔴"
echo "  2. 文件系统访问测试 🔴"
echo "  3. 系统属性访问测试 🟡"
echo "  4. 反射攻击测试 🔴"
echo "  5. 网络Socket测试 🔴"
echo "  6. JVM退出测试 🔴"
echo "  7. HTTP客户端SSRF测试 🟡"
echo ""

echo "⚠️  警告: 这些测试包含危险代码，仅用于安全验证！"
echo ""

read -p "是否继续执行测试? (y/n): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "测试已取消"
    exit 1
fi

echo ""
echo "🚀 开始执行测试..."
echo ""

# 执行JUnit测试
mvn test -Dtest=SecurityTest

# 检查测试结果
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 测试执行完成"
    echo ""
    echo "📊 请检查测试日志，确认:"
    echo "  ✓ 所有高危测试（系统命令、文件访问等）应该失败"
    echo "  ✓ 所有日志中不应该出现【安全漏洞】标记"
    echo "  ⚠ 如果出现安全漏洞警告，请立即修复！"
else
    echo ""
    echo "❌ 测试执行失败"
fi

echo ""
echo "📖 详细文档请参考: doc/SECURITY_TESTING_GUIDE.md"
echo ""


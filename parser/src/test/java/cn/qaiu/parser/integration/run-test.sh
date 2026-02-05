#!/bin/bash

# 带认证的网盘解析集成测试运行脚本

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
CONFIG_FILE="$SCRIPT_DIR/../resources/auth-test.properties"
TEMPLATE_FILE="$SCRIPT_DIR/../resources/auth-test.properties.template"

echo "========================================="
echo "  网盘解析集成测试运行器"
echo "========================================="
echo

# 检查配置文件
if [ ! -f "$CONFIG_FILE" ]; then
    echo "❌ 配置文件不存在: $CONFIG_FILE"
    echo
    echo "请先创建配置文件："
    echo "  cp $TEMPLATE_FILE $CONFIG_FILE"
    echo
    echo "然后编辑配置文件，填入真实的 Cookie 和分享链接"
    exit 1
fi

echo "✓ 找到配置文件: $CONFIG_FILE"
echo

# 检查配置文件是否为空或只有模板
if ! grep -q "qk.url=http" "$CONFIG_FILE" && \
   ! grep -q "uc.url=http" "$CONFIG_FILE" && \
   ! grep -q "fj.url=http" "$CONFIG_FILE"; then
    echo "⚠️  配置文件似乎未填写实际数据"
    echo
    read -p "是否继续？(y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已取消"
        exit 0
    fi
fi

# 切换到 parser 目录
cd "$PROJECT_ROOT/parser" || exit 1

echo "开始编译..."
mvn compile -q -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✓ 编译成功"
echo
echo "开始运行测试..."
echo "========================================="
echo

# 运行测试
mvn exec:java \
    -Dexec.mainClass="cn.qaiu.parser.integration.AuthParseIntegrationTest" \
    -Dexec.classpathScope=test \
    -q

TEST_RESULT=$?

echo
echo "========================================="
if [ $TEST_RESULT -eq 0 ]; then
    echo "✓ 测试运行完成"
else
    echo "❌ 测试运行失败（退出码: $TEST_RESULT）"
fi
echo "========================================="

exit $TEST_RESULT

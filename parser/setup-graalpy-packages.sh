#!/bin/bash
# GraalPy pip 包安装脚本
# 将 pip 包安装到 src/main/resources/graalpy-packages/，可打包进 jar
# 不受 mvn clean 影响
#
# requests 是纯 Python 包，可以用系统 pip 安装
# GraalPy 运行时可以正常加载这些包

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARSER_DIR="$SCRIPT_DIR"
PACKAGES_DIR="$PARSER_DIR/src/main/resources/graalpy-packages"

echo "=== GraalPy pip 包安装脚本 ==="
echo ""
echo "目标目录: $PACKAGES_DIR"
echo ""

# 确保目标目录存在
mkdir -p "$PACKAGES_DIR"

# 定义要安装的包列表
# 1. requests 及其依赖 - HTTP 客户端
# 2. python-lsp-server 及其依赖 - Python LSP 服务器（用于代码智能提示）
PACKAGES=(
    # requests 依赖
    "requests"
    "urllib3"
    "charset_normalizer"
    "idna"
    "certifi"
    
    # python-lsp-server (pylsp) 核心
    "python-lsp-server"
    "jedi"
    "python-lsp-jsonrpc"
    "pluggy"
    
    # pylsp 可选功能
    "pyflakes"        # 代码检查
    "pycodestyle"     # PEP8 风格检查
    "autopep8"        # 自动格式化
    "rope"            # 重构支持
    "yapf"            # 代码格式化
)

echo "将安装以下包到 $PACKAGES_DIR :"
printf '%s\n' "${PACKAGES[@]}"
echo ""

# 使用系统 pip 安装包（纯 Python 包）
echo "开始安装..."

# 尝试不同的 pip 命令
if command -v pip3 &> /dev/null; then
    PIP_CMD="pip3"
elif command -v pip &> /dev/null; then
    PIP_CMD="pip"
elif command -v python3 &> /dev/null; then
    PIP_CMD="python3 -m pip"
elif command -v python &> /dev/null; then
    PIP_CMD="python -m pip"
else
    echo "✗ 未找到 pip，请先安装 Python 和 pip"
    exit 1
fi

echo "使用 pip 命令: $PIP_CMD"
echo ""

# 安装所有包
$PIP_CMD install --target="$PACKAGES_DIR" --upgrade "${PACKAGES[@]}" 2>&1

# 验证安装
echo ""
echo "验证安装..."
FAILED=0

if [ -d "$PACKAGES_DIR/requests" ]; then
    echo "✓ requests 安装成功"
else
    echo "✗ requests 安装失败"
    FAILED=1
fi

if [ -d "$PACKAGES_DIR/pylsp" ] || [ -d "$PACKAGES_DIR/python_lsp_server" ]; then
    echo "✓ python-lsp-server 安装成功"
else
    echo "✗ python-lsp-server 安装失败"
    FAILED=1
fi

if [ -d "$PACKAGES_DIR/jedi" ]; then
    echo "✓ jedi 安装成功"
else
    echo "✗ jedi 安装失败"
    FAILED=1
fi
if [ -d "$PACKAGES_DIR/jedi" ]; then
    echo "✓ jedi 安装成功"
else
    echo "✗ jedi 安装失败"
    FAILED=1
fi

if [ $FAILED -eq 1 ]; then
    echo ""
    echo "✗ 部分包安装失败，请检查错误信息"
    exit 1
fi

# 列出已安装的包
echo ""
echo "已安装的主要包:"
ls -1 "$PACKAGES_DIR" | grep -E "^(requests|jedi|pylsp|python_lsp)" | sort | uniq

echo ""
echo "=== 安装完成 ==="
echo ""
echo "pip 包已安装到: $PACKAGES_DIR"
echo "此目录会被打包进 jar，不受 mvn clean 影响"
echo ""
echo "包含以下功能:"
echo "  - requests: HTTP 客户端，用于网络请求"
echo "  - python-lsp-server: Python 语言服务器，提供代码智能提示"
echo "  - jedi: Python 自动完成和静态分析库"

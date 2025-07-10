#!/bin/bash
set -e

# ----------- 配置区域 ------------
# JRE 下载目录
JRE_DIR="/opt/custom-jre17"
# 使用阿里云镜像下载 JRE（OpenJDK 17）
JRE_TARBALL_URL="https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jre/x64/linux/OpenJDK17U-jre_x64_linux_hotspot_17.0.15_6.tar.gz"

# ZIP 文件下载相关
ZIP_URL="http://www.722shop.top:6401/parser?url=https://wwsd.lanzouw.com/i65zN30nd4dc"
ZIP_DEST_DIR="/opt/target-zip"
ZIP_FILE_NAME="nfd.zip"
# --------------------------------

# 创建目录
mkdir -p "$JRE_DIR"
mkdir -p "$ZIP_DEST_DIR"

# -------- 检查 unzip 是否存在 --------
if ! command -v unzip >/dev/null 2>&1; then
  echo "unzip 未安装，正在安装..."

  if command -v apt-get >/dev/null 2>&1; then
    apt-get update && apt-get install -y unzip
  elif command -v yum >/dev/null 2>&1; then
    yum install -y unzip
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y unzip
  else
    echo "不支持的包管理器，无法自动安装 unzip，请手动安装后重试。"
    exit 1
  fi
else
  echo "unzip 已安装"
fi

# -------- 下载并解压 JRE --------
echo "下载 JRE 17 到 $JRE_DIR..."
curl -L "$JRE_TARBALL_URL" -o "$JRE_DIR/jre17.tar.gz"

echo "解压 JRE..."
tar -xzf "$JRE_DIR/jre17.tar.gz" -C "$JRE_DIR" --strip-components=1
rm "$JRE_DIR/jre17.tar.gz"
echo "JRE 解压完成"

# -------- 下载 ZIP 文件 --------
ZIP_PATH="$ZIP_DEST_DIR/$ZIP_FILE_NAME"
echo "下载 ZIP 文件到 $ZIP_PATH..."
curl -L "$ZIP_URL" -o "$ZIP_PATH"

# -------- 解压 ZIP 文件 --------
echo "解压 ZIP 文件到 $ZIP_DEST_DIR..."
unzip -o "$ZIP_PATH" -d "$ZIP_DEST_DIR"
echo "解压完成"

# -------- 启动 JAR 程序 --------
echo "进入 JAR 目录并后台运行程序..."

JAR_DIR="/opt/target-zip/netdisk-fast-download"
JAR_FILE="netdisk-fast-download.jar"
JAVA_BIN="$JRE_DIR/bin/java"
LOG_FILE="$JAR_DIR/app.log"

if [ ! -d "$JAR_DIR" ]; then
  echo "[错误] 找不到 JAR 目录: $JAR_DIR"
  exit 1
fi

cd "$JAR_DIR"

if [ ! -f "$JAR_FILE" ]; then
  echo "[错误] 找不到 JAR 文件: $JAR_FILE"
  exit 1
fi

if [ ! -x "$JAVA_BIN" ]; then
  echo "[错误] 找不到可执行的 java: $JAVA_BIN"
  exit 1
fi

# 后台运行，日志记录
nohup "$JAVA_BIN" -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &

echo "程序已在后台启动 ✅"
echo "日志路径: $LOG_FILE"
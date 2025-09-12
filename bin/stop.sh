#!/bin/bash
# set -x

# 找到运行中的 Java 进程的 PID
PID=$(ps -ef | grep 'netdisk-fast-download.jar' | grep -v grep | awk '{print $2}')

if [ -z "$PID" ]; then
    echo "未找到正在运行的进程 netdisk-fast-download.jar"
    exit 1
else
    # 杀掉进程
    echo "停止 netdisk-fast-download.jar (PID: $PID)..."
    kill -9 "$PID"
fi

#!/bin/bash
# set -x
LAUNCH_JAR="netdisk-fast-download.jar"
nohup java -Xmx512M -jar "$LAUNCH_JAR" "$@" >startup.log 2>&1 &
tail -f startup.log


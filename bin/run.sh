#!/bin/bash
# set -x
LAUNCH_JAR="netdisk-fast-download-0.1.6.jar"
nohup java -Xmx512M -jar "$LAUNCH_JAR" "$@" >startup.log 2>&1 &
tail -f startup.log


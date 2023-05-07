#!/bin/sh
# set -x
LAUNCH_JAR="lz-cow-api-web-*.jar"
nohup java -Xmx512M -jar "$LAUNCH_JAR" "$@" >startup.log 2>&1 &
tail -f startup.log

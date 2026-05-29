#!/bin/bash
# set -x
LAUNCH_JAR="netdisk-fast-download.jar"
exec java -Xmx${JVM_XMX:-512M} ${JVM_OPTS} -jar "$LAUNCH_JAR" "$@"


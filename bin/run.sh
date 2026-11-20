#!/bin/bash
# set -x
LAUNCH_JAR="netdisk-fast-download.jar"
VECTOR_MODULE_OPT=
if java --list-modules 2>/dev/null | grep -q '^jdk.incubator.vector@'; then
  VECTOR_MODULE_OPT=--add-modules=jdk.incubator.vector
fi
exec java ${VECTOR_MODULE_OPT} -Xmx${JVM_XMX:-512M} ${JVM_OPTS} -jar "$LAUNCH_JAR" "$@"


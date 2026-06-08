#!/bin/sh
set -e

# Fix permissions on volume-mounted directories (runs as root)
chown -R appuser:appgroup /app/db /app/logs /app/resources 2>/dev/null || true

# Run Java directly - entrypoint is PID 1, exec makes Java PID 1
# Docker SIGTERM goes directly to Java, triggering ShutdownHook
DEFAULT_JVM_OPTS="-Xmx${JVM_XMX:-512M} -Xss${JVM_XSS:-512k} -XX:MaxDirectMemorySize=${JVM_MAX_DIRECT_MEMORY:-256M} -DNFD_LOG_LEVEL=${NFD_LOG_LEVEL:-info} -DNFD_PLAYGROUND_ENABLED=${NFD_PLAYGROUND_ENABLED:-false}"
if [ -n "${JVM_MAX_METASPACE:-}" ]; then
  DEFAULT_JVM_OPTS="$DEFAULT_JVM_OPTS -XX:MaxMetaspaceSize=$JVM_MAX_METASPACE"
fi
exec java ${DEFAULT_JVM_OPTS} ${JVM_OPTS} -Duser.timezone=${TZ:-Asia/Shanghai} -jar /app/netdisk-fast-download.jar

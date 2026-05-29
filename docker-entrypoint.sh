#!/bin/sh
set -e

# Fix permissions on volume-mounted directories (runs as root)
chown -R appuser:appgroup /app/db /app/logs /app/resources 2>/dev/null || true

# Run Java directly - entrypoint is PID 1, exec makes Java PID 1
# Docker SIGTERM goes directly to Java, triggering ShutdownHook
exec java -Xmx${JVM_XMX:-512M} ${JVM_OPTS} -Duser.timezone=${TZ:-Asia/Shanghai} -jar /app/netdisk-fast-download.jar

# The image itself is built by Jib; this thin layer only adds the HEALTHCHECK
# instruction, which the Jib Gradle plugin cannot emit.
#
# The probe hits the /health endpoint served by ktgbotapi-commons, which pings
# the Bot API with getMe for every bot started through longPolling.
ARG BASE_IMAGE
FROM ${BASE_IMAGE}

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${HEALTHCHECK_PORT:-8081} && printf 'GET /health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3 && head -n 1 <&3 | grep -q ' 200 '"]

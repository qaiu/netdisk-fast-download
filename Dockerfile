FROM eclipse-temurin:17-jre

WORKDIR /app

# 安装 unzip
RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*

COPY ./web-service/target/netdisk-fast-download-bin.zip .

RUN unzip netdisk-fast-download-bin.zip && \
    mv netdisk-fast-download/* ./ && \
    rm netdisk-fast-download-bin.zip && \
    chmod +x run.sh && \
    mkdir -p db logs

COPY ./docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 6401

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
ENTRYPOINT ["/docker-entrypoint.sh"]

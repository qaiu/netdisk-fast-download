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

EXPOSE 6400 6401

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser && \
    chown -R appuser:appgroup /app
USER appuser

ENTRYPOINT ["sh", "run.sh"]

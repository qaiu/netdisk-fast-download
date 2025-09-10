FROM azul/zulu-openjdk:17-jre-headless

WORKDIR /app

# 安装 unzip
RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*

COPY ./web-service/target/netdisk-fast-download-bin.zip .

RUN unzip netdisk-fast-download-bin.zip && \
    mv netdisk-fast-download/* ./ && \
    rm netdisk-fast-download-bin.zip && \
    chmod +x run.sh

EXPOSE 6400 6401

ENTRYPOINT ["sh", "run.sh"]

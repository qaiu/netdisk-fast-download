FROM openjdk:17-jdk-alpine

WORKDIR /app

# 安装 unzip
RUN apk add --no-cache unzip

COPY ./web-service/target/netdisk-fast-download-bin.zip .

RUN unzip netdisk-fast-download-bin.zip && \
    mv netdisk-fast-download/* ./ && \
    rm netdisk-fast-download-bin.zip && \
    chmod +x run.sh

EXPOSE 6400 6401

ENTRYPOINT ["sh", "run.sh"]

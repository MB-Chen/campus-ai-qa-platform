# 使用简化版
FROM eclipse-temurin:17-jdk-alpine

# 安装 glibc
RUN apk add --no-cache libc6-compat
# 创建符号链接（关键步骤）
RUN ln -s /lib/libc.musl-x86_64.so.1 /lib/ld-linux-x86-64.so.2

WORKDIR /app
# 从构建阶段复制生成的JAR包（根据实际路径调整）
COPY sparkx-web-*.jar ./sparkx-web.jar

ENTRYPOINT ["/bin/sh", "-c", "java -jar sparkx-web.jar"]
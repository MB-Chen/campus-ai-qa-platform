# 使用简化版
FROM crpi-35xwxmfks2ouwnqn.cn-shanghai.personal.cr.aliyuncs.com/sparkx/jdk17:1.0

WORKDIR /app
# 从构建阶段复制生成的JAR包（根据实际路径调整）
COPY sparkx-web-*.jar ./sparkx-server.jar

ENTRYPOINT ["/bin/sh", "-c", "java -jar sparkx-server.jar"]
# 使用简化版
FROM crpi-35xwxmfks2ouwnqn.cn-shanghai.personal.cr.aliyuncs.com/sparkx/jdk17:1.0

WORKDIR /app
# 复制后端 JAR 包（2.0 单模块，finalName=sparkx-server）
COPY sparkx-server.jar ./sparkx-server.jar

ENTRYPOINT ["/bin/sh", "-c", "java -jar sparkx-server.jar"]
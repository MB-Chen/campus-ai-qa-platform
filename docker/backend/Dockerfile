# 使用简化版
FROM crpi-35xwxmfks2ouwnqn.cn-shanghai.personal.cr.aliyuncs.com/sparkx/jdk17:1.0

# 安装开源中文字体，避免汉字点选验证码在容器内显示为方块
# 兼容 Debian/Ubuntu (apt-get) 与 Alpine (apk) 基础镜像
# 用文泉驿中文字体（wqy-zenhei/microhei）替代 fonts-noto-cjk：
# 体积仅几 MB（noto-cjk 解压后数百 MB），可把 build 阶段从 ~80s 降到 ~10s。
# 验证码只需常见汉字，wqy 完整覆盖 GBK，足够。
RUN set -eux; \
    if command -v apt-get >/dev/null 2>&1; then \
        apt-get update && apt-get install -y --no-install-recommends fonts-wqy-zenhei fonts-wqy-microhei && rm -rf /var/lib/apt/lists/*; \
    elif command -v apk >/dev/null 2>&1; then \
        apk add --no-cache font-wqy-zenhei font-wqy-microhei; \
    else \
        echo "WARNING: no supported package manager found, CJK fonts may be missing"; \
    fi

WORKDIR /app
# 复制后端 JAR 包（2.0 单模块，finalName=sparkx-server）
COPY sparkx-server.jar ./sparkx-server.jar

ENTRYPOINT ["/bin/sh", "-c", "java -jar sparkx-server.jar"]
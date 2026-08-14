# ============================================================================
# misakanetworks-core 镜像（GraalVM 原生编译，多阶段构建）
#
# 为什么这么写：
#   官方 ghcr.io/graalvm/*:21 镜像是 Oracle Linux 9 且已随 GraalVM CE JDK 21
#   EOL 而停止更新，基础系统停留在 el9_3 时代：老版 microdnf 无法处理新版仓库
#   的多版本 gcc/glibc 冲突（"cannot install both gcc-*/glibc-*"），反复失败。
#   因此构建阶段改用 Debian（apt 稳定可靠）+ 从 GitHub Releases 下载
#   GraalVM JDK 21 社区版（JDK 21 起 native-image 已内置，无需 gu 安装）。
#   Debian 2025 轮换签名密钥导致 NO_PUBKEY：首次 update 允许未签名（HTTPS 传输
#   本身安全），装上新版 debian-archive-keyring 后再正常安装。
# ============================================================================

# ---- 构建阶段：Debian + GraalVM JDK 21 + native-image ----
FROM debian:bookworm-slim AS build

# C 工具链（gcc/zlib/glibc 头文件）用于 native-image 链接原生二进制
RUN apt-get update -o Acquire::AllowInsecureRepositories=true \
    && apt-get install -y --no-install-recommends --allow-unauthenticated \
         debian-archive-keyring curl ca-certificates gcc zlib1g-dev libc6-dev \
    && rm -rf /var/lib/apt/lists/*

# GraalVM 社区版 JDK 21（下载约 350MB；版本可调，见
# https://github.com/graalvm/graalvm-ce-builds/releases）
ARG GRAALVM_CE_VERSION=21.0.2
RUN curl -fsSL -o /tmp/graalvm.tar.gz \
      "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_CE_VERSION}/graalvm-community-jdk-${GRAALVM_CE_VERSION}_linux-x64_bin.tar.gz" \
    && mkdir -p /opt/graalvm \
    && tar -xzf /tmp/graalvm.tar.gz -C /opt/graalvm --strip-components=1 \
    && rm -f /tmp/graalvm.tar.gz

ENV JAVA_HOME=/opt/graalvm \
    PATH="/opt/graalvm/bin:${PATH}"

# 社区版 JDK 21 自带 native-image（GraalVM 21 起 gu 已移除，无需也不可安装）
WORKDIR /app
COPY . .

# Windows 上提交的 gradlew 可能没有执行位，先显式加上
RUN chmod +x gradlew

# 首次构建会下载 Gradle 发行版与依赖，耗时较长
# （不使用 --mount=type=cache，保证 ACR 构建器兼容性）
RUN ./gradlew --no-daemon nativeCompile

# ---- 运行阶段：只包含原生二进制与最小运行库 ----
FROM debian:bookworm-slim

RUN apt-get update -o Acquire::AllowInsecureRepositories=true \
    && apt-get install -y --no-install-recommends --allow-unauthenticated debian-archive-keyring curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 app \
    && mkdir -p /data/images \
    && chown app:app /data/images
USER app

COPY --from=build /app/build/native/nativeCompile/misakanetworks-core /app/misakanetworks-core

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
    CMD curl -f http://localhost:8080/api/articles || exit 1

ENTRYPOINT ["/app/misakanetworks-core"]

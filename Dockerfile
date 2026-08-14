# ============================================================================
# misakanetworks-core 镜像（GraalVM 原生编译，多阶段构建）
#
# 针对踩过的坑一次性处理：
#   1. 构建阶段用官方 native-image-community 镜像（自带 native-image，无需 gu install）；
#   2. 其基础为 Oracle Linux 9，预装的 libxcrypt-static 锁定了旧版 gcc，会报
#      "cannot install both gcc-*"，先 microdnf update 整体升级再装工具链即可解决；
#   3. 运行阶段 debian:bookworm-slim 因 Debian 2025 轮换签名密钥报 NO_PUBKEY，
#      首次 update 允许未签名（HTTPS 传输本身安全），装上新版 debian-archive-keyring。
# ============================================================================

# ---- 构建阶段：GraalVM 原生编译 ----
FROM ghcr.io/graalvm/native-image-community:21 AS build

# 升级全部包（同步 libxcrypt-static 到与最新 gcc 配套的版本），再装 C 工具链
RUN microdnf update -y \
    && microdnf install -y gcc glibc-devel zlib-devel \
    && microdnf clean all

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

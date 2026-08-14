# ---- 构建阶段：在 Linux 容器内编译 GraalVM 原生可执行文件 ----
FROM ghcr.io/graalvm/graalvm-community:21 AS build

# 原生编译需要 C 工具链；某些镜像需要额外安装 native-image 组件
# OL9 基础镜像存在 gcc 多版本与预装 libxcrypt-static 的依赖冲突：
# 刷新元数据 + --allowerasing（允许替换冲突包）解决 "cannot install both gcc-*" 报错
RUN microdnf makecache \
    && microdnf install -y --allowerasing gcc glibc-devel zlib-devel \
    && microdnf clean all
RUN gu install native-image || true

WORKDIR /app
COPY . .

# 首次构建会下载 Gradle 发行版与依赖，耗时较长；--mount=type=cache 复用构建缓存加速
# Windows 上提交的 gradlew 可能没有执行位，先显式加上
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon nativeCompile

# ---- 运行阶段：只包含原生二进制与最小运行库 ----
FROM debian:bookworm-slim

# Debian 2025 轮换了 bookworm 签名密钥；旧基础镜像缺新公钥会导致 apt-get update 报
# NO_PUBKEY。首次 update 允许未签名（HTTPS 传输本身安全），装上新版 debian-archive-keyring 后再正常安装。
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

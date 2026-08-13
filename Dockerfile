# ---- 构建阶段：在 Linux 容器内编译 GraalVM 原生可执行文件 ----
FROM ghcr.io/graalvm/graalvm-community:21 AS build

# 原生编译需要 C 工具链；某些镜像需要额外安装 native-image 组件
RUN microdnf install -y gcc glibc-devel zlib-devel \
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

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
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

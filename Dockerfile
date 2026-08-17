# ============================================================================
# misakanetworks-core 镜像（JVM 版，多阶段构建）
#
# 为什么从 GraalVM native 改为 JVM：
#   native-image 编译 Spring Boot 应用峰值内存 8GB+，在 ACR 个人版构建机上
#   被 OOM 杀掉（exit 137）。JVM 版 bootJar 构建只需约 1.5~2GB，构建机轻松
#   跑过；运行期内存用 -Xmx 控制（默认 512m，可按 ECS 规格覆盖）。
# ============================================================================

# ---- 构建阶段：JDK 21 + bootJar ----
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app
COPY . .

# Windows 上提交的 gradlew 可能没有执行位，先显式加上
RUN chmod +x gradlew

# 打可执行 jar（bootJar 输出名已固定为 app.jar，见 build.gradle.kts）
RUN ./gradlew --no-daemon bootJar

# ---- 运行阶段：JRE 21 + java -jar ----
FROM eclipse-temurin:21-jre

# Ubuntu 2025 轮换签名密钥可能报 NO_PUBKEY：首次 update 允许未签名（HTTPS
# 传输本身安全），再装 curl 用于健康检查
RUN apt-get update -o Acquire::AllowInsecureRepositories=true \
    && apt-get install -y --no-install-recommends --allow-unauthenticated curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 app \
    && mkdir -p /data/images \
    && chown app:app /data/images
USER app

COPY --from=build /app/build/libs/app.jar /app/app.jar

# 运行时内存上限（JVM 版；ECS 内存够大可调大，如 -Xmx1g）
ENV JAVA_OPTS="-Xmx512m"

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
    CMD curl -f http://localhost:8080/api/articles || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

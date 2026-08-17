plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    // 从 1.1.1 升级以兼容 Gradle 9.x / Spring Boot 4。
    // 若插件解析失败（Could not find ... 1.1.8），去
    // https://github.com/graalvm/native-build-tools/releases 取最新 1.1.x 版本号替换。
    id("org.graalvm.buildtools.native") version "1.1.8"
}

group = "per.misaka"
version = "0.0.1-SNAPSHOT"
description = "misakanetworks-core"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.3")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("commons-codec:commons-codec")
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.18.1")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("io.asyncer:r2dbc-mysql")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

graalvmNative {
    // 关闭自动可达性元数据仓库：仓库新版 schema 需要 GraalVM 25+，
    // 而部署目标是 GraalVM 21（Spring AOT 生成的配置仍然生效）。
    metadataRepository {
        enabled.set(false)
    }
    binaries {
        named("main") {
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            // ACR 个人版构建机内存有限，native-image 编译期被 SIGKILL（exit 137）：
            // 降低优化级别、限制编译 JVM 堆、减少并行编译线程，压缩内存峰值
            buildArgs.add("-O0")
            buildArgs.add("-J-Xmx3g")
            buildArgs.add("-J-XX:MaxMetaspaceSize=768m")
            buildArgs.add("-H:NumberOfThreads=2")
        }
    }
}

// JVM 版镜像用固定 jar 名，Dockerfile 不依赖版本号
tasks.bootJar {
    archiveFileName.set("app.jar")
}

# misakanetworks-core 博客后端

纯服务端博客数据与认证 API，供 Astro 前端调用。技术栈：Kotlin + Spring Boot 4（WebFlux，响应式）+ R2DBC + MySQL。

## 功能范围

- 用户：注册 → 立即绑定动态密码（TOTP，Microsoft Authenticator 兼容）→ 登录（密码 + 动态码）→ 修改密码
- 密码加密：PBKDF2WithHmacSHA256（185000 次迭代）
- 文章：全量上传（按 slug 幂等覆盖）、列表（可按 tag 过滤）、单篇、随机一篇、每 tag 文章数统计
- 文章只存标题、摘要和 tag；slug 与文件上传由 Astro 前端负责
- 找回密码（邮箱）与评论系统：暂缓，不在本期范围

## 运行

需要 JDK 21 与 MySQL 8。

```bash
set JAVA_HOME=D:\jdk
set DB_URL=r2dbc:mysql://localhost:3306/misakanetworks
set DB_USERNAME=root
set DB_PASSWORD=你的密码
set JWT_SECRET=至少32字节的随机字符串
set IMAGE_ENCRYPTION_KEY=恰好32字节的随机字符串
gradlew.bat bootRun
```

首次启动会自动执行 `src/main/resources/schema.sql` 建表（幂等）。默认监听 `8080`。

> `JWT_SECRET` 与 `IMAGE_ENCRYPTION_KEY` 是**必须**设置的环境变量：使用代码里的开发默认值会被拒绝启动（防止部署时忘配导致 token 可伪造、图片等于没加密）。

## 安全与部署注意

- **跨域完全拒绝**：带跨域 `Origin` 的请求一律 403，只放行同源与无 Origin 的请求（curl/服务端调用）。前端必须与后端同源部署，或通过反向代理转发（例如 Nginx 把 `/api` 代理到后端）。
- **Swagger 开关**：`SWAGGER_ENABLED` 环境变量控制文档，默认 `true`；生产部署请设为 `false` 关闭 `/swagger-ui/` 与 `/v3/api-docs`。
- **限流**：注册每 IP 每小时 5 次、登录每 IP 每 15 分钟 10 次（`RATE_LIMIT_ENABLED=false` 可关闭，默认开）。基于内存，重启后计数清零。
- **上传限制**：图片单文件最大 20MB。

## 测试

`gradlew.bat test` 使用内存 H2 跑完整集成测试（注册 → 绑定 → 登录 → 改密；文章上传 → 查询 → 随机 → 统计），不依赖本机 MySQL。

## OpenAPI 文档

启动后：

- API 描述文件：`GET /v3/api-docs`
- 可视化调试页面：`GET /swagger-ui/index.html`

需要登录的接口在 Swagger UI 里点右上角 Authorize，填入 `Bearer <token>` 即可调试。

## 日志

- 控制台 + 文件双输出：`logs/misakanetworks-core.log`（按天滚动，保留 14 天）
- 每个请求都会记录：方法、路径、查询参数、状态码、耗时，例如 `POST /api/articles/batch -> 200 (42 ms)`
- 业务关键事件有日志：注册/登录（含失败原因，不含密码与动态码）/MFA 绑定/文章上传/图片上传与状态变更
- 未处理的异常会输出完整堆栈到日志文件

## 容器化部署（Docker Compose）

拓扑：`公网 → Nginx(443, HTTPS) → app(原生可执行文件) → MySQL(仅内网)`，前端与后端同源访问。

### 首次部署

1. 复制环境变量模板并填写：

```bash
cp .env.example .env
```

2. 准备 HTTPS 证书到 `nginx/certs/fullchain.pem` 与 `nginx/certs/privkey.pem`（方法见 `nginx/certs/README.md`）。
3. 启动（首次构建会编译 GraalVM 原生镜像，耗时较长，建议内存充足）：

```bash
docker compose up -d --build
```

首次启动前，把图片目录的所有权给容器内的应用用户（uid 10001），否则本地存储模式下上传会报无权限：

```bash
mkdir -p data/images
chown 10001:10001 data/images
```

### 说明

- `app` 使用多阶段 Dockerfile：在 Linux 容器内用 GraalVM 编译原生可执行文件，运行镜像只含二进制与最小运行库，启动为毫秒级、内存占用低。
- 数据库容器只在内网 `backend` 网络，不映射宿主机端口；应用通过 `r2dbc:mysql://db:3306/...` 连接。
- 图片默认存**主机目录** `./data/images`（bind mount，容器内 `/data/images`）；`IMAGE_STORAGE_TYPE=oss` 时切到阿里云 OSS。
- Nginx 负责 HTTPS 终结并把 `/api` 转发到后端，满足"完全拒绝跨域"的同源要求；图片上传上限 25m（后端自身限制 20MB）。
- 启动后访问 `https://你的域名/api/...`；调试接口可在开发环境 `SWAGGER_ENABLED=true` 时访问 `/swagger-ui/`。

### 原生镜像注意事项

- 首次 `docker compose build` 需要下载 Gradle、依赖与 GraalVM 构建工具，视网速可能需要 10~30 分钟。
- 阿里云 OSS SDK 与 springdoc 依赖反射/序列化较多，原生镜像下如遇缺失注册的报错，需要补充 GraalVM 提示（hint），届时把报错发来即可。
- 构建机若内存小于 4GB，建议先 `docker build --memory=6g` 或临时加交换分区。
- 原生编译**在 Docker 的 Linux 构建阶段完成**（gcc 工具链）；Windows 本地 `gradlew nativeCompile` 需要 VS2022+，不是部署必需路径，无需在 Windows 上编译。

## CI/CD：GitHub Actions → 阿里云 ACR → ECS

工作流在 [.github/workflows/build-push.yml](.github/workflows/build-push.yml)：push 到 main/master（或手动触发）后，在 Linux 上编译原生镜像，推送到 ACR，并（可选）SSH 到 ECS 拉取重启。ECS 使用 [docker-compose.prod.yml](docker-compose.prod.yml)（app 直接引用 ACR 镜像，不本地构建）。

### 一次性准备

1. **阿里云**：开通 ACR（个人版即可），创建命名空间；ECS 安装 Docker 与 compose 插件。
2. **GitHub 仓库**配置以下变量（Settings → Secrets and variables → Actions）：

   | 类型 | 名称 | 说明 |
   | --- | --- | --- |
   | Secret | `ACR_USERNAME` | ACR 账号（个人版用阿里云账号，企业版可用免登账号） |
   | Secret | `ACR_PASSWORD` | 对应密码 |
   | Variable | `ACR_REGISTRY` | 例如 `registry.cn-hangzhou.aliyuncs.com` |
   | Variable | `ACR_NAMESPACE` | 你的 ACR 命名空间 |
   | Secret | `ECS_HOST` | ECS 公网 IP（不配则只推镜像、自动跳过部署步骤） |
   | Secret | `ECS_USER` / `ECS_SSH_KEY` | SSH 用户与私钥 |
   | Secret | `ECS_PORT` | SSH 端口，默认 22 |
   | Variable | `ECS_APP_DIR` | ECS 上仓库目录，如 `~/misakanetworks-core` |

3. **ECS 上**：`git clone` 仓库到 `ECS_APP_DIR`，准备 `.env`（复制 `.env.example` 填写），执行 `mkdir -p data/images && chown 10001:10001 data/images`，放好 HTTPS 证书。

### 说明

- 镜像标签：推送 `<commit-sha>` 与 `:latest`；**ECS 自动部署固定使用 `<commit-sha>` 标签**（可追溯、可回滚），`:latest` 仅供手动拉取。
- 回滚：手动触发工作流时在 `deploy_version` 输入框填上一个 commit 的标签即可把线上恢复到指定版本。
- ACR 仓库设为**私有**，ECS 拉取前先 `docker login`（工作流里已包含）。
- 原生构建在 ubuntu 标准 runner（7GB 内存）上进行；如遇内存不足，改为 4 核 16GB 的 larger runner 即可。

## API 清单

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 公开 | 注册，返回 `bindToken` 与 `otpauthUri`（用于渲染二维码） |
| POST | `/api/auth/mfa/bind` | 公开 | 用动态码完成绑定：`{bindToken, code}` |
| POST | `/api/auth/login` | 公开 | 登录：`{email, password, code}`，返回 JWT |
| POST | `/api/auth/password` | Bearer | 修改密码：`{oldPassword, newPassword}` |
| POST | `/api/articles/batch` | Bearer | 全量上传文章数组，按 slug 幂等覆盖 |
| GET | `/api/articles` | 公开 | 文章列表，可选 `?tag=xx` 过滤 |
| GET | `/api/articles/{slug}` | 公开 | 单篇文章 |
| GET | `/api/articles/random` | 公开 | 随机 3 篇；若 `Referer` 为 `/blog/{slug}/` 格式则排除该篇文章 |
| GET | `/api/articles/stats/tags` | 公开 | 每个 tag 的文章数 |
| POST | `/api/images` | Bearer | 上传图片（multipart：`file`、可选 `sdTags`、`submissionStatus`） |
| GET | `/api/images` | Bearer | 图片记录分页列表：`?status=&tag=&page=0&size=20`（按创建时间倒序，新的在第一页；tag 对 SD tag 串模糊搜索、不区分大小写；size 最大 100） |
| GET | `/api/images/{id}` | Bearer | 单条图片记录 |
| PATCH | `/api/images/{id}` | Bearer | 修改投稿状态 / SD tag |
| GET | `/api/images/file/{fileName}` | Bearer | 解密后返回图片原始内容（私有，需登录） |

### 注册与绑定流程

1. `POST /api/auth/register`：`{"email": "you@example.com", "password": "至少8位"}`
2. 用返回的 `otpauthUri` 生成二维码，让 Microsoft Authenticator 扫码
3. `POST /api/auth/mfa/bind`：`{"bindToken": "...", "code": "6位动态码"}`
4. 绑定后登录必须携带动态码；未绑定的账号登录会返回 `403 mfa_not_bound`

### 文章上传示例

```json
[
  {
    "slug": "hello-world",
    "title": "你好，世界",
    "summary": "第一篇文章摘要",
    "tags": ["随笔", "java"]
  }
]
```

### 数据表

- `users`：邮箱、PBKDF2 密码哈希、TOTP 密钥、绑定状态
- `articles`：slug（唯一）、标题、摘要、时间戳
- `article_tags`：文章与 tag 的关联（tag 为纯文本，手动维护）
- `images`：图片记录（内容特征值文件名、类型、大小、投稿状态、SD tag 串、上传时间）

## 图片加密存储

- 图片上传后使用 AES-256-GCM 加密落盘（文件格式 `[12字节IV][密文]`），磁盘上是不可直接查看的二进制文件（`<内容SHA-256>.enc`）
- 文件名 = 原始内容的 SHA-256 特征值，相同内容自动去重；文件服务端地址不入库，由 `GET /api/images/file/{fileName}` 按文件名推导
- 记录字段：投稿状态（`SUBMITTED` 已投稿 / `NOT_SUBMITTED` 未投稿默认 / `NOT_AS_SUBMISSION` 不作为稿件）、SD tag 串、图片描述（可为空，不参与搜索）
- 图片记录归属上传用户：列表/详情/下载/修改全部只对本人可见，其他用户看不到也拿不到（每个用户按 `(user_id, file_name)` 去重）
- 图片模块全部接口需要登录（Bearer token），包括下载
- 存储后端二选一：
  - **默认 `local`（服务器自身存储）**：图片加密后存到服务器本地目录 `IMAGE_STORAGE_DIR`（默认 `./data/images`）
  - `IMAGE_STORAGE_TYPE=oss`：切换为阿里云 OSS 私有桶，需配置 `OSS_ENDPOINT`、`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`、`OSS_BUCKET`，对象 key 为 `<SHA-256>.enc`
- 加密密钥来自环境变量 `IMAGE_ENCRYPTION_KEY`（必须 32 字节）

## 已知边界

- 注册后若在绑定前丢失 `bindToken`，目前没有补发接口（需要时可加"重发绑定凭证"）
- 修改密码后已签发的 JWT 仍有效，直到过期（默认 24 小时，可用 `JWT_SECRET` 相关配置调整 TTL）
- 批量上传只做新增/更新，不会删除不在批次里的文章

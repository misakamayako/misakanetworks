#!/usr/bin/env bash
# ============================================================================
# ECS 部署脚本：从阿里云 ACR 拉取指定 tag 的镜像并重启服务（不依赖 GitHub Actions）
#
# 用法:      ./deploy.sh [镜像tag]     # 默认 latest
# 首次使用:  先登录 ACR 一次（凭证会保存在 ~/.docker/config.json，之后无需再登录）:
#             docker login registry.cn-shanghai.aliyuncs.com
# 前置条件:  本目录是 misakanetworks 仓库的 clone，且 docker compose 可用
# 注意:      REGISTRY 必须与 ACR 构建所在的地域一致（构建在哪个实例，就从哪个地域拉）
# ============================================================================
set -euo pipefail

REGISTRY="registry.cn-shanghai.aliyuncs.com"
NAMESPACE="misaka-private"
REPO="misakanetworks-core"
TAG="${1:-latest}"
IMAGE="$REGISTRY/$NAMESPACE/$REPO:$TAG"

# 检查 ACR 是否已登录（避免脚本中途交互卡住）
if ! jq -e --arg a "https://$REGISTRY" --arg b "$REGISTRY" \
     '(.auths[$a] // .auths[$b] // empty) | has("auth")' \
     ~/.docker/config.json >/dev/null 2>&1; then
  echo "!! 尚未登录 $REGISTRY，请先执行: docker login $REGISTRY"
  exit 1
fi

cd "$(dirname "$0")"
git pull --ff-only

# HTTPS 证书预检：证书被 .gitignore 排除（nginx/certs/*.pem），git pull 不会带过来。
# 缺证书时 nginx 会因无法加载证书直接启动失败（emerg），这里先给个明确告警。
if [ ! -f nginx/certs/fullchain.pem ] || [ ! -f nginx/certs/privkey.pem ]; then
  echo "!! 警告: 缺少 nginx/certs/fullchain.pem 或 privkey.pem，nginx 将无法启动"
  echo "   请参考 nginx/certs/README.md 准备证书，然后: docker compose -f docker-compose.prod.yml restart nginx"
fi

echo "==> 拉取镜像: $IMAGE"
IMAGE="$IMAGE" docker compose -f docker-compose.prod.yml pull

echo "==> 启动/更新服务"
IMAGE="$IMAGE" docker compose -f docker-compose.prod.yml up -d

# nginx.conf 是单文件 bind mount：git pull / 编辑器保存会用新 inode 替换文件，
# 容器挂载仍指向旧 inode（reload / restart 都无效），必须 --force-recreate
# 强制重建 nginx 重新解析挂载，新配置与证书才会真正生效。
# 注意：compose 任何子命令都会插值整个文件，必须带 IMAGE= 前缀，否则报 required variable
IMAGE="$IMAGE" docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps nginx

echo "==> 部署完成: $IMAGE"
IMAGE="$IMAGE" docker compose -f docker-compose.prod.yml ps

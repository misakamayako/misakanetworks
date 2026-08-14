#!/usr/bin/env bash
# ============================================================================
# ECS 部署脚本：从阿里云 ACR 拉取指定 tag 的镜像并重启服务（不依赖 GitHub Actions）
#
# 用法:      ./deploy.sh [镜像tag]     # 默认 latest
# 首次使用:  先登录 ACR 一次（凭证会保存在 ~/.docker/config.json，之后无需再登录）:
#             docker login registry.cn-hangzhou.aliyuncs.com
# 前置条件:  本目录是 misakanetworks 仓库的 clone，且 docker compose 可用
# ============================================================================
set -euo pipefail

REGISTRY="registry.cn-hangzhou.aliyuncs.com"
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

echo "==> 拉取镜像: $IMAGE"
IMAGE="$IMAGE" docker compose -f docker-compose.prod.yml pull

echo "==> 启动/更新服务"
IMAGE="$IMAGE" docker compose -f docker-compose.prod.yml up -d

# 证书续期等场景下热重载 nginx；失败不影响本次部署
docker compose -f docker-compose.prod.yml exec -T nginx nginx -s reload || true

echo "==> 部署完成: $IMAGE"
docker compose -f docker-compose.prod.yml ps

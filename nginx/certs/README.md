# HTTPS 证书

把证书放到本目录：

- `fullchain.pem` —— 证书链
- `privkey.pem` —— 私钥

## 获取证书

推荐 Let's Encrypt。首次可以先在服务器上用 certbot 的 standalone 模式签发：

```bash
docker run --rm -p 80:80 -v "$PWD/nginx/certs:/etc/letsencrypt" \
  certbot/certbot certonly --standalone -d 你的域名 --email 你的邮箱 \
  --agree-tos --no-eff-email
```

签发后把容器内 `/etc/letsencrypt/live/你的域名/` 下的 `fullchain.pem` 和 `privkey.pem`
复制到本目录。后续续期可以再加 certbot 容器，这里先保证能跑起来。

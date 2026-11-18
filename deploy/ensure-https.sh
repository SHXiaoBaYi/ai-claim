#!/usr/bin/env bash
# 在 ECS 上执行：给公网 IP 申请 Let’s Encrypt 证书，并安排每天续期。
# IP 证书必须用 shortlived 配置，有效期约 6 天。
# 用法：bash deploy/ensure-https.sh
set -euo pipefail

ECS_IP="${ECS_IP:-47.103.191.164}"
CERT_NAME="${CERT_NAME:-ip-origin}"
WEBROOT="${WEBROOT:-/opt/xby-admin/html}"
CERTBOT="${CERTBOT:-/opt/certbot/bin/certbot}"

install_python() {
  if command -v python3 >/dev/null 2>&1; then
    return
  fi
  echo "==> 安装 Python 3"
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y python3
  elif command -v yum >/dev/null 2>&1; then
    yum install -y python3
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y python3 python3-venv
  else
    echo "无法安装 Python 3" >&2
    exit 1
  fi
}

install_certbot() {
  if [[ -x "$CERTBOT" ]] && "$CERTBOT" --help all 2>/dev/null | grep -q -- '--ip-address'; then
    return
  fi
  install_python
  if ! python3 -c 'import venv' >/dev/null 2>&1; then
    if command -v dnf >/dev/null 2>&1; then
      dnf install -y python3-pip || dnf install -y python3
    elif command -v yum >/dev/null 2>&1; then
      yum install -y python3-pip || yum install -y python3
    elif command -v apt-get >/dev/null 2>&1; then
      apt-get install -y python3-venv
    fi
  fi
  echo "==> 安装 Certbot（需要 5.4+ 才能为 IP 申请证书）"
  python3 -m venv /opt/certbot
  /opt/certbot/bin/pip install --upgrade pip
  /opt/certbot/bin/pip install 'certbot>=5.4'
  ln -sfn "$CERTBOT" /usr/local/bin/certbot
}

open_firewall() {
  if command -v firewall-cmd >/dev/null 2>&1; then
    firewall-cmd --permanent --add-service=http || true
    firewall-cmd --permanent --add-service=https || true
    firewall-cmd --reload || true
  fi
}

mkdir -p "$WEBROOT/.well-known/acme-challenge"
install_certbot
open_firewall

if [[ ! -f "/etc/letsencrypt/live/${CERT_NAME}/fullchain.pem" ]]; then
  echo "==> 为 ${ECS_IP} 申请 HTTPS 证书"
  "$CERTBOT" certonly \
    --non-interactive \
    --agree-tos \
    --register-unsafely-without-email \
    --cert-name "$CERT_NAME" \
    --preferred-profile shortlived \
    --webroot \
    --webroot-path "$WEBROOT" \
    --ip-address "$ECS_IP" \
    --deploy-hook "nginx -t && systemctl reload nginx"
else
  echo "==> 证书已存在：/etc/letsencrypt/live/${CERT_NAME}/"
fi

if [[ -d /opt/xby-admin/config ]]; then
  sed -i "s#http://${ECS_IP}#https://${ECS_IP}#g" /opt/xby-admin/config/*.yml /opt/xby-admin/config/*.yaml 2>/dev/null || true
fi

cat > /etc/cron.d/xby-certbot <<EOF
# IP 证书约 6 天过期，每天检查两次。
0 3,15 * * * root ${CERTBOT} renew --quiet --deploy-hook "nginx -t && systemctl reload nginx"
EOF
chmod 644 /etc/cron.d/xby-certbot

echo "==> HTTPS 证书就绪。云安全组还需放行 TCP 443。"

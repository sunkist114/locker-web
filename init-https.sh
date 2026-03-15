#!/bin/bash
# HTTPS 최초 설정 스크립트 - 처음 한 번만 실행
set -e

DOMAIN="locker-cse.sch.ac.kr"
EMAIL="${1:-}"

if [ -z "$EMAIL" ]; then
    echo "사용법: ./init-https.sh your@email.com"
    exit 1
fi

mkdir -p certbot/conf certbot/www

echo "▶ 1단계: 임시 nginx 시작 (포트 80)..."
docker run -d --name nginx-temp \
    -p 80:80 \
    -v "$(pwd)/nginx/nginx-init.conf:/etc/nginx/nginx.conf:ro" \
    -v "$(pwd)/certbot/www:/var/www/certbot" \
    nginx:alpine

echo "▶ 2단계: Let's Encrypt 인증서 발급..."
docker run --rm \
    -v "$(pwd)/certbot/conf:/etc/letsencrypt" \
    -v "$(pwd)/certbot/www:/var/www/certbot" \
    certbot/certbot certonly \
    --webroot \
    -w /var/www/certbot \
    -d "$DOMAIN" \
    --email "$EMAIL" \
    --agree-tos \
    --no-eff-email

echo "▶ 3단계: 임시 nginx 제거..."
docker stop nginx-temp && docker rm nginx-temp

echo "▶ 4단계: 운영 스택 시작 (HTTPS 포함)..."
docker compose up -d

echo ""
echo "✅ HTTPS 설정 완료!"
echo "   접속: https://$DOMAIN"
echo ""
echo "⚠️  .env 파일에서 아래 항목을 https로 변경하세요:"
echo "   KAKAO_REDIRECT_URI=https://$DOMAIN/oauth/kakao/callback"
echo "   KAKAO_STUDENT_PAGE_URL=https://$DOMAIN/student.html"

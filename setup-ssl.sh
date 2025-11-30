#!/bin/bash
# ==================================================
# SSL 인증서 발급 스크립트 (Let's Encrypt)
# ==================================================

set -e

# 도메인 설정 (실제 도메인으로 변경 필요)
DOMAIN="yourdomain.com"
WWW_DOMAIN="www.yourdomain.com"
EMAIL="your-email@example.com"

echo "============================================"
echo "SSL 인증서 발급 시작"
echo "도메인: $DOMAIN, $WWW_DOMAIN"
echo "============================================"
echo ""

# 1. Certbot 디렉토리 생성
echo "📁 Certbot 디렉토리 생성 중..."
mkdir -p certbot/conf
mkdir -p certbot/www
echo "✅ 디렉토리 생성 완료"
echo ""

# 2. Docker Compose로 Nginx 시작 (HTTP만)
echo "🚀 Nginx 컨테이너 시작 중..."
docker-compose -f docker-compose.prod.yml up -d nginx
echo "✅ Nginx 시작 완료"
echo ""

# 3. Certbot으로 인증서 발급
echo "🔐 SSL 인증서 발급 중..."
docker run --rm \
  -v $(pwd)/certbot/conf:/etc/letsencrypt \
  -v $(pwd)/certbot/www:/var/www/certbot \
  certbot/certbot certonly \
  --webroot \
  --webroot-path=/var/www/certbot \
  --email $EMAIL \
  --agree-tos \
  --no-eff-email \
  -d $DOMAIN \
  -d $WWW_DOMAIN

if [ $? -eq 0 ]; then
    echo "✅ SSL 인증서 발급 완료!"
else
    echo "❌ SSL 인증서 발급 실패"
    echo "📝 확인 사항:"
    echo "   1. 도메인이 이 서버의 IP를 가리키는지 확인"
    echo "   2. 포트 80이 열려있는지 확인"
    echo "   3. Nginx가 정상 동작하는지 확인"
    exit 1
fi
echo ""

# 4. Nginx 재시작 (HTTPS 활성화)
echo "🔄 Nginx 재시작 중..."
docker-compose -f docker-compose.prod.yml restart nginx
echo "✅ Nginx 재시작 완료"
echo ""

# 5. 인증서 확인
echo "============================================"
echo "SSL 인증서 설치 완료!"
echo "============================================"
echo ""
echo "📋 인증서 정보:"
docker run --rm \
  -v $(pwd)/certbot/conf:/etc/letsencrypt \
  certbot/certbot certificates
echo ""
echo "🌐 브라우저에서 확인:"
echo "   https://$DOMAIN"
echo ""
echo "🔄 자동 갱신 설정:"
echo "   crontab -e"
echo "   0 3 * * * cd $(pwd) && bash renew-ssl.sh >> ssl-renew.log 2>&1"
echo ""

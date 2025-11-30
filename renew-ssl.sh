#!/bin/bash
# ==================================================
# SSL 인증서 자동 갱신 스크립트
# ==================================================

echo "🔄 SSL 인증서 갱신 시작: $(date)"

# Certbot으로 인증서 갱신
docker run --rm \
  -v $(pwd)/certbot/conf:/etc/letsencrypt \
  -v $(pwd)/certbot/www:/var/www/certbot \
  certbot/certbot renew \
  --quiet

# Nginx 재시작
if [ $? -eq 0 ]; then
    echo "✅ 인증서 갱신 완료"
    docker-compose -f docker-compose.prod.yml restart nginx
    echo "✅ Nginx 재시작 완료"
else
    echo "⚠️ 인증서 갱신 불필요 또는 실패"
fi

echo "🏁 갱신 작업 종료: $(date)"

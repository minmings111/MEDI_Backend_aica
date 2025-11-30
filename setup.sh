#!/bin/bash
# ==================================================
# Medi Backend - 초기 설정 스크립트
# ==================================================

set -e

echo "============================================"
echo "Medi Backend 초기 설정"
echo "============================================"
echo ""

# 1. .env 파일 생성
if [ -f .env ]; then
    echo "⚠️  .env 파일이 이미 존재합니다."
    read -p "덮어쓰시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "✅ .env 파일 생성 건너뜀"
    else
        cp .env.example .env
        echo "✅ .env 파일 생성 완료"
    fi
else
    cp .env.example .env
    echo "✅ .env 파일 생성 완료"
fi

# 2. application.yml 파일 생성
if [ -f src/main/resources/application.yml ]; then
    echo "⚠️  application.yml 파일이 이미 존재합니다."
    read -p "덮어쓰시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "✅ application.yml 파일 생성 건너뜀"
    else
        cp src/main/resources/application.yml.example src/main/resources/application.yml
        echo "✅ application.yml 파일 생성 완료"
    fi
else
    cp src/main/resources/application.yml.example src/main/resources/application.yml
    echo "✅ application.yml 파일 생성 완료"
fi

# 3. 로그 디렉토리 생성
mkdir -p logs/heapdump
echo "✅ 로그 디렉토리 생성 완료"

echo ""
echo "============================================"
echo "초기 설정 완료!"
echo "============================================"
echo ""
echo "📝 다음 단계:"
echo ""
echo "1. .env 파일 편집:"
echo "   nano .env"
echo ""
echo "   필수 설정 항목:"
echo "   - MYSQL_ROOT_PASSWORD"
echo "   - MYSQL_PASSWORD"
echo "   - GOOGLE_CLIENT_ID"
echo "   - GOOGLE_CLIENT_SECRET"
echo "   - CORS_ALLOWED_ORIGINS"
echo "   - MAIL_USERNAME"
echo "   - MAIL_PASSWORD"
echo ""
echo "2. (선택) application.yml 파일 편집 (로컬 개발 시):"
echo "   nano src/main/resources/application.yml"
echo ""
echo "3. 배포 방법 선택:"
echo ""
echo "   A. Docker Compose로 배포 (권장):"
echo "      bash deploy.sh"
echo ""
echo "   B. 로컬에서 실행:"
echo "      docker-compose up -d mysql redis"
echo "      ./gradlew bootRun"
echo ""
echo "📚 자세한 내용은 README.md를 참고하세요."
echo ""

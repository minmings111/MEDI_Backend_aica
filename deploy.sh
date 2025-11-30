#!/bin/bash
# ==================================================
# Medi Backend - 배포 스크립트
# ==================================================

set -e  # 에러 발생 시 스크립트 중단

echo "============================================"
echo "Medi Backend 배포 시작"
echo "============================================"
echo ""

# 1. .env 파일 확인
if [ ! -f .env ]; then
    echo "❌ .env 파일이 없습니다!"
    echo "📝 .env.example을 복사하여 .env 파일을 생성하고 실제 값으로 변경하세요:"
    echo "   cp .env.example .env"
    echo "   nano .env  # 또는 vim, code 등 편집기 사용"
    exit 1
fi

echo "✅ .env 파일 확인됨"
echo ""

# 2. 필수 환경 변수 확인
required_vars=(
    "MYSQL_ROOT_PASSWORD"
    "MYSQL_PASSWORD"
    "GOOGLE_CLIENT_ID"
    "GOOGLE_CLIENT_SECRET"
    "CORS_ALLOWED_ORIGINS"
    "MAIL_USERNAME"
    "MAIL_PASSWORD"
)

missing_vars=()
for var in "${required_vars[@]}"; do
    if ! grep -q "^${var}=" .env || grep -q "^${var}=your_" .env; then
        missing_vars+=("$var")
    fi
done

if [ ${#missing_vars[@]} -gt 0 ]; then
    echo "❌ 다음 환경 변수가 설정되지 않았습니다:"
    for var in "${missing_vars[@]}"; do
        echo "   - $var"
    done
    echo ""
    echo "📝 .env 파일을 열어 실제 값으로 변경하세요:"
    echo "   nano .env"
    exit 1
fi

echo "✅ 필수 환경 변수 확인됨"
echo ""

# 3. 로그 디렉토리 생성
echo "📁 로그 디렉토리 생성 중..."
mkdir -p logs/heapdump
echo "✅ 로그 디렉토리 생성 완료"
echo ""

# 4. 기존 컨테이너 중지 및 제거
echo "🛑 기존 컨테이너 중지 중..."
docker-compose down || true
echo "✅ 기존 컨테이너 중지 완료"
echo ""

# 5. Docker 이미지 빌드
echo "🔨 Docker 이미지 빌드 중..."
docker-compose build --no-cache
echo "✅ Docker 이미지 빌드 완료"
echo ""

# 6. 컨테이너 시작
echo "🚀 컨테이너 시작 중..."
docker-compose up -d
echo "✅ 컨테이너 시작 완료"
echo ""

# 7. 헬스체크 대기
echo "⏳ 애플리케이션 시작 대기 중 (최대 60초)..."
for i in {1..60}; do
    if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✅ 애플리케이션 정상 시작됨!"
        break
    fi
    echo -n "."
    sleep 1
    
    if [ $i -eq 60 ]; then
        echo ""
        echo "⚠️ 애플리케이션 시작 확인 실패 (60초 초과)"
        echo "📋 로그를 확인하세요:"
        echo "   docker-compose logs -f backend"
        exit 1
    fi
done
echo ""

# 8. 배포 상태 확인
echo "============================================"
echo "배포 완료!"
echo "============================================"
echo ""
echo "📊 컨테이너 상태:"
docker-compose ps
echo ""
echo "🔗 애플리케이션 URL: http://localhost:8080"
echo "🔗 헬스체크: http://localhost:8080/actuator/health"
echo "🔗 API 문서: http://localhost:8080/swagger-ui.html"
echo ""
echo "📋 로그 확인:"
echo "   docker-compose logs -f backend"
echo ""
echo "📊 메모리 사용량 확인:"
echo "   docker stats medi-backend"
echo ""

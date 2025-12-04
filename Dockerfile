# ==================================================
# Medi Backend - Dockerfile (멀티 스테이지 빌드)
# ==================================================

# Stage 1: 빌드
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

# Gradle 캐시 최적화를 위한 의존성 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 소스 코드 복사
COPY src ./src

# 빌드 실행 (테스트 제외)
RUN gradle clean build -x test --no-daemon

# ========================================
# Stage 2: 실행
# ⚠️ 수정됨: openjdk:17-jdk-slim → eclipse-temurin:17-jre-jammy
# 이유: openjdk 공식 이미지가 2022년에 deprecated됨
# ========================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Python과 pip 설치 (yt-dlp 자동 설치용)
# ⚠️ 주의: Ubuntu 기반이므로 apt-get 사용
RUN apt-get update && \
  apt-get install -y --no-install-recommends \
  python3 \
  python3-pip \
  curl \
  && apt-get clean && \
  rm -rf /var/lib/apt/lists/*

# JAR 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 헬스체크
# ⚠️ curl이 위에서 설치되므로 정상 작동
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM 메모리 설정 (기본값) - 컨테이너 실행 시 오버라이드 가능
# Python(yt-dlp) 실행을 위해 Heap 크기를 줄이고 Native 메모리 여유 확보
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

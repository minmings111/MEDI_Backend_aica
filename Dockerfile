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

# Stage 2: 실행
FROM openjdk:17-jdk-slim
WORKDIR /app

# Python과 pip 설치 (yt-dlp 자동 설치용)
RUN apt-get update && \
  apt-get install -y --no-install-recommends \
  python3 \
  python3-pip \
  curl \
  && \
  apt-get clean && \
  rm -rf /var/lib/apt/lists/*

# yt-dlp 설치 (YouTube 영상 메타데이터 수집용)
RUN pip3 install --no-cache-dir yt-dlp>=2025.11.12

# JAR 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 실행
# JVM 메모리 설정 (프로덕션 기본값) - 컨테이너 실행 시 오버라이드 가능
# Python(yt-dlp) 실행을 위해 Native 메모리 여유 확보
# 2.5GB 컨테이너 기준: Heap 1.5GB + Native/yt-dlp 1GB
ENV JAVA_OPTS="-Xms1500m -Xmx1500m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof"

# 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]


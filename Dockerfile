# ==================================================
# Medi Backend - Dockerfile (멀티 스테이지 빌드)
# ==================================================

# --------------------------------------------------
# Stage 1: 빌드 (Gradle)
#  - 여기서 Gradle task ':ensureYtDlp'가 실행됨
#  - 이 태스크가 'yt-dlp' 명령을 호출하므로
#    builder 이미지 안에 yt-dlp가 반드시 있어야 함
# --------------------------------------------------
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

# ⚠️ yt-dlp 설치 (ensureYtDlp가 사용하는 CLI)
#  - gradle 이미지는 Debian/Ubuntu 계열이므로 apt + curl 사용 가능
#  - 최신 yt-dlp 바이너리를 /usr/local/bin/yt-dlp 에 두고 실행 권한 부여
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates python3 python3-pip \
    && curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Gradle 캐시 최적화를 위한 의존성 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 소스 코드 복사
COPY src ./src

# 빌드 실행 (테스트 제외)
#  - 이 시점에 ':ensureYtDlp' 가 실행되는데,
#    위에서 yt-dlp를 설치했으므로 더 이상 실패하지 않음
RUN gradle clean build -x test --no-daemon

# --------------------------------------------------
# Stage 2: 실행 (런타임 이미지)
# --------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Python과 pip 설치 (실행 시 yt-dlp 호출용)
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

# 헬스체크 (Spring Boot Actuator)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM 메모리 설정 (컨테이너 실행 시 변경 가능)
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

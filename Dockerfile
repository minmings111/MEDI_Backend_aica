# ==================================================
# Medi Backend - Dockerfile (Stable Build Version)
# ==================================================

# ---------- Stage 1: Build ----------
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates python3 python3-pip \
    && curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN gradle clean build -x test --no-daemon


# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends python3 python3-pip curl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/build/libs/*.jar app.jar
COPY src/main/resources/서빈감각_final_filtered.json /app/data/서빈감각_final_filtered.json

EXPOSE 8080

# 💡 HealthCheck (프로덕션 안정화 버전)
HEALTHCHECK --interval=30s --timeout=15s --start-period=120s --retries=10 \
    CMD curl -fs http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-Xms256m -Xmx1024m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
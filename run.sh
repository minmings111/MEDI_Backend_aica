#!/bin/bash
# ============================================
# Spring Boot 실행 스크립트 (Linux/Mac)
# 메모리 최적화 설정 포함
# ============================================

# JVM 메모리 설정 (4GB 서버 기준)
# -Xms: 최소 힙 크기 (물리 메모리의 25%)
# -Xmx: 최대 힙 크기 (물리 메모리의 50-75%)
# -XX:MaxMetaspaceSize: 메타스페이스 최대 크기
JAVA_OPTS="-Xms2g -Xmx2g -XX:MaxMetaspaceSize=256m"

# GC 설정 (G1 GC 사용 - 대량 데이터 처리에 유리)
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# GC 로깅 설정
JAVA_OPTS="$JAVA_OPTS -Xlog:gc*:file=logs/gc.log:time,uptime:filecount=10,filesize=10M"

# OOM 발생 시 힙 덤프 자동 생성
JAVA_OPTS="$JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/heapdump/"

# 로그 디렉토리 생성
mkdir -p logs/heapdump

# Spring Boot 실행
echo "============================================"
echo "Spring Boot 실행 중..."
echo "JVM 옵션: $JAVA_OPTS"
echo "============================================"
echo ""

java $JAVA_OPTS -jar build/libs/backend-0.0.1-SNAPSHOT.jar


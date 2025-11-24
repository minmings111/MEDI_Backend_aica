@echo off
REM ============================================
REM Spring Boot 실행 스크립트 (Windows)
REM 메모리 최적화 설정 포함
REM ============================================

REM JVM 메모리 설정 (4GB 서버 기준)
REM -Xms: 최소 힙 크기 (물리 메모리의 25%)
REM -Xmx: 최대 힙 크기 (물리 메모리의 50-75%)
REM -XX:MaxMetaspaceSize: 메타스페이스 최대 크기
set JAVA_OPTS=-Xms2g -Xmx2g -XX:MaxMetaspaceSize=256m

REM GC 설정 (G1 GC 사용 - 대량 데이터 처리에 유리)
set JAVA_OPTS=%JAVA_OPTS% -XX:+UseG1GC -XX:MaxGCPauseMillis=200

REM GC 로깅 설정
set JAVA_OPTS=%JAVA_OPTS% -Xlog:gc*:file=logs/gc.log:time,uptime:filecount=10,filesize=10M

REM OOM 발생 시 힙 덤프 자동 생성
set JAVA_OPTS=%JAVA_OPTS% -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/heapdump/

REM 로그 디렉토리 생성
if not exist "logs" mkdir logs
if not exist "logs\heapdump" mkdir logs\heapdump

REM Spring Boot 실행
echo ============================================
echo Spring Boot 실행 중...
echo JVM 옵션: %JAVA_OPTS%
echo ============================================
echo.

java %JAVA_OPTS% -jar build\libs\backend-0.0.1-SNAPSHOT.jar

pause


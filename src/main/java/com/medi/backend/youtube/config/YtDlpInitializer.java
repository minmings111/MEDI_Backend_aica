package com.medi.backend.youtube.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 애플리케이션 시작 시 yt-dlp 설치 확인 및 자동 설치
 *
 * 실행 시점: 애플리케이션 시작 직후, 다른 빈 초기화 전
 * 실행 빈도: 서버 시작할 때마다 한 번
 * 영향: 서버 시작 시간 약간 증가 (이미 설치되어 있으면 1초 이내)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // 우선순위: 다른 초기화 작업보다 먼저 실행
public class YtDlpInitializer implements ApplicationRunner {

    private final YoutubeSyncConfigProperties youtubeSyncConfigProperties;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // yt-dlp 사용이 비활성화되어 있으면 스킵
        if (!youtubeSyncConfigProperties.isEnableYtDlp()) {
            log.debug("yt-dlp 사용이 비활성화되어 있습니다. 체크를 건너킵니다.");
            return;
        }

        String ytDlpPath = youtubeSyncConfigProperties.getYtDlpPath();
        
        // 환경 감지
        String activeProfile = System.getProperty("spring.profiles.active");
        if (activeProfile == null || activeProfile.isEmpty()) {
            activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        
        if ("dev".equals(activeProfile) || "local".equals(activeProfile)) {
            log.info("🔧 개발 환경 감지: yt-dlp 상세 체크");
        } else if ("prod".equals(activeProfile)) {
            log.info("🚀 프로덕션 환경: yt-dlp 체크 시작");
        }

        log.info("🔍 yt-dlp 설치 확인 시작: path={}", ytDlpPath);

        // 1. yt-dlp 설치 여부 확인
        if (isYtDlpInstalled(ytDlpPath)) {
            String version = getYtDlpVersion(ytDlpPath);
            log.info("✅ yt-dlp가 이미 설치되어 있습니다: version={}", version);
            return;
        }

        // 2. 설치되어 있지 않으면 자동 설치 시도
        log.warn("⚠️ yt-dlp가 설치되어 있지 않습니다. 자동 설치를 시도합니다...");
        if (installYtDlp()) {
            log.info("✅ yt-dlp 자동 설치 완료");
            // 설치 후 다시 확인
            if (isYtDlpInstalled(ytDlpPath)) {
                String version = getYtDlpVersion(ytDlpPath);
                log.info("✅ yt-dlp 설치 확인: version={}", version);
            } else {
                log.error("❌ yt-dlp 설치 후에도 확인되지 않습니다. PATH 설정을 확인하세요.");
            }
        } else {
            log.error("❌ yt-dlp 자동 설치 실패");
            log.warn("📝 수동 설치 방법:");
            log.warn("   1. pip install yt-dlp>=2025.11.12");
            log.warn("   2. 또는 pip3 install yt-dlp>=2025.11.12");
            log.warn("   3. 공식 사이트: https://github.com/yt-dlp/yt-dlp/releases");
            log.warn("⚠️ 자막 추출 기능이 제한될 수 있습니다.");
        }
    }

    /**
     * yt-dlp 설치 여부 확인
     * @param ytDlpPath yt-dlp 실행 경로
     * @return 설치되어 있으면 true
     */
    private boolean isYtDlpInstalled(String ytDlpPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(ytDlpPath, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("yt-dlp 확인 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * yt-dlp 버전 조회
     * @param ytDlpPath yt-dlp 실행 경로
     * @return 버전 문자열
     */
    private String getYtDlpVersion(String ytDlpPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(ytDlpPath, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String version = reader.readLine();
                process.waitFor();
                return version != null ? version.trim() : "unknown";
            }
        } catch (Exception e) {
            log.debug("yt-dlp 버전 조회 실패: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * yt-dlp 자동 설치
     * @return 설치 성공 여부
     */
    private boolean installYtDlp() {
        // pip3 먼저 시도
        if (installWithCommand("pip3", "install", "--upgrade", "yt-dlp>=2025.11.12")) {
            return true;
        }
        // pip 시도
        if (installWithCommand("pip", "install", "--upgrade", "yt-dlp>=2025.11.12")) {
            return true;
        }
        // python3 -m pip 시도
        if (installWithCommand("python3", "-m", "pip", "install", "--upgrade", "yt-dlp>=2025.11.12")) {
            return true;
        }
        // --user 옵션으로 재시도
        log.info("📦 사용자 영역에 설치 시도...");
        if (installWithCommand("pip3", "install", "--user", "--upgrade", "yt-dlp>=2025.11.12")) {
            return true;
        }
        if (installWithCommand("pip", "install", "--user", "--upgrade", "yt-dlp>=2025.11.12")) {
            return true;
        }
        return installWithCommand("python3", "-m", "pip", "install", "--user", "--upgrade", "yt-dlp>=2025.11.12");
    }

    /**
     * 지정된 명령어로 설치 시도
     * @param command 설치 명령어 배열
     * @return 성공 여부
     */
    private boolean installWithCommand(String... command) {
        try {
            log.info("📦 설치 시도: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 출력 로깅 (디버그 레벨)
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("pip 출력: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("✅ 설치 성공: {}", command[0]);
                return true;
            } else {
                log.debug("⚠️ 설치 실패: {} (exitCode={})", command[0], exitCode);
                return false;
            }
        } catch (Exception e) {
            log.debug("⚠️ 설치 시도 에러: {} - {}", command[0], e.getMessage());
            return false;
        }
    }
}


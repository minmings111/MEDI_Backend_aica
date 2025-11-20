package com.medi.backend.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring 비동기 처리 설정
 * 
 * YouTube 자막 추출 병렬 처리를 위한 전용 Executor 설정
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 자막 추출 전용 Executor 빈
     * 
     * I/O 바운드 작업(yt-dlp 실행)에 최적화된 스레드 풀 설정
     * - corePoolSize: 8 (기본 스레드 수)
     * - maxPoolSize: 15 (최대 스레드 수)
     * - queueCapacity: 100 (대기 큐 크기)
     * 
     * 거부 정책: CallerRunsPolicy (큐가 꽉 차면 호출자 스레드에서 실행)
     */
    @Bean(name = "transcriptExecutor")
    public Executor transcriptExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("Transcript-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        log.info("✅ Transcript Executor 빈 생성 완료: corePoolSize=8, maxPoolSize=15, queueCapacity=100");
        
        return executor;
    }

    /**
     * 비동기 작업 예외 처리 핸들러
     * 
     * @Async 메서드에서 발생한 예외를 로깅
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("비동기 작업 예외 발생: method={}, params={}, error={}", 
                     method.getName(), Arrays.toString(params), ex.getMessage(), ex);
        };
    }
}


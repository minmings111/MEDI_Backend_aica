package com.medi.backend.youtube.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * YouTube 동기화 설정
 * 
 * application.yml의 youtube.sync.* 설정값을 관리합니다.
 * 
 * 예시:
 * youtube:
 *   sync:
 *     max-videos-per-hour: 50
 *     max-videos-initial: 5
 * 
 * @Validated 어노테이션으로 설정값 검증을 수행합니다.
 * 잘못된 값(음수, 범위 초과) 입력 시 애플리케이션 시작 시점에 오류가 발생합니다.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "youtube.sync")
public class YoutubeSyncConfigProperties {

    /**
     * 증분 동기화 시 시간당 최대 처리 영상 수
     * 
     * API 할당량 관리를 위한 제한값입니다.
     * 활발한 채널에서도 일일 할당량(10,000 units)을 초과하지 않도록 설정합니다.
     * 
     * 기본값: 50
     * 범위: 1 ~ 1000
     * 
     * 예시:
     * - 1시간에 30개 영상 업로드 → 30개 모두 처리
     * - 1시간에 60개 영상 업로드 → 50개만 처리, 나머지 10개는 다음 시간에
     */
    @Positive(message = "max-videos-per-hour는 양수여야 합니다")
    @Max(value = 1000, message = "max-videos-per-hour는 1000을 초과할 수 없습니다")
    private int maxVideosPerHour = 50;

    /**
     * 초기 동기화 시 저장할 영상 개수
     * 
     * 사용자가 채널을 처음 등록할 때 MySQL에 저장할 영상 개수입니다.
     * 
     * 기본값: 5
     * 범위: 1 ~ 20
     * 
     * 예시:
     * - 채널 등록 시 최신 영상 5개만 MySQL에 저장
     * - 이 5개 영상의 댓글은 필터링 대상이 됩니다
     */
    @Positive(message = "max-videos-initial는 양수여야 합니다")
    @Max(value = 20, message = "max-videos-initial는 20을 초과할 수 없습니다")
    private int maxVideosInitial = 5;
}


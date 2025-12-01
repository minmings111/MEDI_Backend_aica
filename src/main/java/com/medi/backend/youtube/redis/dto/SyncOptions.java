package com.medi.backend.youtube.redis.dto;

import lombok.Builder;
import lombok.Getter;

//  Redis 동기화 옵션 설정
//  초기 동기화와 증분 동기화를 구분하여 다른 옵션을 적용할 수 있도록 함

@Getter
@Builder
public class SyncOptions {

    /**
     * 댓글 개수 제한
     * - 초기 동기화: 100개 제한
     * - 증분 동기화: 제한 없음 (null 또는 0)
     */
    private final Integer maxCommentCount;

    /**
     * 메타데이터 저장 옵션
     * - false: 기본 필드만 (video_id, video_title, channel_id, video_tags)
     * - true: 전체 필드 (조회수, 좋아요 수, 댓글 수, 발행일, 썸네일 등)
     */
    private final boolean includeFullMetadata;

    /**
     * 초기 동기화용 기본 옵션
     */
    public static SyncOptions initialSync() {
        return SyncOptions.builder()
                .maxCommentCount(20) // 댓글 20개 제한 (프로파일링용)
                .includeFullMetadata(false) // 기본 메타데이터만
                .build();
    }

    /**
     * 증분 동기화용 기본 옵션 (새 영상 추가 시)
     */
    public static SyncOptions incrementalSync() {
        return SyncOptions.builder()
                .maxCommentCount(null) // 댓글 제한 없음 (전부 가져오기)
                .includeFullMetadata(true) // 전체 메타데이터
                .build();
    }
}

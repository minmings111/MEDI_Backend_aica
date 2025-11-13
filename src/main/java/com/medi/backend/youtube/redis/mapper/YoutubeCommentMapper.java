package com.medi.backend.youtube.redis.mapper;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

import com.google.api.services.youtube.model.Comment;
import com.medi.backend.youtube.redis.dto.YoutubeComment;

@Component
public class YoutubeCommentMapper {

    public YoutubeComment toRedisComment(Comment comment, String parentId) {
        if (comment == null || comment.getSnippet() == null) {
            return null;
        }

        String commentId = comment.getId();
        String text = comment.getSnippet().getTextDisplay();
        String author = comment.getSnippet().getAuthorDisplayName();
        
        // 작성자 채널 ID 추출 (선택적)
        // YouTube API의 authorChannelId는 ResourceId 객체이며, getValue()로 실제 채널 ID를 가져옴
        // TypeScript 코드 참고: authorChannelId?.value
        String authorChannelId = null;
        if (comment.getSnippet().getAuthorChannelId() != null 
            && comment.getSnippet().getAuthorChannelId().getValue() != null) {
            authorChannelId = comment.getSnippet().getAuthorChannelId().getValue();
        }
        
        // 좋아요 수 추출 (null 가능)
        Long likeCount = null;
        if (comment.getSnippet().getLikeCount() != null) {
            likeCount = comment.getSnippet().getLikeCount().longValue();
        }

        // 발행 시간 변환
        java.time.LocalDateTime publishedAt = null;
        if (comment.getSnippet().getPublishedAt() != null) {
            publishedAt = ZonedDateTime.parse(comment.getSnippet().getPublishedAt().toStringRfc3339())
                .toLocalDateTime();
        }
        
        // 수정 시간 변환 (null 가능)
        java.time.LocalDateTime updatedAt = null;
        if (comment.getSnippet().getUpdatedAt() != null) {
            updatedAt = ZonedDateTime.parse(comment.getSnippet().getUpdatedAt().toStringRfc3339())
                .toLocalDateTime();
        }

        return YoutubeComment.builder()
            .commentId(commentId)
            .parentId(parentId) // null이면 최상위 댓글, 값이 있으면 대댓글
            .text(text)
            .author(author)
            .authorChannelId(authorChannelId)
            .likeCount(likeCount)
            .publishedAt(publishedAt)
            .updatedAt(updatedAt)
            .build();
    }
}

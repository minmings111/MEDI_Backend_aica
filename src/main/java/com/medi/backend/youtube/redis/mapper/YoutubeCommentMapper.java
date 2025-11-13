package com.medi.backend.youtube.redis.mapper;

import org.springframework.stereotype.Component;

import com.google.api.services.youtube.model.Comment;
import com.medi.backend.youtube.redis.dto.YoutubeComment;

/**
 * YouTube API Comment 객체를 Redis 저장용 YoutubeComment DTO로 변환하는 매퍼
 * 
 * 주요 변환 작업:
 * 1. YouTube API의 Comment 객체에서 필요한 필드 추출
 * 2. AI 서버 호환을 위해 스네이크 케이스 필드명으로 매핑
 * 3. 날짜/시간을 ISO 8601 형식 문자열로 변환
 * 
 * 참고:
 * - channel_comment_fetcher.py의 extract_comment_info 메서드와 유사한 역할
 * - Python 코드: comment_info = { "comment_id": ..., "text_original": ..., ... }
 */
@Component
public class YoutubeCommentMapper {

    /**
     * YouTube API Comment 객체를 YoutubeComment DTO로 변환
     * 
     * @param comment YouTube API Comment 객체
     * @param parentId 부모 댓글 ID (최상위 댓글이면 null, 대댓글이면 부모 ID)
     * @return YoutubeComment DTO (변환 실패 시 null)
     */
    public YoutubeComment toRedisComment(Comment comment, String parentId) {
        if (comment == null || comment.getSnippet() == null) {
            return null;
        }

        // 1. 댓글 ID 추출
        String commentId = comment.getId();
        
        // 2. 댓글 원본 텍스트 추출
        // YouTube API: textDisplay (HTML 형식) 또는 textOriginal (순수 텍스트)
        // Python 코드: text_original = comment["snippet"]["textOriginal"]
        String textOriginal = comment.getSnippet().getTextDisplay();
        if (comment.getSnippet().getTextOriginal() != null) {
            textOriginal = comment.getSnippet().getTextOriginal();  // 순수 텍스트 우선 사용
        }
        
        // 3. 작성자 이름 추출
        // Python 코드: author_name = comment["snippet"]["authorDisplayName"]
        String authorName = comment.getSnippet().getAuthorDisplayName();
        
        // 4. 좋아요 수 추출 (null 가능)
        // Python 코드: like_count = comment["snippet"].get("likeCount", 0)
        Long likeCount = null;
        if (comment.getSnippet().getLikeCount() != null) {
            likeCount = comment.getSnippet().getLikeCount().longValue();
        }

        // 5. 발행 시간 변환 (ISO 8601 형식 문자열)
        // YouTube API: DateTime 객체 → RFC3339 문자열로 변환
        // Python 코드: published_at = comment["snippet"]["publishedAt"]  (이미 문자열)
        // Java: DateTime.toStringRfc3339() → ISO 8601 형식 (예: "2021-04-18T10:05:00Z")
        String publishedAt = null;
        if (comment.getSnippet().getPublishedAt() != null) {
            publishedAt = comment.getSnippet().getPublishedAt().toStringRfc3339();
        }

        return YoutubeComment.builder()
            .commentId(commentId)
            .textOriginal(textOriginal)
            .authorName(authorName)
            .likeCount(likeCount)
            .publishedAt(publishedAt)
            .build();
    }
}

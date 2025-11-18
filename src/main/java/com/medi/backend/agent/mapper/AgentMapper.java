package com.medi.backend.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentMapper {
    // find int videoID(using in mysql) by string youtube videoID
    Integer findVideoIdByYoutubeVideoId(String youtubeVideoId);

    // Insert filtered comment into youtube_comments table
    Integer insertFilteredComment(
        @Param("videoId") Integer videoId,
        @Param("youtubeCommentId") String youtubeCommentId,
        @Param("commentText") String commentText,
        @Param("commenterName") String commenterName,
        @Param("publishedAt") String publishedAt, // DATETIME(String → '%Y-%m-%dT%H:%i:%sZ')
        @Param("likeCount") Long likeCount
        // @Param("authorChannelId") String authorChannelId,
        // @Param("updatedAt") String updatedAt,
        // @Param("parentId") String parentId,
        // @Param("totalReplyCount") Long totalReplyCount,
        // @Param("canRate") Boolean canRate,
        // @Param("viewerRating") String viewerRating
    );
}

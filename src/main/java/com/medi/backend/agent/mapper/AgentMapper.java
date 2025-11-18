package com.medi.backend.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentMapper {
    // find int ID(using in mysql) by string youtube ID
    Integer findVideoIdByYoutubeVideoId(String youtubeVideoId);
    Integer findChannelIdByYoutubeChannelId(String youtubeChannelId);
    
    Integer findUserIdByChannelId(Integer channelId);
    
    // find channel_id by video_id
    Integer findChannelIdByVideoId(Integer videoId);
    
    // find youtube_channel_id by channel_id
    String findYoutubeChannelIdByChannelId(Integer channelId);

    // Insert filtered comment into youtube_comments table
    Integer insertFilteredComment(
        @Param("videoId") Integer videoId,
        @Param("youtubeCommentId") String youtubeCommentId,
        @Param("commentText") String commentText,
        @Param("commenterName") String commenterName,
        @Param("publishedAt") String publishedAt, // DATETIME(String → '%Y-%m-%dT%H:%i:%sZ')
        @Param("likeCount") Long likeCount,
        @Param("authorChannelId") String authorChannelId,
        @Param("updatedAt") String updatedAt,
        @Param("parentId") String parentId,
        @Param("totalReplyCount") Long totalReplyCount,
        @Param("canRate") Boolean canRate,
        @Param("viewerRating") String viewerRating 
    );

    // Insert result video into ai_video_analysis_result
    Integer insertResultVideo(
        @Param("videoId") Integer videoId, 
        @Param("summary") String summary, 
        @Param("communicationReport") String communicationReport
    );

    // Insert result channel into ai_channel_analysis_result
    Integer insertResultChannel(
        @Param("channelId") Integer channelId, 
        @Param("profileReport") String profileReport, 
        @Param("ecosystemReport") String ecosystemReport
    );






}

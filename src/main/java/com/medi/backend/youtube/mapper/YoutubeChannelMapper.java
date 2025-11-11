package com.medi.backend.youtube.mapper;

import com.medi.backend.youtube.dto.YoutubeChannelDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface YoutubeChannelMapper {
    void upsert(YoutubeChannelDto channel);
    YoutubeChannelDto findByYoutubeChannelId(@Param("youtubeChannelId") String youtubeChannelId);
    List<YoutubeChannelDto> findByUserId(@Param("userId") Integer userId);
    void updateSyncState(@Param("youtubeChannelId") String youtubeChannelId,
                         @Param("lastSyncedAt") LocalDateTime lastSyncedAt,
                         @Param("lastVideoPublishedAt") LocalDateTime lastVideoPublishedAt);
}



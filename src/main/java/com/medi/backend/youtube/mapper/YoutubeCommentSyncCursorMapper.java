package com.medi.backend.youtube.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.medi.backend.youtube.dto.YoutubeCommentSyncCursorDto;

@Mapper
public interface YoutubeCommentSyncCursorMapper {

    void upsert(YoutubeCommentSyncCursorDto cursor);

    YoutubeCommentSyncCursorDto findByVideoId(@Param("videoId") String videoId);

    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}



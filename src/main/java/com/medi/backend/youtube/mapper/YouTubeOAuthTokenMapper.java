package com.medi.backend.youtube.mapper;

import com.medi.backend.youtube.dto.YouTubeOAuthTokenDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface YouTubeOAuthTokenMapper {

    void upsert(YouTubeOAuthTokenDTO token);

    YouTubeOAuthTokenDTO findByUserId(@Param("userId") Integer userId);

    YouTubeOAuthTokenDTO findByUserIdAndEmail(@Param("userId") Integer userId,
                                               @Param("googleEmail") String googleEmail);

    void updateTokenStatus(@Param("id") Integer id, @Param("status") String status);

    void updateUsageTimestamps(@Param("id") Integer id,
                               @Param("lastUsedAt") String lastUsedAt,
                               @Param("lastRefreshedAt") String lastRefreshedAt);
}



package com.medi.backend.youtube.mapper;

import com.medi.backend.youtube.dto.YoutubeOAuthTokenDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface YoutubeOAuthTokenMapper {

    void upsert(YoutubeOAuthTokenDto token);

    YoutubeOAuthTokenDto findByUserId(@Param("userId") Integer userId);

    YoutubeOAuthTokenDto findByUserIdAndEmail(@Param("userId") Integer userId,
                                              @Param("googleEmail") String googleEmail);

    void updateTokenStatus(@Param("id") Integer id, @Param("status") String status);

    void updateUsageTimestamps(@Param("id") Integer id,
                               @Param("lastUsedAt") String lastUsedAt,
                               @Param("lastRefreshedAt") String lastRefreshedAt);
}



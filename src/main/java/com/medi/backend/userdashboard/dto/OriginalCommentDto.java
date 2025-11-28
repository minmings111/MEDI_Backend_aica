package com.medi.backend.userdashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OriginalCommentDto {

    private String id;
    private String author;
    private String content;
    private String publishedAt;
    private Long likes;
    
}

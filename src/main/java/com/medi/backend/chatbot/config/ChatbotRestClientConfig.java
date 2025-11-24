package com.medi.backend.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 챗봇용 RestClient 설정
 */
@Configuration
public class ChatbotRestClientConfig {
    
    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);      // 연결 타임아웃 5초
        factory.setReadTimeout(600000);       // 읽기 타임아웃 10분 (긴 LLM 응답 대비)
        
        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }
}


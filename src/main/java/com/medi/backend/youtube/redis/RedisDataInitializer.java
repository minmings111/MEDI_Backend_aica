package com.medi.backend.youtube.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Component
public class RedisDataInitializer implements CommandLineRunner {

    private final ObjectMapper mapper;
    private final StringRedisTemplate redisTemplateDb2;

    @Value("${data.uploader.enabled:false}")
    private boolean enabled;

    @Value("${data.uploader.filename:서빈감각_final_filtered.json}")
    private String filename;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    public RedisDataInitializer(ObjectMapper mapper,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort) {
        this.mapper = mapper;
        this.redisHost = redisHost;
        this.redisPort = redisPort;

        // DB 2번 전용 RedisTemplate 생성
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(2); // DB 2번 사용

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        this.redisTemplateDb2 = new StringRedisTemplate();
        this.redisTemplateDb2.setConnectionFactory(connectionFactory);
        this.redisTemplateDb2.afterPropertiesSet();
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("⏸️ Redis Data Initializer is disabled (data.uploader.enabled=false)");
            return;
        }

        // Redis에 초기화 완료 플래그 확인
        String initFlag = "system:data:initialized";
        if (Boolean.TRUE.equals(redisTemplateDb2.hasKey(initFlag))) {
            log.info("⏭️ Data already initialized. Skipping upload...");
            return;
        }

        log.info("🚀 Redis Data Initializer started. Filename: {}", filename);
        uploadVideoDataFromJson();

        // 초기화 완료 플래그 설정
        redisTemplateDb2.opsForValue().set(initFlag, "true");
        log.info("🏁 Initialization flag set. This will not run again on restart.");
    }

    private void uploadVideoDataFromJson() {
        try {
            // ClassPath에서 파일 읽기 (src/main/resources 내부 파일)
            ClassPathResource resource = new ClassPathResource(filename);
            if (!resource.exists()) {
                log.error("❌ JSON file not found in resources: {}", filename);
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode rootNode = mapper.readTree(inputStream);

                // 각 비디오 ID에 대해 처리
                Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();

                int totalVideos = 0;
                int totalComments = 0;

                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String videoId = entry.getKey();
                    JsonNode commentsNode = entry.getValue();

                    // 빈 배열이면 스킵
                    if (!commentsNode.isArray() || commentsNode.size() == 0) {
                        log.debug("⏭️ Skip (no comments): {}", videoId);
                        continue;
                    }

                    // Redis Key 생성
                    String redisKey = "video:" + videoId + ":filtering:filtered";

                    // 분석 시간 생성
                    String analyzedAtStr = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));

                    // Comments 배열 변환
                    ArrayNode commentsArray = mapper.createArrayNode();

                    for (JsonNode comment : commentsNode) {
                        ObjectNode commentNode = mapper.createObjectNode();

                        // 안전하게 필드 가져오기
                        commentNode.put("commentId", getTextSafe(comment, "comment_id"));
                        commentNode.put("textOriginal", getTextSafe(comment, "textOriginal"));
                        commentNode.put("authorName", getTextSafe(comment, "authorDisplayName"));
                        commentNode.put("likeCount", getIntSafe(comment, "likeCount"));
                        commentNode.put("publishedAt", getTextSafe(comment, "publishedAt"));
                        commentNode.put("reason", getTextSafe(comment, "final_filter_reason"));

                        // 추가 필드들
                        if (comment.has("intensity")) {
                            commentNode.put("intensity", getTextSafe(comment, "intensity"));
                        }
                        if (comment.has("thumbnail")) {
                            commentNode.put("thumbnail", getTextSafe(comment, "thumbnail"));
                        }

                        commentsArray.add(commentNode);
                    }

                    // 최종 JSON 객체 생성
                    ObjectNode jsonRoot = mapper.createObjectNode();
                    jsonRoot.set("comments", commentsArray);
                    jsonRoot.put("total", commentsArray.size());
                    jsonRoot.put("analyzedAt", analyzedAtStr);

                    // Redis에 저장
                    String jsonValue = mapper.writeValueAsString(jsonRoot);
                    redisTemplateDb2.opsForValue().set(redisKey, jsonValue);

                    totalVideos++;
                    totalComments += commentsArray.size();

                    log.info("👍 Saved: {} ({} comments)", videoId, commentsArray.size());
                }

                log.info("✅ Data upload completed! Total Videos: {}, Total Comments: {}", totalVideos, totalComments);
            }

        } catch (Exception e) {
            log.error("❌ Error during data upload", e);
        }
    }

    private String getTextSafe(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asText() : "";
    }

    private int getIntSafe(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asInt() : 0;
    }
}

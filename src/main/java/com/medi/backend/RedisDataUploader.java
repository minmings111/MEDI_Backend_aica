package com.medi.backend;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

public class RedisDataUploader {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final int REDIS_DB = 2;
    
    // JSON 파일 경로 (본인 경로에 맞게 수정)
    private static final String JSON_FILE_PATH = "/app/data/서빈감각_final_filtered.json";

    public static void main(String[] args) {
        try (JedisPool jedisPool = new JedisPool(REDIS_HOST, REDIS_PORT)) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.select(REDIS_DB);
                System.out.println("✅ Redis DB " + REDIS_DB + "에 연결되었습니다.");

                uploadVideoDataFromJson(jedis);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Redis 연결 또는 데이터 업로드 중 오류 발생.");
        }
    }

    private static void uploadVideoDataFromJson(Jedis jedis) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            // JSON 파일 읽기
            JsonNode rootNode = mapper.readTree(new File(JSON_FILE_PATH));
            
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
                    System.out.println("⏭️ 스킵 (댓글 없음): " + videoId);
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
                    
                    // 안전하게 필드 가져오기 (null 체크)
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
                jedis.set(redisKey, jsonValue);
                
                totalVideos++;
                totalComments += commentsArray.size();
                
                System.out.println("👍 저장 완료: " + videoId + " (댓글 " + commentsArray.size() + "개)");
            }
            
            System.out.println("\n✅ 업로드 완료! 총 " + totalVideos + "개 영상, " + totalComments + "개 댓글");
            
        } catch (Exception e) {
            System.err.println("데이터 처리 중 오류 발생");
            e.printStackTrace();
        }
    }
    
    // 안전하게 텍스트 가져오기
    private static String getTextSafe(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asText() : "";
    }
    
    // 안전하게 정수 가져오기
    private static int getIntSafe(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asInt() : 0;
    }
}
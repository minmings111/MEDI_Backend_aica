package com.medi.backend.youtube.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.youtube.dto.ChannelAgentRequestDto;
import com.medi.backend.youtube.dto.ChannelAnalysisResponseDto;
import com.medi.backend.youtube.dto.ChannelThreatAnalysisDto;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.ChannelThreatAnalysisMapper;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelThreatAnalysisService {

    private final ChannelThreatAnalysisMapper mapper;
    private final YoutubeChannelMapper youtubeChannelMapper;
    private final ObjectMapper objectMapper;

    /**
     * FastAPI Agent 결과 저장 (채널 기준, NULL Safe + 자동 업서트)
     * 
     * @return 저장된 channel_id (DB의 youtube_channels.id)
     */
    @Transactional
    public Integer saveFromAgent(ChannelAgentRequestDto request) {
        try {
            log.info("FastAPI 채널 분석 저장 시작: channelId={}", request.getChannelId());

            // 1. JSON 파싱
            JsonNode root = objectMapper.readTree(request.getJsonPayload());

            // 2. channelId 결정 (쿼리 파라미터 우선, 없으면 JSON에서 찾기)
            Integer channelId = request.getChannelId();
            if (channelId == null) {
                // JSON에서 youtube_channel_id 찾기
                String youtubeChannelId = safeGetString(root, "youtube_channel_id");
                if (youtubeChannelId == null || youtubeChannelId.isEmpty()) {
                    throw new RuntimeException("channelId가 제공되지 않았습니다. 쿼리 파라미터 또는 JSON의 youtube_channel_id가 필요합니다.");
                }
                
                // YouTube 채널 ID로 DB ID 조회
                YoutubeChannelDto channel = youtubeChannelMapper.findByYoutubeChannelId(youtubeChannelId);
                if (channel == null) {
                    throw new RuntimeException("채널을 찾을 수 없습니다: youtube_channel_id=" + youtubeChannelId);
                }
                channelId = channel.getId();
                log.info("JSON에서 youtube_channel_id로 DB ID 변환: {} -> {}", youtubeChannelId, channelId);
            }

            // 3. NULL Safe 필드 추출
            String channelName = safeGetString(root, "channel_name");
            LocalDateTime generatedAt = safeGetDateTime(root, "generated_at");
            Integer totalComments = safeGetInteger(root, "total_comments");

            // 3. JSON 섹션 추출 (TypeHandler가 자동 처리)
            Map<String, Object> section1 = extractSectionAsMap(root, "section_1_threat_intelligence");
            Map<String, Object> section2 = extractSectionAsMap(root, "section_2_defense_strategy");

            // 4. 통계 데이터 추출
            Integer criticalCount = extractCriticalCount(section1);
            Integer highCount = extractHighCount(section1);

            // 5. DTO 생성
            ChannelThreatAnalysisDto dto = ChannelThreatAnalysisDto.builder()
                    .channelId(channelId)  // 결정된 channelId 사용
                    .channelName(channelName)
                    .generatedAt(generatedAt)
                    .totalComments(totalComments)
                    .criticalCount(criticalCount)
                    .highCount(highCount)
                    .section1ThreatIntelligence(section1)
                    .section2DefenseStrategy(section2)
                    .build();

            // 6. UPSERT (업데이트 또는 삽입)
            if (generatedAt != null) {
                boolean exists = mapper.existsByChannelIdAndGeneratedAt(
                        dto.getChannelId(), dto.getGeneratedAt());

                if (exists) {
                    mapper.updateAnalysis(dto);
                    log.info("기존 채널 분석 업데이트 완료: channelId={}, generatedAt={}", 
                            channelId, generatedAt);
                } else {
                    mapper.insertAnalysis(dto);
                    log.info("새 채널 분석 저장 완료: channelId={}, id={}, generatedAt={}", 
                            channelId, dto.getId(), generatedAt);
                }
            } else {
                // generatedAt이 null이면 그냥 INSERT
                mapper.insertAnalysis(dto);
                log.info("새 채널 분석 저장 완료 (generatedAt null): channelId={}, id={}", 
                        channelId, dto.getId());
            }

            return channelId;

        } catch (Exception e) {
            log.error("채널 분석 저장 실패: channelId={}", request.getChannelId(), e);
            throw new RuntimeException("채널 분석 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * API 1: 채널 최신 보고서 메타데이터 조회
     */
    public ChannelAnalysisResponseDto getMetadata(Integer channelId) {
        ChannelThreatAnalysisDto dto = mapper.findLatestByChannelId(channelId)
                .orElseThrow(() -> new RuntimeException("채널 분석 결과를 찾을 수 없습니다"));

        return ChannelAnalysisResponseDto.builder()
                .id(dto.getId())
                .channelId(dto.getChannelId())
                .channelName(dto.getChannelName())
                .generatedAt(dto.getGeneratedAt())
                .totalComments(dto.getTotalComments())
                .criticalCount(dto.getCriticalCount())
                .highCount(dto.getHighCount())
                .riskLevel(calculateRiskLevel(dto.getCriticalCount(), dto.getHighCount()))
                .hasSectionThreatIntelligence(dto.getSection1ThreatIntelligence() != null)
                .hasSectionDefenseStrategy(dto.getSection2DefenseStrategy() != null)
                .build();
    }

    /**
     * API 2: 위협 인텔리전스 조회 (원본 JSON 구조 그대로)
     */
    public Map<String, Object> getThreatIntelligence(Integer channelId) {
        ChannelThreatAnalysisDto dto = mapper.findLatestByChannelId(channelId)
                .orElseThrow(() -> new RuntimeException("채널 분석 결과를 찾을 수 없습니다"));

        Map<String, Object> response = new HashMap<>();

        if (dto.getSection1ThreatIntelligence() != null) {
            // TypeHandler가 이미 Map으로 변환해줌
            response.putAll(dto.getSection1ThreatIntelligence());
            response.put("data_available", true);
        } else {
            response.put("data_available", false);
            response.put("message", "위협 인텔리전스 데이터가 없습니다");
        }

        // 메타 정보 추가
        response.put("_meta", createMetaInfo(channelId, dto));

        return response;
    }

    /**
     * API 3: 방어 전략 조회 (원본 JSON 구조 그대로)
     */
    public Map<String, Object> getDefenseStrategy(Integer channelId) {
        ChannelThreatAnalysisDto dto = mapper.findLatestByChannelId(channelId)
                .orElseThrow(() -> new RuntimeException("채널 분석 결과를 찾을 수 없습니다"));

        Map<String, Object> response = new HashMap<>();

        if (dto.getSection2DefenseStrategy() != null) {
            // TypeHandler가 이미 Map으로 변환해줌
            response.putAll(dto.getSection2DefenseStrategy());
            response.put("data_available", true);
        } else {
            response.put("data_available", false);
            response.put("message", "방어 전략 데이터가 없습니다");
        }

        // 메타 정보 추가
        response.put("_meta", createMetaInfo(channelId, dto));

        return response;
    }

    /**
     * API 4: 채널 분석 히스토리 조회
     */
    public List<Map<String, Object>> getAnalysisHistory(Integer channelId, Integer limit) {
        List<ChannelThreatAnalysisDto> history = mapper.findByChannelIdOrderByGeneratedAt(
                channelId, limit != null ? limit : 10);

        return history.stream().map(dto -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("id", dto.getId());
            summary.put("generatedAt", dto.getGeneratedAt());
            summary.put("totalComments", dto.getTotalComments());
            summary.put("criticalCount", dto.getCriticalCount());
            summary.put("highCount", dto.getHighCount());
            summary.put("riskLevel", calculateRiskLevel(dto.getCriticalCount(), dto.getHighCount()));
            summary.put("hasThreatIntelligence", dto.getSection1ThreatIntelligence() != null);
            summary.put("hasDefenseStrategy", dto.getSection2DefenseStrategy() != null);
            return summary;
        }).collect(Collectors.toList());
    }

    // === PRIVATE 헬퍼 메서드들 ===

    private Map<String, Object> createMetaInfo(Integer channelId, ChannelThreatAnalysisDto dto) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("channel_id", channelId);
        meta.put("generated_at", dto.getGeneratedAt());
        return meta;
    }

    private String safeGetString(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return null;
    }

    private Integer safeGetInteger(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asInt();
        }
        return null;
    }

    private LocalDateTime safeGetDateTime(JsonNode node, String fieldName) {
        String dateStr = safeGetString(node, fieldName);
        if (dateStr != null) {
            try {
                // ISO 형식 파싱 (밀리초 포함 처리)
                String normalized = dateStr.length() > 19 ? dateStr.substring(0, 19) : dateStr;
                return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                log.warn("날짜 파싱 실패: {}", dateStr, e);
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSectionAsMap(JsonNode root, String sectionName) {
        JsonNode section = root.path(sectionName);
        if (section.isNull() || section.isMissingNode()) {
            return null;
        }
        try {
            return (Map<String, Object>) objectMapper.convertValue(section, Map.class);
        } catch (Exception e) {
            log.error("섹션 추출 실패: {}", sectionName, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Integer extractCriticalCount(Map<String, Object> section1) {
        if (section1 == null) return null;
        try {
            Map<String, Object> intensity = (Map<String, Object>) section1.get("intensity_distribution");
            if (intensity == null) return null;
            Object critical = intensity.get("critical");
            return critical instanceof Number ? ((Number) critical).intValue() : null;
        } catch (Exception e) {
            log.warn("Critical count 추출 실패", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Integer extractHighCount(Map<String, Object> section1) {
        if (section1 == null) return null;
        try {
            Map<String, Object> intensity = (Map<String, Object>) section1.get("intensity_distribution");
            if (intensity == null) return null;
            Object high = intensity.get("high");
            return high instanceof Number ? ((Number) high).intValue() : null;
        } catch (Exception e) {
            log.warn("High count 추출 실패", e);
            return null;
        }
    }

    private String calculateRiskLevel(Integer critical, Integer high) {
        int c = (critical != null) ? critical : 0;
        int h = (high != null) ? high : 0;

        if (c >= 15) return "CRITICAL";
        if (c >= 10 || h >= 15) return "HIGH";
        if (c >= 5 || h >= 10) return "MEDIUM";
        return "LOW";
    }
}


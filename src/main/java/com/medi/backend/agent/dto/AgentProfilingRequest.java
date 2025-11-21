package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AgentProfilingRequest {
    @JsonProperty("channelId")
    private final String channelId;
    
    @JsonProperty("profileData")
    private final ProfileData profileData;
    
    @JsonProperty("metadata")
    private final Metadata metadata;
    
    public AgentProfilingRequest(
        @JsonProperty("channelId") String channelId,
        @JsonProperty("profileData") ProfileData profileData,
        @JsonProperty("metadata") Metadata metadata
    ) {
        this.channelId = channelId;
        this.profileData = profileData;
        this.metadata = metadata;
    }
    
    @Getter
    public static class ProfileData {
        @JsonProperty("creatorProfile")
        private final Object creatorProfile;  // JSON으로 저장하므로 Object로 받음
        
        @JsonProperty("commentEcosystem")
        private final Object commentEcosystem;  // JSON으로 저장
        
        @JsonProperty("channelCommunication")
        private final Object channelCommunication;  // JSON으로 저장
        
        public ProfileData(
            @JsonProperty("creatorProfile") Object creatorProfile,
            @JsonProperty("commentEcosystem") Object commentEcosystem,
            @JsonProperty("channelCommunication") Object channelCommunication
        ) {
            this.creatorProfile = creatorProfile;
            this.commentEcosystem = commentEcosystem;
            this.channelCommunication = channelCommunication;
        }
    }
    
    @Getter
    public static class Metadata {
        @JsonProperty("profilingCompletedAt")
        private final String profilingCompletedAt;
        
        @JsonProperty("version")
        private final String version;
        
        @JsonProperty("agentVersion")
        private final Object agentVersion;  // JSON으로 저장
        
        public Metadata(
            @JsonProperty("profilingCompletedAt") String profilingCompletedAt,
            @JsonProperty("version") String version,
            @JsonProperty("agentVersion") Object agentVersion
        ) {
            this.profilingCompletedAt = profilingCompletedAt;
            this.version = version;
            this.agentVersion = agentVersion;
        }
    }
}


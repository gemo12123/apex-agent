package org.gemo.apex.memory.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSearchHit {
    private String sourceType;
    private String sessionId;
    private String messageId;
    private Integer turnNo;
    private Long sortNo;
    private String role;
    private double score;
    private ScoreBreakdown scoreBreakdown;
    private String snippet;
    private LocalDateTime createTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBreakdown {
        private double fts;
        private double vector;
        private double hybrid;
    }
}

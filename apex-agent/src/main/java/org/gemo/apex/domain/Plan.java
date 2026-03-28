package org.gemo.apex.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Optional;

/**
 * 计划模型
 */
@Data
public class Plan {
    @JsonProperty(required = true, value = "stages")
    @JsonPropertyDescription("代办阶段定义")
    private List<Stage> stages;

    public Optional<Stage> currentStage(String stageId) {
        if (CollectionUtils.isEmpty(stages)) {
            return Optional.empty();
        }
        for (Stage stage : stages) {
            if (stage.getId().equals(stageId)) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
    }

    public Optional<Stage> nextStage(String currentStageId) {
        if (CollectionUtils.isEmpty(stages)) {
            return Optional.empty();
        }
        if (StringUtils.isEmpty(currentStageId)) {
            return Optional.of(stages.getFirst());
        }

        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i);
            if (stage.getId().equals(currentStageId) && stages.size() != i + 1) {
                return Optional.of(stages.get(i + 1));
            }
        }
        return Optional.empty();
    }
}

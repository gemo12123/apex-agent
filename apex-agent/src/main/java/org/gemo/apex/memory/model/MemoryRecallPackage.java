package org.gemo.apex.memory.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 送模前召回的记忆集合。
 */
@Data
public class MemoryRecallPackage {

    /**
     * 用户画像记忆。
     */
    private List<MemoryItem> profileItems = new ArrayList<>();

    /**
     * 用户执行历史。
     */
    private List<MemoryItem> executionHistoryItems = new ArrayList<>();

    /**
     * 智能体经验。
     */
    private List<MemoryItem> experienceItems = new ArrayList<>();
}

package org.gemo.apex.memory.web.dto;

import lombok.Data;

/**
 * 记忆更新请求。
 */
@Data
public class MemoryUpdateRequest {

    /**
     * 更新后的记忆内容。
     */
    private String content;
}

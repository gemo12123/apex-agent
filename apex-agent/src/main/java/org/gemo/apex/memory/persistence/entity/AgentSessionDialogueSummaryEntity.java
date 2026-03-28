package org.gemo.apex.memory.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话压缩摘要实体。
 */
@Data
@TableName("agent_session_dialogue_summary")
public class AgentSessionDialogueSummaryEntity {

    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    @TableField("role")
    private String role;

    @TableField("message_type")
    private String messageType;

    @TableField("content")
    private String content;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("compacted_to_sort_no")
    private Long compactedToSortNo;

    @TableField("source_turn_no")
    private Integer sourceTurnNo;

    @TableField("version_no")
    private Long versionNo;

    /**
     * 完整摘要消息 JSON。
     */
    @TableField("message_payload")
    private String messagePayload;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

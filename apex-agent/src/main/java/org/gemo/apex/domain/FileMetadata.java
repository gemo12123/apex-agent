package org.gemo.apex.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {
    private String id; // 唯一标识 (UUID or Path)
    private String name; // 文件名
    private String path; // MinIO存储路径
    private String summary; // 摘要
    private String creator; // 创建者 Agent Name
    private Long size; // 大小
    private LocalDateTime createTime;

    // 缓存内容 (可选，用于避免重复读取或SubAgent直接传回内容)
    private String content;
}

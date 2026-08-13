package org.gemo.apex.common.skill;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

/** Skill 子资源。元信息在发现 Skill 时加载，content 在首次读取资源后缓存。 */
public final class SkillResource {
    private final String path;
    private final String fileName;
    private final String fileType;
    private volatile String content;

    public SkillResource(String path, String fileName, String fileType) {
        this(path, fileName, fileType, null);
    }

    public SkillResource(String path, String fileName, String fileType, String content) {
        this.path = required(path, "path");
        this.fileName = required(fileName, "fileName");
        this.fileType = required(fileType, "fileType");
        this.content = content;
    }

    public String path() {
        return path;
    }

    public String fileName() {
        return fileName;
    }

    public String fileType() {
        return fileType;
    }

    public String content() {
        return content;
    }

    public void cacheContent(String value) {
        nonNull(value, "content");
        synchronized (this) {
            if (content == null) {
                content = value;
            }
        }
    }
}

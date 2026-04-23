package org.gemo.apex.tool.skills;

public interface SkillResource {

    String relativePath();

    String content();

    static DefaultSkillResource.Builder builder() {
        return new DefaultSkillResource.Builder();
    }
}

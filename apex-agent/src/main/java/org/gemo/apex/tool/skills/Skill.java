package org.gemo.apex.tool.skills;

import java.util.List;

public interface Skill {

    String name();

    String description();

    String content();

    List<SkillResource> resources();

    static DefaultSkill.Builder builder() {
        return new DefaultSkill.Builder();
    }
}

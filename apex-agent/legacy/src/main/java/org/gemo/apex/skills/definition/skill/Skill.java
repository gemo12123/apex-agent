package org.gemo.apex.skills.definition.skill;

import org.gemo.apex.skills.definition.skill.impl.DefaultSkill;
import org.gemo.apex.skills.definition.resource.SkillResource;

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

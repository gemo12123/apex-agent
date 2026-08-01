package org.gemo.apex.skills.definition.resource;

import org.gemo.apex.skills.definition.resource.impl.DefaultSkillResource;

public interface SkillResource {

    String relativePath();

    String content();

    static DefaultSkillResource.Builder builder() {
        return new DefaultSkillResource.Builder();
    }
}

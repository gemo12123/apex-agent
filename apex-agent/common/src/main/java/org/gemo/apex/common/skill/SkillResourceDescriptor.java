package org.gemo.apex.common.skill;

import static org.gemo.apex.common.support.DomainValues.required;

public record SkillResourceDescriptor(String name, String mediaType, String location) {
    public SkillResourceDescriptor {
        name = required(name, "name");
        mediaType = required(mediaType, "mediaType");
        location = required(location, "location");
    }
}

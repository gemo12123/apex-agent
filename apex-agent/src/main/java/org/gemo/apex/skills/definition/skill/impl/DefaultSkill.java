package org.gemo.apex.skills.definition.skill.impl;

import org.gemo.apex.skills.definition.skill.AbstractSkill;

public final class DefaultSkill extends AbstractSkill {

    private DefaultSkill(Builder builder) {
        super(builder);
    }

    public static final class Builder extends BaseBuilder<Builder> {

        public DefaultSkill build() {
            return new DefaultSkill(this);
        }
    }
}

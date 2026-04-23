package org.gemo.apex.tool.skills;

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

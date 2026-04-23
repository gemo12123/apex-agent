package org.gemo.apex.tool.skills;

public final class ActivateSkillToolConfig {

    static final String DEFAULT_NAME = "activate_skill";
    static final String DEFAULT_DESCRIPTION_TEMPLATE = "通过名称激活一个专用智能体技能。可用技能名称：{available_skill_names}。返回被包裹在 <activated_skill> 标签中的该技能指令。这些指令为当前任务提供专门的指导。当你识别到某个任务与某个技能的描述相匹配时，应使用该技能。只能使用 <available_skills> 部分中完全一致的技能名称。";
    static final String DEFAULT_PARAMETER_NAME = "command";
    static final String DEFAULT_PARAMETER_DESCRIPTION = "The skill name (no arguments). E.g., \"pdf\" or \"xlsx\"";

    final String name;
    final String descriptionTemplate;
    final String parameterName;
    final String parameterDescription;
    final boolean returnDirect;

    private ActivateSkillToolConfig(Builder builder) {
        this.name = builder.name == null ? DEFAULT_NAME : builder.name;
        this.descriptionTemplate = builder.descriptionTemplate == null ? DEFAULT_DESCRIPTION_TEMPLATE
                : builder.descriptionTemplate;
        this.parameterName = builder.parameterName == null ? DEFAULT_PARAMETER_NAME : builder.parameterName;
        this.parameterDescription = builder.parameterDescription == null ? DEFAULT_PARAMETER_DESCRIPTION
                : builder.parameterDescription;
        this.returnDirect = builder.returnDirect != null && builder.returnDirect;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private String descriptionTemplate;
        private String parameterName;
        private String parameterDescription;
        private Boolean returnDirect;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder descriptionTemplate(String descriptionTemplate) {
            this.descriptionTemplate = descriptionTemplate;
            return this;
        }

        public Builder parameterName(String parameterName) {
            this.parameterName = parameterName;
            return this;
        }

        public Builder parameterDescription(String parameterDescription) {
            this.parameterDescription = parameterDescription;
            return this;
        }

        public Builder returnDirect(Boolean returnDirect) {
            this.returnDirect = returnDirect;
            return this;
        }

        public ActivateSkillToolConfig build() {
            return new ActivateSkillToolConfig(this);
        }
    }
}

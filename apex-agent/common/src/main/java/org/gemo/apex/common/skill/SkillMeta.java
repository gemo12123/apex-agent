package org.gemo.apex.common.skill;

import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Objects;

/** Skill 的基础元信息。子类可以补充 Provider 特有的元数据。 */
public class SkillMeta {
    private final String name;
    private final String description;

    public SkillMeta(String name, String description) {
        this.name = required(name, "name");
        this.description = required(description, "description");
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (value == null || getClass() != value.getClass()) {
            return false;
        }
        SkillMeta that = (SkillMeta) value;
        return name.equals(that.name) && description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description);
    }

    @Override
    public String toString() {
        return "SkillMeta[name=" + name + ", description=" + description + "]";
    }
}

package org.gemo.apex.protocol.event.detail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EditableFieldInputType {
    TEXT("text"),
    TEXTAREA("textarea"),
    SINGLE_SELECT("single-select"),
    CONFIRM("confirm"),
    DATE("date"),
    DATETIME("datetime");

    private final String wireValue;

    EditableFieldInputType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }

    @JsonCreator
    public static EditableFieldInputType fromWireValue(String rawValue) {
        if (rawValue != null) {
            for (EditableFieldInputType value : values()) {
                if (value.wireValue.equalsIgnoreCase(rawValue)) {
                    return value;
                }
            }
        }
        return TEXT;
    }
}

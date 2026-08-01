package org.gemo.apex.memory.search;

import java.util.StringJoiner;

public final class PgVectorLiteralFormatter {

    private PgVectorLiteralFormatter() {
    }

    public static String format(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}

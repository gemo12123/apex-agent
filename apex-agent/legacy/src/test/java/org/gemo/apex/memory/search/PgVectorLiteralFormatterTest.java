package org.gemo.apex.memory.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PgVectorLiteralFormatterTest {

    @Test
    void formatShouldProducePgVectorLiteral() {
        assertEquals("[0.1,0.2,0.3]", PgVectorLiteralFormatter.format(new float[] {0.1f, 0.2f, 0.3f}));
    }

    @Test
    void formatShouldReturnNullForNullVector() {
        assertNull(PgVectorLiteralFormatter.format(null));
    }
}

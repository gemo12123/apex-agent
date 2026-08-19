package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.gemo.apex.kit.subagent.SseEventDecoder;
import org.junit.jupiter.api.Test;

class SseEventDecoderTest {
    @Test
    void decodesMultilineEventsAtEventBoundary() {
        var decoder = new SseEventDecoder();
        decoder.accept("data: {");
        decoder.accept("data: }");

        assertEquals(List.of("{\n}"), decoder.accept(""));
    }
}

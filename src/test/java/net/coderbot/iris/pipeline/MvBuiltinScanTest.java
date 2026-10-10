package net.coderbot.iris.pipeline;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MvBuiltinScanTest {

    static Stream<Arguments> sources() {
        return Stream.of(
            Arguments.of("variable usage", "#version 120\nvoid main() { vec4 p = gl_ModelViewMatrix * vec4(1.0); gl_FragColor = p; }", true),
            Arguments.of("core profile matrix names", "#version 150\nuniform mat4 modelViewMatrixInverse;\nvoid main() { gl_FragData[0] = modelViewMatrixInverse * vec4(1.0); }", true),
            Arguments.of("ftransform call", "#version 120\nvoid main() { gl_Position = ftransform(); }", true),
            Arguments.of("comment only", "#version 120\n// gl_ModelViewMatrix in a comment only\nvoid main() { gl_FragColor = vec4(1.0); }", false),
            Arguments.of("null source", null, false));
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("sources")
    void referencesMvBuiltins(String name, String source, boolean expected) {
        assertEquals(expected, DeferredWorldRenderingPipeline.referencesMvBuiltins(source));
    }
}

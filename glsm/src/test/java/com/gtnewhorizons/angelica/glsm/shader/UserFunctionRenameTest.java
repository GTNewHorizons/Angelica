package com.gtnewhorizons.angelica.glsm.shader;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserFunctionRenameTest {

    static Stream<Arguments> renames() {
        return Stream.of(
            Arguments.of("definition, forward declaration and calls are renamed", """
                #version 330 core
                float fade(float t);
                out vec4 color;
                void main() { color = vec4(fade(0.5)); }
                float fade(float t) { return t * t; }
                """, """
                #version 330 core
                float angelica_renamed_fade(float t);
                out vec4 color;
                void main() { color = vec4(angelica_renamed_fade(0.5)); }
                float angelica_renamed_fade(float t) { return t * t; }
                """),
            Arguments.of("overloads of one name are renamed together", """
                #version 330 core
                out vec4 color;
                float clamp(float min, float f, float max) { return f; }
                vec2 clamp(vec2 f) { return vec2(clamp(0.0, f.x, 1.0), clamp(0.0, f.y, 1.0)); }
                void main() { color = vec4(clamp(vec2(0.5)), 0.0, 1.0); }
                """, """
                #version 330 core
                out vec4 color;
                float angelica_renamed_clamp(float min, float f, float max) { return f; }
                vec2 angelica_renamed_clamp(vec2 f) { return vec2(angelica_renamed_clamp(0.0, f.x, 1.0), angelica_renamed_clamp(0.0, f.y, 1.0)); }
                void main() { color = vec4(angelica_renamed_clamp(vec2(0.5)), 0.0, 1.0); }
                """),
            Arguments.of("name used as macro argument is left alone", """
                #version 330 core
                #define SAT(fn, x) fn(0.0, x, 1.0)
                out vec4 color;
                float clamp(float min, float f, float max) { return f; }
                void main() { color = vec4(SAT(clamp, 0.5)); }
                """, null),
            Arguments.of("name used in macro body is left alone", """
                #version 330 core
                #define FADE(x) fade(x)
                out vec4 color;
                float fade(float t) { return t * t; }
                void main() { color = vec4(FADE(0.5)); }
                """, null),
            Arguments.of("name shared with a variable is left alone", """
                #version 330 core
                out vec4 color;
                float fade(float t) { return t * t; }
                void main() { float fade = 2.0; color = vec4(fade); }
                """, null),
            Arguments.of("shader with only main has nothing to rename", "#version 330 core\nout vec4 color;\nvoid main() { color = vec4(1.0); }\n", null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void renames(String label, String source, String expected) {
        assertEquals(expected, GlslVulkanPreprocess.renameUserFunctions(source));
    }
}

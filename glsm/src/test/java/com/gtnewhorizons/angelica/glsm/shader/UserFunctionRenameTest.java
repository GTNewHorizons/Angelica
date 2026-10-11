package com.gtnewhorizons.angelica.glsm.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserFunctionRenameTest {

    @Test
    void definitionForwardDeclarationAndCallsAreRenamed() {
        final String out = GlslVulkanPreprocess.renameUserFunctions("""
            #version 330 core
            float fade(float t);
            out vec4 color;
            void main() { color = vec4(fade(0.5)); }
            float fade(float t) { return t * t; }
            """);
        assertEquals("""
            #version 330 core
            float angelica_renamed_fade(float t);
            out vec4 color;
            void main() { color = vec4(angelica_renamed_fade(0.5)); }
            float angelica_renamed_fade(float t) { return t * t; }
            """, out);
    }

    @Test
    void overloadsOfOneNameAreRenamedTogether() {
        final String out = GlslVulkanPreprocess.renameUserFunctions("""
            #version 330 core
            out vec4 color;
            float clamp(float min, float f, float max) { return f; }
            vec2 clamp(vec2 f) { return vec2(clamp(0.0, f.x, 1.0), clamp(0.0, f.y, 1.0)); }
            void main() { color = vec4(clamp(vec2(0.5)), 0.0, 1.0); }
            """);
        assertEquals("""
            #version 330 core
            out vec4 color;
            float angelica_renamed_clamp(float min, float f, float max) { return f; }
            vec2 angelica_renamed_clamp(vec2 f) { return vec2(angelica_renamed_clamp(0.0, f.x, 1.0), angelica_renamed_clamp(0.0, f.y, 1.0)); }
            void main() { color = vec4(angelica_renamed_clamp(vec2(0.5)), 0.0, 1.0); }
            """, out);
    }

    @Test
    void nameUsedAsMacroArgumentIsLeftAlone() {
        assertNull(GlslVulkanPreprocess.renameUserFunctions("""
            #version 330 core
            #define SAT(fn, x) fn(0.0, x, 1.0)
            out vec4 color;
            float clamp(float min, float f, float max) { return f; }
            void main() { color = vec4(SAT(clamp, 0.5)); }
            """));
    }

    @Test
    void nameUsedInMacroBodyIsLeftAlone() {
        assertNull(GlslVulkanPreprocess.renameUserFunctions("""
            #version 330 core
            #define FADE(x) fade(x)
            out vec4 color;
            float fade(float t) { return t * t; }
            void main() { color = vec4(FADE(0.5)); }
            """));
    }

    @Test
    void nameSharedWithVariableIsLeftAlone() {
        assertNull(GlslVulkanPreprocess.renameUserFunctions("""
            #version 330 core
            out vec4 color;
            float fade(float t) { return t * t; }
            void main() { float fade = 2.0; color = vec4(fade); }
            """));
    }

    @Test
    void shaderWithOnlyMainHasNothingToRename() {
        assertNull(GlslVulkanPreprocess.renameUserFunctions("#version 330 core\nout vec4 color;\nvoid main() { color = vec4(1.0); }\n"));
    }
}

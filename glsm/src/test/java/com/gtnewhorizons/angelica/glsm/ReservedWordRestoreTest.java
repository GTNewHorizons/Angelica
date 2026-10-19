package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservedWordRestoreTest {

    static Stream<Arguments> restores() {
        return Stream.of(
            Arguments.of("all three renames restore identically",
                "float angelica_renamed_sample = 1.0;\n"
                    + "uniform sampler2D angelica_renamed_sampler;\n"
                    + "vec4 angelica_renamed_texture = vec4(0.0);\n"
                    + "void main() {\n"
                    + "    vec4 c = angelica_renamed_texture + vec4(angelica_renamed_sample);\n"
                    + "}\n",
                new String[0], new String[0]),
            Arguments.of("new is reserved in every GLSL version and must stay renamed",
                "float angelica_renamed_new = 2.0;\n"
                    + "void main() { float x = angelica_renamed_new; }\n",
                new String[] { "angelica_renamed_new" }, new String[0]),
            Arguments.of("suffixed rename must strip only the prefix",
                "uniform sampler2D tex;\n"
                    + "void main() {\n"
                    + "    vec4 c = angelica_renamed_texture2D(tex, vec2(0.5));\n"
                    + "}\n",
                new String[] { "texture2D" }, new String[] { "angelica_renamed_" }),
            Arguments.of("untouched source prints identically",
                "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() { fragColor = vec4(1.0); }\n",
                new String[0], new String[0]));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void restores(String label, String renamedSource, String[] present, String[] absent) {
        final GLSLParser.Translation_unitContext treeA = GlslTransformUtils.parseFullQuiet(renamedSource);
        final String legacy = GlslTransformUtils.restoreReservedWords(GlslTransformUtils.getFormattedShader(treeA, ""));

        final GLSLParser.Translation_unitContext treeB = GlslTransformUtils.parseFullQuiet(renamedSource);
        GlslTransformUtils.restoreReservedWordsInTree(treeB);
        final String inTree = GlslTransformUtils.getFormattedShader(treeB, "");

        assertEquals(legacy, inTree, "in-tree restore diverged from string restore");
        for (String needle : present) assertTrue(inTree.contains(needle), needle);
        for (String needle : absent) assertFalse(inTree.contains(needle), needle);
    }
}

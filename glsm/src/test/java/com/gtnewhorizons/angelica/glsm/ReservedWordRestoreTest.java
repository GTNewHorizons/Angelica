package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.taumc.glsl.grammar.GLSLParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservedWordRestoreTest {

    private static void assertTreeRestoreMatchesStringRestore(String label, String renamedSource) {
        final GLSLParser.Translation_unitContext treeA = GlslTransformUtils.parseFullQuiet(renamedSource);
        final String legacy = GlslTransformUtils.restoreReservedWords(GlslTransformUtils.getFormattedShader(treeA, ""));

        final GLSLParser.Translation_unitContext treeB = GlslTransformUtils.parseFullQuiet(renamedSource);
        GlslTransformUtils.restoreReservedWordsInTree(treeB);
        final String inTree = GlslTransformUtils.getFormattedShader(treeB, "");

        assertEquals(legacy, inTree, "in-tree restore diverged from string restore for " + label);
    }

    @Test
    void allThreeRenamesRestoreIdentically() {
        final String src = "float angelica_renamed_sample = 1.0;\n"
            + "uniform sampler2D angelica_renamed_sampler;\n"
            + "vec4 angelica_renamed_texture = vec4(0.0);\n"
            + "void main() {\n"
            + "    vec4 c = angelica_renamed_texture + vec4(angelica_renamed_sample);\n"
            + "}\n";
        assertTreeRestoreMatchesStringRestore("all-three", src);
    }

    @Test
    void renamedNewStaysRenamed() {
        final String src = "float angelica_renamed_new = 2.0;\n"
            + "void main() { float x = angelica_renamed_new; }\n";
        assertTreeRestoreMatchesStringRestore("new-identifier", src);

        final GLSLParser.Translation_unitContext tree = GlslTransformUtils.parseFullQuiet(src);
        GlslTransformUtils.restoreReservedWordsInTree(tree);
        final String out = GlslTransformUtils.getFormattedShader(tree, "");
        assertTrue(out.contains("angelica_renamed_new"), "new is reserved in every GLSL version and must stay renamed");
    }

    @Test
    void renamedTextureFunctionCallRestores() {
        final String src = "uniform sampler2D tex;\n"
            + "void main() {\n"
            + "    vec4 c = angelica_renamed_texture2D(tex, vec2(0.5));\n"
            + "}\n";
        assertTreeRestoreMatchesStringRestore("texture2D-call", src);

        final GLSLParser.Translation_unitContext tree = GlslTransformUtils.parseFullQuiet(src);
        GlslTransformUtils.restoreReservedWordsInTree(tree);
        final String out = GlslTransformUtils.getFormattedShader(tree, "");
        assertTrue(out.contains("texture2D"), "suffixed rename must strip only the prefix");
        assertFalse(out.contains("angelica_renamed_"), "no renamed remnants expected");
    }

    @Test
    void untouchedSourcePrintsIdentically() {
        final String src = "layout(location = 0) out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(1.0); }\n";
        assertTreeRestoreMatchesStringRestore("no-renames", src);
    }
}

package net.coderbot.iris.pipeline.transform;

import org.junit.jupiter.api.Test;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.ShaderPrinter;
import org.taumc.glsl.Transformer;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerAliasDeduplicatorTest {

    private static Transformer parse(String src) {
        return new Transformer(ShaderParser.parseShader(src).full());
    }

    private static String format(Transformer t) {
        StringBuilder sb = new StringBuilder();
        t.mutateTree(tree -> sb.append(ShaderPrinter.getFormattedShader(tree)));
        return sb.toString();
    }

    @Test
    void bothDeclared_aliasDropped_canonicalKept() {
        String src =
            "#version 330 core\n" +
            "uniform sampler2D tex;\n" +
            "uniform sampler2D textureAtlas;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texture(textureAtlas, vec2(0.5)) + texture(tex, vec2(0.5));\n" +
            "}\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerAliasDeduplicator.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("uniform sampler2D tex"), "canonical declaration must remain");
        assertFalse(out.contains("uniform sampler2D textureAtlas"), "alias declaration must be removed: " + out);
        assertFalse(out.contains("textureAtlas"), "all references to alias must be rewritten: " + out);
        assertEquals(2, occurrences(out, "texture(tex"), "both references should now read from tex");
    }

    @Test
    void onlyAliasDeclared_renamedToCanonical() {
        String src =
            "#version 330 core\n" +
            "uniform sampler2D textureAtlas;\n" +
            "out vec4 fragColor;\n" +
            "void main() { fragColor = texture(textureAtlas, vec2(0.5)); }\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerAliasDeduplicator.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("uniform sampler2D tex"), "alias should be promoted to canonical: " + out);
        assertFalse(out.contains("textureAtlas"), "no alias should remain: " + out);
    }

    @Test
    void crossStage_consistentCanonical() {
        String vs =
            "#version 330 core\n" +
            "uniform sampler2D textureAtlas;\n" +
            "void main() { gl_Position = vec4(texture(textureAtlas, vec2(0.0)).xyz, 1.0); }\n";
        String fs =
            "#version 330 core\n" +
            "uniform sampler2D tex;\n" +
            "uniform sampler2D textureAtlas;\n" +
            "out vec4 fragColor;\n" +
            "void main() { fragColor = texture(textureAtlas, vec2(0.5)) + texture(tex, vec2(0.5)); }\n";

        Transformer vt = parse(vs);
        Transformer ft = parse(fs);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.VERTEX, vt);
        trees.put(PatchShaderType.FRAGMENT, ft);
        SamplerAliasDeduplicator.transformGrouped(trees, null);

        String voutS = format(vt);
        String foutS = format(ft);

        assertTrue(voutS.contains("uniform sampler2D tex"), "vertex must declare tex: " + voutS);
        assertFalse(voutS.contains("textureAtlas"), "vertex must not reference alias: " + voutS);
        assertTrue(foutS.contains("uniform sampler2D tex"), "fragment must declare tex: " + foutS);
        assertFalse(foutS.contains("uniform sampler2D textureAtlas"), "fragment alias decl must be gone: " + foutS);
        assertFalse(foutS.contains("textureAtlas"), "fragment must not reference alias: " + foutS);
    }

    @Test
    void noMatch_unchanged() {
        String src =
            "#version 330 core\n" +
            "uniform sampler2D somethingElse;\n" +
            "out vec4 fragColor;\n" +
            "void main() { fragColor = texture(somethingElse, vec2(0.5)); }\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerAliasDeduplicator.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("uniform sampler2D somethingElse"), "untouched uniform must survive: " + out);
        assertFalse(out.contains("uniform sampler2D tex"), "no spurious tex declaration: " + out);
    }

    @Test
    void depthAliasGroup_collapsesToDepthtex0() {
        String src =
            "#version 330 core\n" +
            "uniform sampler2D depthtex0;\n" +
            "uniform sampler2D gdepthtex;\n" +
            "out vec4 fragColor;\n" +
            "void main() { fragColor = texture(gdepthtex, vec2(0.5)) + texture(depthtex0, vec2(0.5)); }\n";

        Transformer t = parse(src);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerAliasDeduplicator.transformGrouped(trees, null);

        String out = format(t);
        assertTrue(out.contains("uniform sampler2D depthtex0"));
        assertFalse(out.contains("gdepthtex"), out);
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) != -1) { n++; i += needle.length(); }
        return n;
    }
}

package net.coderbot.iris.pipeline.transform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.taumc.glsl.Transformer;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import static net.coderbot.iris.pipeline.transform.TransformTestSupport.format;
import static net.coderbot.iris.pipeline.transform.TransformTestSupport.occurrences;
import static net.coderbot.iris.pipeline.transform.TransformTestSupport.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerAliasDeduplicatorTest {

    private static String transformFragment(String src) {
        final Transformer t = parse(src);
        final Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.FRAGMENT, t);
        SamplerAliasDeduplicator.transformGrouped(trees, null);
        return format(t);
    }

    static Stream<Arguments> presentAbsentCases() {
        return Stream.of(
            Arguments.of("only alias declared",
                "#version 330 core\n" +
                "uniform sampler2D gtexture;\n" +
                "out vec4 fragColor;\n" +
                "void main() { fragColor = texture(gtexture, vec2(0.5)); }\n",
                "uniform sampler2D tex", "gtexture"),
            Arguments.of("no match",
                "#version 330 core\n" +
                "uniform sampler2D somethingElse;\n" +
                "out vec4 fragColor;\n" +
                "void main() { fragColor = texture(somethingElse, vec2(0.5)); }\n",
                "uniform sampler2D somethingElse", "uniform sampler2D tex")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presentAbsentCases")
    void presentAndAbsent(String label, String src, String present, String absent) {
        String out = transformFragment(src);
        assertTrue(out.contains(present), out);
        assertFalse(out.contains(absent), out);
    }

    static Stream<Arguments> bothDeclaredCases() {
        return Stream.of(
            Arguments.of("tex group", "tex", "gtexture"),
            Arguments.of("depth group", "depthtex0", "gdepthtex")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bothDeclaredCases")
    void bothDeclared_aliasDropped_canonicalKept(String label, String canonical, String alias) {
        String src =
            "#version 330 core\n" +
            "uniform sampler2D " + canonical + ";\n" +
            "uniform sampler2D " + alias + ";\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texture(" + alias + ", vec2(0.5)) + texture(" + canonical + ", vec2(0.5));\n" +
            "}\n";

        String out = transformFragment(src);
        assertTrue(out.contains("uniform sampler2D " + canonical), "canonical declaration must remain");
        assertFalse(out.contains("uniform sampler2D " + alias), "alias declaration must be removed: " + out);
        assertFalse(out.contains(alias), "all references to alias must be rewritten: " + out);
        assertEquals(2, occurrences(out, "texture(" + canonical), "both references should now read from " + canonical);
    }

    @Test
    void crossStage_consistentCanonical() {
        String vs =
            "#version 330 core\n" +
            "uniform sampler2D gtexture;\n" +
            "void main() { gl_Position = vec4(texture(gtexture, vec2(0.0)).xyz, 1.0); }\n";
        String fs =
            "#version 330 core\n" +
            "uniform sampler2D tex;\n" +
            "uniform sampler2D gtexture;\n" +
            "out vec4 fragColor;\n" +
            "void main() { fragColor = texture(gtexture, vec2(0.5)) + texture(tex, vec2(0.5)); }\n";

        Transformer vt = parse(vs);
        Transformer ft = parse(fs);
        Map<PatchShaderType, Transformer> trees = new EnumMap<>(PatchShaderType.class);
        trees.put(PatchShaderType.VERTEX, vt);
        trees.put(PatchShaderType.FRAGMENT, ft);
        SamplerAliasDeduplicator.transformGrouped(trees, null);

        String voutS = format(vt);
        String foutS = format(ft);

        assertTrue(voutS.contains("uniform sampler2D tex"), "vertex must declare tex: " + voutS);
        assertFalse(voutS.contains("gtexture"), "vertex must not reference alias: " + voutS);
        assertTrue(foutS.contains("uniform sampler2D tex"), "fragment must declare tex: " + foutS);
        assertFalse(foutS.contains("uniform sampler2D gtexture"), "fragment alias decl must be gone: " + foutS);
        assertFalse(foutS.contains("gtexture"), "fragment must not reference alias: " + foutS);
    }

    @Test
    void packCustomSamplerIsNotAnAlias() {
        String src =
            "#version 330 core\n" +
            "uniform sampler2D tex;\n" +
            "uniform sampler2D textureAtlas;\n" +
            "out vec4 fragColor;\n" +
            "void main() { fragColor = texture(textureAtlas, vec2(0.5)) + texture(tex, vec2(0.5)); }\n";

        String out = transformFragment(src);
        assertTrue(out.contains("uniform sampler2D textureAtlas"), "a pack-defined sampler keeps its own binding: " + out);
        assertTrue(out.contains("texture(textureAtlas"), "its reads must not be redirected to tex: " + out);
    }
}

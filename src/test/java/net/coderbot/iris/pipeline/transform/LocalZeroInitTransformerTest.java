package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.ShaderPrinter;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalZeroInitTransformerTest {

    private static int savedMaxGlsl;

    @BeforeAll
    static void initShaderTransformer() {
        savedMaxGlsl = Reflect.getStatic(RenderSystem.class, "maxGlslVersion");
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", 460);
        ShaderTransformer.clearCache();
        ShaderTransformer.init();
    }

    @AfterAll
    static void restoreCapabilities() {
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", savedMaxGlsl);
        ShaderTransformer.clearCache();
        TransformPatcher.clearCache();
        ShaderTransformer.init();
    }

    private static final Set<String> NUMERIC = Set.of(
        "float", "int", "uint",
        "vec2", "vec3", "vec4", "ivec2", "ivec3", "ivec4", "uvec2", "uvec3", "uvec4",
        "mat2", "mat3", "mat4",
        "mat2x2", "mat2x3", "mat2x4", "mat3x2", "mat3x3", "mat3x4", "mat4x2", "mat4x3", "mat4x4");

    private record Declarator(String type, String name) {}

    private static String run(String src) {
        final Transformer t = new Transformer(ShaderParser.parseShader(src).full());
        LocalZeroInitTransformer.transform(t);
        final StringBuilder sb = new StringBuilder();
        t.mutateTree(tree -> sb.append(ShaderPrinter.getFormattedShader(tree)));
        return sb.toString();
    }

    private static List<Declarator> uninitialized(String src) {
        final ParseTree tree = ShaderParser.parseShader(src).full();
        final List<Declarator> out = new ArrayList<>();
        for (GLSLParser.Declaration_statementContext stmt : GlslAstHelpers.collectAll(tree, GLSLParser.Declaration_statementContext.class)) {
            final GLSLParser.DeclarationContext declaration = stmt.declaration();
            if (declaration == null) continue;
            final GLSLParser.Init_declarator_listContext list = declaration.init_declarator_list();
            if (list == null) continue;
            final GLSLParser.Single_declarationContext single = list.single_declaration();
            if (single == null) continue;
            final String type = single.fully_specified_type() == null ? "" : single.fully_specified_type().getText();

            final List<GLSLParser.Typeless_declarationContext> declarators = new ArrayList<>();
            declarators.add(single.typeless_declaration());
            declarators.addAll(list.typeless_declaration());
            for (GLSLParser.Typeless_declarationContext d : declarators) {
                if (d == null || d.initializer() != null) continue;
                out.add(new Declarator(type, d.IDENTIFIER().getText()));
            }
        }
        return out;
    }

    private static boolean isUninitialized(String src, String name) {
        return uninitialized(src).stream().anyMatch(d -> d.name().equals(name));
    }

    @Test
    void accumulatorGetsZeroInitializer() {
        final String src =
            "#version 460 core\n" +
            "out vec4 fragColor;\n" +
            "vec4 accumulate() {\n" +
            "    vec4 lightVolume;\n" +
            "    for (int i = 0; i < 8; i++) lightVolume += vec4(float(i));\n" +
            "    return lightVolume;\n" +
            "}\n" +
            "void main() { fragColor = accumulate(); }\n";

        assertTrue(isUninitialized(src, "lightVolume"), "fixture must start uninitialized");
        final String out = run(src);
        assertTrue(uninitialized(out).isEmpty(), out);
    }

    @Test
    void existingInitializersAreLeftAlone() {
        final String src =
            "#version 460 core\n" +
            "void main() {\n" +
            "    vec3 a = vec3(1.0, 2.0, 3.0);\n" +
            "    float b = 4.0;\n" +
            "}\n";

        final String out = run(src);
        assertTrue(out.contains("vec3(1.0, 2.0, 3.0)"), out);
        assertTrue(out.contains("4.0"), out);
        assertTrue(uninitialized(out).isEmpty(), out);
    }

    @Test
    void multiDeclaratorListInitializesOnlyBareNames() {
        final String src =
            "#version 460 core\n" +
            "void main() {\n" +
            "    vec3 a, b = vec3(1.0), c;\n" +
            "    a += b; c += b;\n" +
            "}\n";

        final String out = run(src);
        assertTrue(uninitialized(out).isEmpty(), out);
        assertTrue(out.contains("vec3(1.0)"), "b must keep its own initializer: " + out);
    }

    @Test
    void arrayDeclarationsAreSkippedInBothSpellings() {
        final String src =
            "#version 460 core\n" +
            "void main() {\n" +
            "    float a[3];\n" +
            "    float[3] b;\n" +
            "    vec3 c[2];\n" +
            "}\n";

        final String out = run(src);
        assertTrue(isUninitialized(out, "a"), out);
        assertTrue(isUninitialized(out, "b"), out);
        assertTrue(isUninitialized(out, "c"), out);
    }

    @Test
    void structAndOpaqueLocalsAreSkipped() {
        final String src =
            "#version 460 core\n" +
            "struct Material { vec3 albedo; float rough; };\n" +
            "void main() {\n" +
            "    Material m;\n" +
            "    m.rough = 1.0;\n" +
            "}\n";

        final String out = run(src);
        assertTrue(isUninitialized(out, "m"), "struct locals have no scalar zero constructor: " + out);
    }

    @Test
    void qualifiedDeclarationsAreSkipped() {
        final String src =
            "#version 460 core\n" +
            "void main() {\n" +
            "    const float k = 2.0;\n" +
            "    precise float p;\n" +
            "}\n";

        final String out = run(src);
        assertTrue(isUninitialized(out, "p"), out);
        assertTrue(out.contains("2.0"), "const initializer must survive: " + out);
    }

    @Test
    void boolAndDoubleAreSkipped() {
        final String src =
            "#version 460 core\n" +
            "void main() {\n" +
            "    bool flag;\n" +
            "    double d;\n" +
            "}\n";

        final String out = run(src);
        assertTrue(isUninitialized(out, "flag"), out);
        assertTrue(isUninitialized(out, "d"), out);
    }

    private static int numericBareCount(String src) {
        return (int) uninitialized(src).stream().filter(d -> NUMERIC.contains(d.type())).count();
    }

    @Test
    void patcherEmittedShapesLeaveNoBareNumericLocals() {
        TransformPatcher.clearCache();
        final Map<PatchShaderType, String> patched = TransformPatcher.patchComposite(PACK_SHAPED_VSH, null, PACK_SHAPED_FSH);

        for (PatchShaderType stage : List.of(PatchShaderType.VERTEX, PatchShaderType.FRAGMENT)) {
            final String source = patched.get(stage);
            assertNotNull(source, stage + " produced no output");
            assertTrue(numericBareCount(source) > 0, stage + ": fixture must reach the transformer with bare numeric locals\n\n" + source);

            final List<Declarator> leftover = uninitialized(run(source)).stream()
                .filter(d -> NUMERIC.contains(d.type()))
                .toList();
            assertEquals(List.of(), leftover, stage + " still has bare numeric locals\n\n" + run(source));
        }
    }

    private static final String PACK_SHAPED_VSH = String.join("\n",
        "#version 330 core",
        "in vec4 iris_Vertex;",
        "in vec4 iris_Color;",
        "in vec2 mc_midTexCoord;",
        "out vec4 v_Color;",
        "out vec3 v_Accum;",
        "void main() {",
        "    vec4 accum;",
        "    mat3 tbn;",
        "    float depth;",
        "    ivec2 cell;",
        "    accum += iris_Color;",
        "    tbn[0] += vec3(mc_midTexCoord, 0.0);",
        "    depth += iris_Vertex.z;",
        "    cell += ivec2(1);",
        "    v_Color = accum + vec4(float(cell.x));",
        "    v_Accum = tbn[0] * depth;",
        "    gl_Position = iris_Vertex;",
        "}",
        "");

    private static final String PACK_SHAPED_FSH = String.join("\n",
        "#version 330 core",
        "in vec4 v_Color;",
        "in vec3 v_Accum;",
        "uniform sampler2D tex;",
        "void main() {",
        "    vec3 lighting;",
        "    vec2 uv;",
        "    uint bits;",
        "    lighting += v_Accum;",
        "    uv += v_Color.xy;",
        "    bits += 1u;",
        "    gl_FragColor = vec4(lighting + texture(tex, uv).rgb, float(bits));",
        "}",
        "");
}

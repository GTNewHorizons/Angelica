package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryUtil.memFree;

class DerivativeHoistingTest {

    private static String hoist(String source) {
        final List<Edit> edits = new ArrayList<>();
        DerivativeHoisting.collectEdits(GlslTransformUtils.parseFullQuiet(source), source, edits);
        return GlslVulkanPreprocess.applyEdits(source, edits);
    }

    private static void assertCompiles(String source) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, Shaderc.shaderc_fragment_shader, "derivatives.frag", SpirvCompiler.Options.vulkanForced460Core(), false);
        assertNotNull(r.spirv(), () -> "compile failed: " + r.error() + "\n" + source);
        memFree(r.spirv());
    }

    @Test
    void directionalBlocklightDerivativesLeaveTheLightmapBranch() {
        final String out = hoist("""
            #version 420 core
            in vec2 lmCoord;
            in vec3 viewIn;
            out vec4 color;
            float light(vec3 viewPos) {
                vec2 oldLightmap = lmCoord;
                float result = oldLightmap.x;
                if (oldLightmap.x > 0.035) {
                    vec2 dFdBlock = vec2(dFdx(oldLightmap.x), dFdy(oldLightmap.x));
                    vec3 dir = vec3(0.0);
                    if (length(dFdBlock) < 1e-6) {
                        dir = vec3(1.0);
                    } else {
                        dir = dFdx(viewPos) * dFdBlock.x + dFdy(viewPos) * dFdBlock.y;
                    }
                    result *= dot(normalize(dir), vec3(0.0, 1.0, 0.0));
                }
                return result;
            }
            void main() {
                color = vec4(light(viewIn));
            }
            """);

        final int branch = out.indexOf("if (oldLightmap.x > 0.035)");
        assertTrue(branch > 0, out);
        assertFalse(out.substring(branch).contains("dFdx("), out);
        assertFalse(out.substring(branch).contains("dFdy("), out);
        final String hoisted = out.substring(out.indexOf("float result"), branch);
        assertTrue(hoisted.contains("float angelica_derivative_0 = dFdx(oldLightmap.x);"), out);
        assertTrue(hoisted.contains("float angelica_derivative_1 = dFdy(oldLightmap.x);"), out);
        assertTrue(hoisted.contains("vec3 angelica_derivative_2 = dFdx(viewPos);"), out);
        assertTrue(hoisted.contains("vec3 angelica_derivative_3 = dFdy(viewPos);"), out);
        assertCompiles(out);
    }

    @Test
    void variableWrittenInsideTheBranchStaysPut() {
        final String source = """
            #version 420 core
            in vec2 uv;
            out vec4 color;
            void bump(inout vec2 p) { p += 1.0; }
            void main() {
                vec2 a = uv;
                vec2 b = uv;
                if (uv.x > 0.5) {
                    a *= 2.0;
                    bump(b);
                    color = vec4(dFdx(a), dFdy(b));
                }
            }
            """;
        assertEquals(source, hoist(source));
    }

    @Test
    void writesHiddenInsideDirectivesBlockTheMove() {
        final String source = """
            #version 420 core
            in vec2 uv;
            out vec4 color;
            void main() {
                vec2 p = uv;
                if (true) {
            #if 1
                    p *= 2.0;
            #endif
                    color = vec4(dFdx(p), 0.0, 1.0);
                }
            }
            """;
        assertEquals(source, hoist(source));
    }

    @Test
    void parenthesizedBuiltinOutArgumentCountsAsAWrite() {
        final String source = """
            #version 420 core
            in vec2 uv;
            out vec4 color;
            void main() {
                vec2 p = uv;
                if (true) {
                    modf(uv * 2.0, (p));
                    color = vec4(dFdx(p), 0.0, 1.0);
                }
            }
            """;
        assertEquals(source, hoist(source));
    }

    @Test
    void globalsStayOnlyWhenACalleeWritesThem() {
        final String out = hoist("""
            #version 420 core
            in vec4 colorIn;
            out vec4 color;
            vec4 glColor = colorIn;
            vec2 nudged = vec2(0.0);
            float square(float x) { return x * x; }
            void nudge() { nudged += 1.0; }
            void touch() { nudge(); }
            void main() {
                if (glColor.a > 0.5) {
                    touch();
                    color = vec4(dFdx(glColor.a) + square(1.0), dFdy(nudged), 1.0);
                }
            }
            """);

        final int branch = out.indexOf("if (glColor.a > 0.5)");
        assertTrue(out.substring(0, branch).contains("float angelica_derivative_0 = dFdx(glColor.a);"), out);
        assertTrue(out.substring(branch).contains("dFdy(nudged)"), out);
        assertCompiles(out);
    }

    @Test
    void loopsAreNotCrossed() {
        final String out = hoist("""
            #version 420 core
            in vec2 uv;
            out vec4 color;
            void main() {
                color = vec4(0.0);
                if (uv.y > 0.5) {
                    for (int i = 0; i < 4; i++) {
                        vec2 p = uv * float(i);
                        if (p.x > 1.0) {
                            color.xy += dFdx(p);
                        }
                    }
                }
            }
            """);

        final int loop = out.indexOf("for (");
        final int inner = out.indexOf("if (p.x > 1.0)");
        final int hoisted = out.indexOf("vec2 angelica_derivative_0 = dFdx(p);");
        assertTrue(loop < hoisted && hoisted < inner, out);
        assertCompiles(out);
    }
}

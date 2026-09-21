package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.shader.ShaderType;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gbuffer_overrides.matching.ProgramTable;
import net.coderbot.iris.gbuffer_overrides.matching.RenderCondition;
import net.coderbot.iris.gl.shader.GlShader;
import net.coderbot.iris.gl.shader.ProgramCreator;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.uniform.Uniform;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline.Pass;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.ShaderTransformer;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL15;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class IrisInstancingGLTest {

    private static final InputAvailability TEX_LM = InputAvailability.of(true, true);

    private static final String CUBE_VERTEX = """
        #version 120
        attribute vec4 mc_Entity;
        attribute vec4 at_tangent;
        attribute vec4 mc_midTexCoord;
        varying vec2 texcoord;
        varying vec2 lmcoord;
        varying vec4 color;
        varying float mats;
        varying vec3 nrm;
        varying vec3 tang;
        varying vec2 midtex;
        uniform int entityId;
        uniform int blockEntityId;
        uniform vec4 entityColor;
        void main() {
            texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
            lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
            color = gl_Color + entityColor + vec4(float(entityId + blockEntityId));
            mats = mc_Entity.x;
            nrm = normalize(gl_NormalMatrix * gl_Normal);
            tang = normalize(gl_NormalMatrix * at_tangent.xyz) * at_tangent.w;
            midtex = mc_midTexCoord.st;
            gl_Position = ftransform();
        }
        """;

    private static final String CUBE_FRAGMENT = """
        #version 120
        varying vec2 texcoord;
        varying vec4 color;
        uniform sampler2D tex;
        uniform int entityId;
        uniform vec4 entityColor;
        void main() {
            gl_FragData[0] = texture2D(tex, texcoord) * color * entityColor + vec4(float(entityId));
        }
        """;

    private static final String TEMPLATE_VERTEX = """
        #version 120
        in ivec4 iris_Entity;
        out vec4 entityColor;
        uniform int entityId;
        varying float mats;
        void main() {
            entityColor = vec4(1.0);
            mats = float(entityId);
            gl_Position = ftransform();
        }
        """;

    private static final String TEMPLATE_GEOMETRY = """
        #version 150
        layout(triangles) in;
        layout(triangle_strip, max_vertices = 3) out;
        void main() {
            for (int i = 0; i < 3; i++) {
                gl_Position = gl_in[i].gl_Position;
                EmitVertex();
            }
            EndPrimitive();
        }
        """;

    private static final String TEMPLATE_FRAGMENT = """
        #version 120
        uniform int entityId;
        uniform vec4 entityColor;
        void main() {
            gl_FragData[0] = entityColor + vec4(float(entityId));
        }
        """;

    private static final String PARTICLE_VERTEX = """
        #version 120
        attribute vec4 mc_Entity;
        attribute vec2 mc_midTexCoord;
        attribute vec4 at_tangent;
        varying vec2 texcoord;
        varying vec2 lmcoord;
        varying vec4 color;
        varying float mats;
        varying vec2 midtex;
        varying vec3 tang;
        varying vec3 wpos;
        void main() {
            texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
            lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
            color = gl_Color;
            mats = mc_Entity.x;
            midtex = mc_midTexCoord.st;
            tang = at_tangent.xyz * at_tangent.w;
            wpos = (gl_ModelViewMatrixInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
            gl_Position = ftransform();
        }
        """;

    private static final String PARTICLE_FRAGMENT = """
        #version 120
        varying vec2 texcoord;
        varying vec2 midtex;
        varying vec4 color;
        varying vec3 tang;
        varying vec3 wpos;
        uniform sampler2D tex;
        void main() {
            gl_FragData[0] = texture2D(tex, texcoord) * color * vec4(midtex, 1.0, 1.0) + vec4(tang + wpos, 0.0);
        }
        """;

    private static int previousMaxGlslVersion;

    @BeforeAll
    static void pinGlslVersion() {
        previousMaxGlslVersion = RenderSystem.getMaxGlslVersion();
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", 330);
        ShaderTransformer.init();
    }

    @AfterAll
    static void restoreGlslVersion() {
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", previousMaxGlslVersion);
        ShaderTransformer.init();
    }

    @BeforeEach
    void setUp() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
        GLStateManager.disableBlend();
    }

    @AfterEach
    void tearDown() {
        GLSMHooks.pendingProgramSelection = null;
        Reflect.setStatic(BatchEligibility.class, "depth", 0);
        Reflect.setStatic(BatchEligibility.class, "allowed", false);
        Reflect.setStatic(GLStateManager.class, "foreignDrawDepth", 0);
    }

    private static DeferredWorldRenderingPipeline newPipeline(WorldRenderingPhase phase, boolean isMainBound) {
        final DeferredWorldRenderingPipeline pipeline = Reflect.allocate(DeferredWorldRenderingPipeline.class);
        Reflect.set(pipeline, "table", new ProgramTable<Pass>((condition, availability) -> null));
        Reflect.set(pipeline, "isRenderingWorld", true);
        Reflect.set(pipeline, "isMainBound", isMainBound);
        Reflect.set(pipeline, "phase", phase);
        Reflect.set(pipeline, "pushedPhase", WorldRenderingPhase.NONE);
        Reflect.set(pipeline, "inputs", InputAvailability.of(false, false));
        return pipeline;
    }

    private static RenderCondition currentCondition(DeferredWorldRenderingPipeline pipeline) {
        return Reflect.get(pipeline, "currentCondition");
    }

    @Test
    void settersDeferInsideASafeBatchWindowAndResolveWhenItCloses() {
        final DeferredWorldRenderingPipeline pipeline = newPipeline(WorldRenderingPhase.ENTITIES, true);
        final long dc = GLStateManager.drawCalls;

        BatchEligibility.begin(BatchEligibility.SAFE, dc);
        BatchEligibility.begin(BatchEligibility.SAFE, dc);
        pipeline.setInputs(InputAvailability.of(true, false));
        assertSame(pipeline, GLSMHooks.pendingProgramSelection, "setInputs must defer inside a SAFE window");
        assertNull(currentCondition(pipeline), "a deferred setter must not match yet");

        BatchEligibility.end(BatchEligibility.SAFE, dc);
        assertSame(pipeline, GLSMHooks.pendingProgramSelection, "a nested end must not resolve the pending match");

        BatchEligibility.end(BatchEligibility.SAFE, dc);
        assertNull(GLSMHooks.pendingProgramSelection, "the outermost end must resolve the pending match");
        assertEquals(RenderCondition.ENTITIES, currentCondition(pipeline));
    }

    @Test
    void onModProgramOverrideDropsThePendingMarker() {
        final DeferredWorldRenderingPipeline pipeline = newPipeline(WorldRenderingPhase.ENTITIES, true);
        GLSMHooks.pendingProgramSelection = pipeline;

        pipeline.onModProgramOverride();

        assertNull(GLSMHooks.pendingProgramSelection, "a mod program override must clobber the pending match");
    }

    @Test
    void foreignDrawOnlyDeferralResolvesAtEndForeignDraw() {
        final DeferredWorldRenderingPipeline pipeline = newPipeline(WorldRenderingPhase.ENTITIES, true);

        GLStateManager.beginForeignDraw();
        pipeline.setInputs(InputAvailability.of(true, false));
        assertSame(pipeline, GLSMHooks.pendingProgramSelection, "a foreign draw alone must defer the match");

        GLStateManager.endForeignDraw();
        assertNull(GLSMHooks.pendingProgramSelection, "endForeignDraw at depth zero must resolve the match");
    }

    @Test
    void onEntityRenderBoundaryResolvesBeforeCheckingForAStaleCondition() {
        final DeferredWorldRenderingPipeline pipeline = newPipeline(WorldRenderingPhase.ENTITIES, true);
        Reflect.set(pipeline, "currentCondition", RenderCondition.ENTITIES);

        final long dc = GLStateManager.drawCalls;
        BatchEligibility.begin(BatchEligibility.SAFE, dc);
        pipeline.setInputs(InputAvailability.of(true, true));
        assertSame(pipeline, GLSMHooks.pendingProgramSelection);

        pipeline.onEntityRenderBoundary();

        assertNull(GLSMHooks.pendingProgramSelection, "onEntityRenderBoundary must resolve before the same-enum staleness check skips it");

        BatchEligibility.end(BatchEligibility.SAFE, dc);
    }

    @Test
    void rebindCurrentPassResolvesBeforeItsOwnNullPassFallback() {
        final DeferredWorldRenderingPipeline pipeline = newPipeline(WorldRenderingPhase.ENTITIES, true);

        final int program = GLStateManager.glCreateProgram();
        GLStateManager.glUseProgram(program);
        try {
            final long dc = GLStateManager.drawCalls;
            BatchEligibility.begin(BatchEligibility.SAFE, dc);
            pipeline.setInputs(InputAvailability.of(true, true));
            assertSame(pipeline, GLSMHooks.pendingProgramSelection);

            pipeline.rebindCurrentPass();

            assertNull(GLSMHooks.pendingProgramSelection, "rebindCurrentPass must resolve the pending match; its null-pass fallback needs no bound program");

            BatchEligibility.end(BatchEligibility.SAFE, dc);
        } finally {
            GLStateManager.glUseProgram(0);
            GLStateManager.glDeleteProgram(program);
        }
    }

    @Test
    void cubeVariantCompiles() {
        final Map<PatchShaderType, String> result = TransformPatcher.patchAttributesInstanced(CUBE_VERTEX, null, null, null, CUBE_FRAGMENT, TEX_LM, false, Instancing.CUBE);
        assertNotNull(result);
        final String v = result.get(PatchShaderType.VERTEX);
        final String f = result.get(PatchShaderType.FRAGMENT);
        assertNotNull(v);
        assertNotNull(f);
        assertTrue(v.startsWith("#version 330"), () -> "expected a 3.3 core vertex shader:\n" + v);
        compile(GL20.GL_VERTEX_SHADER, v);
        compile(GL20.GL_FRAGMENT_SHADER, f);
    }

    @Test
    void particleVariantCompiles() {
        final Map<PatchShaderType, String> result = TransformPatcher.patchAttributesInstanced(PARTICLE_VERTEX, null, null, null, PARTICLE_FRAGMENT, TEX_LM, false, Instancing.PARTICLE);
        assertNotNull(result);
        final String v = result.get(PatchShaderType.VERTEX);
        assertNotNull(v);
        assertTrue(v.startsWith("#version 330"), () -> "expected a 3.3 core vertex shader:\n" + v);
        link("particleVariant", result);
    }

    @Test
    void templateVariantCompilesAndLinksWithGeometryStage() {
        final Map<PatchShaderType, String> result = TransformPatcher.patchAttributesInstanced(TEMPLATE_VERTEX, TEMPLATE_GEOMETRY, null, null, TEMPLATE_FRAGMENT, TEX_LM, false, Instancing.TEMPLATE);
        assertNotNull(result);
        assertNotNull(result.get(PatchShaderType.GEOMETRY));
        link("templateVariant", result);
    }

    private static void link(String name, Map<PatchShaderType, String> result) {
        GLStateManager.glDeleteProgram(linkProgram(name, result));
    }

    @ParameterizedTest
    @EnumSource(value = Instancing.class, names = { "NONE", "TEMPLATE", "CUBE" })
    void freshEntityTintPreservesColorBeforeAndAfterAHurtScope(Instancing kind) {
        final CapturedRenderingState state = Reflect.construct(CapturedRenderingState.class);
        final String vertexSource = """
            #version 120
            void main() {
                gl_Position = vec4(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0, 0.0, 1.0);
            }
            """;
        final String fragmentSource = """
            #version 120
            uniform vec4 entityColor;
            void main() {
                gl_FragData[0] = vec4(mix(vec3(0, 0, 1), entityColor.rgb, entityColor.a), 1);
            }
            """;
        final int program = linkProgram("entityTint", kind == Instancing.NONE
            ? TransformPatcher.patchAttributes(vertexSource, null, fragmentSource, TEX_LM)
            : TransformPatcher.patchAttributesInstanced(vertexSource, null, null, null, fragmentSource, TEX_LM, false, kind));
        final int vao = GLStateManager.glGenVertexArrays();
        final int buffer = GLStateManager.glGenBuffers();
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GLStateManager.disableDepthTest();
            GLStateManager.disableCull();
            GLStateManager.glColorMask(true, true, true, true);
            GLStateManager.glViewport(0, 0, 8, 8);
            GLStateManager.glUseProgram(program);
            GLStateManager.glUniform1i(GLStateManager.glGetUniformLocation(program, "iris_currentAlphaFunc"), 7);
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            InstancedAttribs.pointHead(0, InstancedAttribs.HEAD_SIZE);
            GLStateManager.glEnableVertexAttribArray(InstancedAttribs.LOC_OVERLAY);
            GLStateManager.glVertexAttribDivisor(InstancedAttribs.LOC_OVERLAY, 1);
            final ByteBuffer data = BufferUtils.createByteBuffer(InstancedAttribs.HEAD_SIZE);
            final var builder = ProgramUniforms.builder("entityTint", program);
            builder.uniform4f("entityColor", state::getCurrentEntityColor, state.getEntityColorNotifier());
            final List<Uniform> uniforms = Reflect.get(builder.buildUniforms(), "dynamic");
            final Consumer<int[]> checkPixel = expected -> {
                for (Uniform uniform : uniforms) uniform.update();
                data.putInt(InstancedAttribs.OFFSET_OVERLAY, AngelicaBufferSource.packEntityColor(state.getCurrentEntityColor()));
                GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                GLStateManager.glClearColor(0, 0, 0, 1);
                GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
                if (kind == Instancing.NONE) GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                else GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 3, 1);
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "draw error");
                final int[] pixel = FfpFixture.readPixel(0, 0);
                for (int channel = 0; channel < 4; channel++) {
                    assertEquals(expected[channel], pixel[channel], 1, kind + " tint pixel " + Arrays.toString(pixel));
                }
            };
            checkPixel.accept(new int[] {0, 0, 255, 255});
            state.pushCurrentEntityColor();
            state.setCurrentEntityColor(1, 0, 0, 0.5f);
            checkPixel.accept(new int[] {128, 0, 127, 255});
            state.popCurrentEntityColor();
            checkPixel.accept(new int[] {0, 0, 255, 255});
        } finally {
            state.getEntityColorNotifier().setListener(null);
            GLStateManager.glUseProgram(0);
            GLStateManager.glBindVertexArray(0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteBuffers(buffer);
            GLStateManager.glDeleteVertexArrays(vao);
            GLStateManager.glDeleteProgram(program);
            GLStateManager.glPopAttrib();
        }
    }

    @Test
    void highEntityBlockAndItemIdsReachTheFragmentShader() {
        final String vertexSource = """
            #version 120
            void main() {
                gl_Position = vec4(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0, 0.0, 1.0);
            }
            """;
        final String fragmentSource = """
            #version 120
            uniform int entityId;
            uniform int blockEntityId;
            uniform int currentRenderedItemId;
            uniform int expectedId;
            void main() {
                bool matches = entityId == expectedId && blockEntityId == expectedId && currentRenderedItemId == expectedId;
                gl_FragData[0] = matches ? vec4(0, 1, 0, 1) : vec4(1, 0, 0, 1);
            }
            """;
        final int vao = GLStateManager.glGenVertexArrays();
        final int buffer = GLStateManager.glGenBuffers();
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.glViewport(0, 0, 8, 8);
        try {
            for (Instancing kind : new Instancing[] { Instancing.TEMPLATE, Instancing.CUBE }) {
                final int program = linkProgram("highIds", TransformPatcher.patchAttributesInstanced(
                    vertexSource, null, null, null, fragmentSource, TEX_LM, false, kind));
                try {
                    GLStateManager.glUseProgram(program);
                    GLStateManager.glUniform1i(GLStateManager.glGetUniformLocation(program, "iris_currentAlphaFunc"), 7);
                    GLStateManager.glBindVertexArray(vao);
                    GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
                    InstancedAttribs.pointHead(0, InstancedAttribs.HEAD_SIZE);
                    GLStateManager.glEnableVertexAttribArray(InstancedAttribs.LOC_ENTITY);
                    GLStateManager.glVertexAttribDivisor(InstancedAttribs.LOC_ENTITY, 1);
                    final ByteBuffer data = BufferUtils.createByteBuffer(InstancedAttribs.HEAD_SIZE);
                    for (int id : new int[] { -1, 0, 32767, 32768, 45020, 50020, 50072, 65534 }) {
                        data.putLong(InstancedAttribs.OFFSET_ENTITY, InstancedAttribs.packEntityInfo(id, id, id));
                        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
                        GLStateManager.glUniform1i(GLStateManager.glGetUniformLocation(program, "expectedId"), id);
                        GLStateManager.glClearColor(0, 0, 0, 1);
                        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
                        GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 3, 1);
                        assertEquals(program, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), "draw program");
                        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "draw error");
                        final int[] pixel = FfpFixture.readPixel(0, 0);
                        assertEquals(255, pixel[1], kind + " material ID " + id + " pixel " + Arrays.toString(pixel));
                    }
                } finally {
                    GLStateManager.glUseProgram(0);
                    GLStateManager.glDeleteProgram(program);
                }
            }
        } finally {
            GLStateManager.glBindVertexArray(0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteBuffers(buffer);
            GLStateManager.glDeleteVertexArrays(vao);
            GLStateManager.glPopAttrib();
        }
    }

    private static int linkProgram(String name, Map<PatchShaderType, String> result) {
        final String v = result.get(PatchShaderType.VERTEX);
        final String g = result.get(PatchShaderType.GEOMETRY);
        final String f = result.get(PatchShaderType.FRAGMENT);
        assertNotNull(v);
        assertNotNull(f);

        final GlShader vertex = new GlShader(ShaderType.VERTEX, name + "Vertex", v);
        final GlShader geometry = g == null ? null : new GlShader(ShaderType.GEOMETRY, name + "Geometry", g);
        final GlShader fragment = new GlShader(ShaderType.FRAGMENT, name + "Fragment", f);
        try {
            return geometry == null
                ? ProgramCreator.create(name, vertex, fragment)
                : ProgramCreator.create(name, vertex, geometry, fragment);
        } finally {
            vertex.destroy();
            if (geometry != null) {
                geometry.destroy();
            }
            fragment.destroy();
        }
    }

    private static void compile(int type, String source) {
        final int shader = GLStateManager.glCreateShader(type);
        GLStateManager.glShaderSource(shader, source);
        GLStateManager.glCompileShader(shader);
        assertEquals(GL11.GL_TRUE, GLStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), () -> "shader failed to compile:\n" + GLStateManager.glGetShaderInfoLog(shader, 4096) + "\n" + source);
        GLStateManager.glDeleteShader(shader);
    }
}

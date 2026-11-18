package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import com.gtnewhorizons.angelica.api.tesr.TesrShaderUniforms;
import com.gtnewhorizons.angelica.api.tesr.TesrShaders;
import com.gtnewhorizons.angelica.glsm.CompatUniformManager;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMFog;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.mixins.interfaces.EntityRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@GLCoreTest
class TesrShaderProgramsGLTest {

    private static final String LIT_VERTEX = """
        angelica_out vec2 v_UV; angelica_out vec2 v_Light; angelica_out vec4 v_Color; angelica_out float v_Depth;
        void main() { gl_Position = angelica_clipPosition(); vec3 normal = angelica_eyeNormal(); float d0 = max(dot(normal, normalize(angelica_lightDirection(0))), 0.0); float d1 = max(dot(normal, normalize(angelica_lightDirection(1))), 0.0); vec3 light = min(vec3(0.4) + angelica_lightDiffuse(0) * d0 + angelica_lightDiffuse(1) * d1, 1.0); vec4 color = angelica_color(); v_Color = vec4(color.rgb * light, color.a); v_UV = angelica_uv(); v_Light = angelica_lightmapUv(); v_Depth = angelica_fogCoord(); }
        """;
    private static final String LIT_FRAGMENT = """
        angelica_in vec2 v_UV; angelica_in vec2 v_Light; angelica_in vec4 v_Color; angelica_in float v_Depth;
        void main() { vec4 color = angelica_sampleTexture(v_UV) * v_Color; if (!angelica_alphaTest(color.a)) discard; color.rgb *= angelica_sampleLightmap(v_Light).rgb; angelica_FragColor = vec4(angelica_fog(color.rgb, v_Depth), color.a); }
        """;
    private static final String UNLIT_VERTEX = """
        angelica_out vec2 v_UV; angelica_out vec4 v_Color; angelica_out float v_Depth;
        void main() { gl_Position = angelica_clipPosition(); v_Color = angelica_color(); v_UV = angelica_uv(); v_Depth = angelica_fogCoord(); }
        """;
    private static final String UNLIT_FRAGMENT = """
        angelica_in vec2 v_UV; angelica_in vec4 v_Color; angelica_in float v_Depth;
        void main() { vec4 color = angelica_sampleTexture(v_UV) * v_Color; if (!angelica_alphaTest(color.a)) discard; angelica_FragColor = vec4(angelica_fog(color.rgb, v_Depth), color.a); }
        """;
    private static final String BROKEN_FRAGMENT = "void main() { angelica_FragColor = missing; }\n";

    private static int names;

    private final Map<ResourceLocation, String> sources = new HashMap<>();
    private int reads;
    private Minecraft savedMinecraft;
    private int lightmapTexture;
    private int otherTexture;

    @BeforeEach
    void installResources() {
        ShaderManager.enable();
        ShaderManager.getInstance().activate();
        lightmapTexture = FfpFixture.solidTexture(255, 255, 255, 255);
        otherTexture = FfpFixture.solidTexture(0, 0, 0, 255);

        final IResourceManager manager = new IResourceManager() {
            @Override
            public Set<String> getResourceDomains() {
                return Set.of("test");
            }

            @Override
            public IResource getResource(ResourceLocation location) throws IOException {
                reads++;
                final String body = sources.get(location);
                if (body == null) throw new FileNotFoundException(location.toString());
                final IResource resource = mock(IResource.class);
                when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                return resource;
            }

            @Override
            public List<IResource> getAllResources(ResourceLocation location) throws IOException {
                return List.of(getResource(location));
            }
        };
        final DynamicTexture lightmap = mock(DynamicTexture.class);
        when(lightmap.getGlTextureId()).thenReturn(lightmapTexture);
        final EntityRenderer entityRenderer = mock(EntityRenderer.class, withSettings().extraInterfaces(EntityRendererAccessor.class));
        when(((EntityRendererAccessor) entityRenderer).getLightmapTexture()).thenReturn(lightmap);
        final Minecraft minecraft = mock(Minecraft.class);
        when(minecraft.getResourceManager()).thenReturn(manager);
        minecraft.entityRenderer = entityRenderer;
        savedMinecraft = Reflect.getStatic(Minecraft.class, "theMinecraft");
        Reflect.setStatic(Minecraft.class, "theMinecraft", minecraft);
    }

    @AfterEach
    void cleanup() {
        GLSMPrograms.glUseProgram(0);
        TesrShaderPrograms.invalidateAll();
        Reflect.setStatic(Minecraft.class, "theMinecraft", savedMinecraft);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLSMTextures.glDeleteTextures(lightmapTexture);
        GLSMTextures.glDeleteTextures(otherTexture);
        GLSMFog.disableFog();
        final ShaderManager ffp = ShaderManager.getInstance();
        if (ffp.isActive()) ffp.deactivate();
    }

    private TesrShaderPrograms.Entry entry(String vertexBody, String fragmentBody, TesrShaderUniforms uniforms) {
        final int id = names++;
        final ResourceLocation vertex = new ResourceLocation("test", "shaders/" + id + ".vert");
        final ResourceLocation fragment = new ResourceLocation("test", "shaders/" + id + ".frag");
        sources.put(vertex, vertexBody);
        sources.put(fragment, fragmentBody);
        final TesrShader shader = TesrShaders.register("test:programs_" + id, vertex, fragment, uniforms);
        return TesrShaderPrograms.entry(shader);
    }

    private static int uniformInt(int program, String name) {
        final int location = GL20.glGetUniformLocation(program, name);
        assertNotEquals(-1, location, name + " must be active");
        final IntBuffer value = BufferUtils.createIntBuffer(1);
        GL20.glGetUniform(program, location, value);
        return value.get(0);
    }

    private static void refresh(int program) {
        assertTrue(CompatUniformManager.refreshCompatUniforms(program, GLStateManager.ctx()), "a linked variant must be registered for Angelica's uniforms");
    }

    @Test
    void variantsLinkLazilyOnFirstBind() {
        final TesrShaderPrograms.Entry entry = entry(LIT_VERTEX, LIT_FRAGMENT, null);
        assertEquals(0, reads, "registration and entry resolution read nothing");
        assertFalse(TesrShaderPrograms.instancedAvailable(entry), "an unlinked variant reads as unavailable");
        assertEquals(0, reads, "the queue-time availability read must not link");
        assertEquals(0, entry.baked.program);
        assertEquals(0, entry.instanced.program);

        assertTrue(TesrShaderPrograms.bindBaked(entry, false));
        assertEquals(2, reads, "one read of each stage serves both variants");
        assertNotEquals(0, entry.baked.program);
        assertNotEquals(0, entry.instanced.program, "the first layer bind links the instanced variant too");
        assertTrue(TesrShaderPrograms.instancedAvailable(entry));
        assertEquals(entry.baked.program, GLSMPrograms.getActiveProgram());

        assertTrue(TesrShaderPrograms.bindInstanced(entry));
        assertEquals(entry.instanced.program, GLSMPrograms.getActiveProgram());
        TesrShaderPrograms.release(entry);
        assertEquals(0, GLSMPrograms.getActiveProgram(), "release restores the program bound before the layer");

        assertTrue(TesrShaderPrograms.bindBaked(entry, false));
        TesrShaderPrograms.release(entry);
        assertEquals(2, reads, "a ready variant is never read or linked again");
    }

    @Test
    void linkWaitsWhileADisplayListRecords() {
        final TesrShaderPrograms.Entry entry = entry(UNLIT_VERTEX, UNLIT_FRAGMENT, null);
        final long failures = TesrShaderPrograms.linkFailures;
        final int list = GLSMDraws.glGenLists(1);
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        try {
            assertFalse(TesrShaderPrograms.bindBaked(entry, false), "a recording list would capture the link's program switches");
            TesrShaderPrograms.release(entry);
        } finally {
            GLSMDraws.glEndList();
            GLSMDraws.glDeleteLists(list, 1);
        }
        assertEquals(0, reads);
        assertEquals(failures, TesrShaderPrograms.linkFailures, "the variants stay unlinked, not failed");

        assertTrue(TesrShaderPrograms.bindBaked(entry, false), "the next bind outside a list links");
        TesrShaderPrograms.release(entry);
    }

    @ParameterizedTest(name = "missing resource {0}")
    @ValueSource(booleans = { false, true })
    void failedVariantsReportOnceAndAreNotRetried(boolean missingResource) {
        final TesrShaderPrograms.Entry entry = entry(UNLIT_VERTEX, missingResource ? UNLIT_FRAGMENT : BROKEN_FRAGMENT, null);
        if (missingResource) sources.clear();
        final long failures = TesrShaderPrograms.linkFailures;
        final long fallbacks = TesrShaderPrograms.fallbacks;

        assertFalse(TesrShaderPrograms.bindBaked(entry, false));
        assertEquals(0, GLSMPrograms.getActiveProgram(), "a failed bind leaves the emulation bound");
        TesrShaderPrograms.release(entry);
        final int readsAfterFirstBind = reads;
        assertFalse(TesrShaderPrograms.bindBaked(entry, false));
        TesrShaderPrograms.release(entry);

        assertEquals(0, entry.baked.program);
        assertEquals(2, TesrShaderPrograms.linkFailures - failures, "each variant reports its failure once, not per bind");
        assertEquals(2, TesrShaderPrograms.fallbacks - fallbacks);
        assertEquals(readsAfterFirstBind, reads, "a failed variant is not retried");
        assertEquals(0, GLSMPrograms.getActiveProgram());
    }

    @Test
    void instancedFailureDisablesInstancingOnly() {
        final TesrShaderPrograms.Entry entry = entry(RetainedTesrTestSupport.BAKED_ONLY_VERTEX, RetainedTesrTestSupport.COLOR_FRAGMENT, null);
        final long failures = TesrShaderPrograms.linkFailures;

        assertTrue(TesrShaderPrograms.bindBaked(entry, false), "the baked variant still draws");
        assertEquals(entry.baked.program, GLSMPrograms.getActiveProgram());
        assertFalse(TesrShaderPrograms.instancedAvailable(entry), "the body names a uniform only the baked variant declares");
        assertEquals(1, TesrShaderPrograms.linkFailures - failures);
        assertFalse(TesrShaderPrograms.bindInstanced(entry));
        assertEquals(entry.baked.program, GLSMPrograms.getActiveProgram(), "a failed instanced bind leaves the baked variant bound");
        TesrShaderPrograms.release(entry);
        assertEquals(1, TesrShaderPrograms.linkFailures - failures, "a failed variant is not retried");
    }

    @Test
    void invalidateAllRelinks() {
        final int[] linked = new int[2];
        final int[] bound = new int[2];
        final TesrShaderUniforms uniforms = new TesrShaderUniforms() {
            @Override
            public void angelica$linked(int program, boolean instanced) {
                assertEquals(program, GLSMPrograms.getActiveProgram(), "the variant is bound while locations resolve");
                linked[instanced ? 1 : 0]++;
            }

            @Override
            public void angelica$bound(int program, boolean instanced) {
                bound[instanced ? 1 : 0]++;
            }
        };
        final TesrShaderPrograms.Entry entry = entry(UNLIT_VERTEX, BROKEN_FRAGMENT, uniforms);
        assertFalse(TesrShaderPrograms.bindBaked(entry, false));
        TesrShaderPrograms.release(entry);
        assertEquals(0, linked[0]);

        sources.put(entry.shader.fragment(), UNLIT_FRAGMENT);
        TesrShaderPrograms.invalidateAll();
        assertTrue(TesrShaderPrograms.bindBaked(entry, false), "a reload retries a failed variant");
        TesrShaderPrograms.release(entry);
        final int first = entry.baked.program;
        assertTrue(GL20.glIsProgram(first));

        TesrShaderPrograms.invalidateAll();
        assertEquals(0, entry.baked.program);
        assertTrue(!GL20.glIsProgram(first) || GL20.glGetProgrami(first, GL20.GL_DELETE_STATUS) == GL11.GL_TRUE, "the stale program is deleted, or flagged while the driver still has it current");
        assertTrue(TesrShaderPrograms.bindBaked(entry, false));
        TesrShaderPrograms.release(entry);
        assertNotEquals(0, entry.baked.program);
        assertEquals(2, linked[0], "linked runs once per linked variant");
        assertEquals(2, linked[1], "each relink of the baked variant links the instanced one with it");
        assertEquals(2, bound[0], "bound runs once per successful bind");
        assertEquals(0, bound[1]);
    }

    @Test
    void lightmapBoundOnUnitOneAndRestored() {
        final TesrShaderPrograms.Entry entry = entry(LIT_VERTEX, LIT_FRAGMENT, null);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, otherTexture);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);

        assertTrue(TesrShaderPrograms.bindBaked(entry, false));
        assertEquals(otherTexture, GLSMTextures.getBoundTextureForServerState(1), "a layer without the lightmap flag leaves unit 1 alone");
        TesrShaderPrograms.release(entry);

        assertTrue(TesrShaderPrograms.bindBaked(entry, true));
        assertEquals(lightmapTexture, GLSMTextures.getBoundTextureForServerState(1));
        assertEquals(0, GLSMTextures.getActiveTextureUnit(), "the bind must not move the active unit");
        assertEquals(1, uniformInt(entry.baked.program, TesrShaderPrelude.LIGHTMAP_SAMPLER));
        assertTrue(TesrShaderPrograms.bindInstanced(entry));
        assertEquals(lightmapTexture, GLSMTextures.getBoundTextureForServerState(1));
        TesrShaderPrograms.release(entry);
        assertEquals(otherTexture, GLSMTextures.getBoundTextureForServerState(1), "release restores the caller's unit-1 texture");
        assertEquals(0, GLSMTextures.getActiveTextureUnit());
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        assertEquals(otherTexture, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "the driver binding is restored too");
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }

    @Test
    void fogUniformsFollowFogState() {
        final TesrShaderPrograms.Entry entry = entry(LIT_VERTEX, LIT_FRAGMENT, null);
        assertTrue(TesrShaderPrograms.bindBaked(entry, false));
        final int program = entry.baked.program;

        GLSMFog.disableFog();
        GLSMFog.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        refresh(program);
        assertEquals(0, uniformInt(program, "angelica_FogMode"), "disabled fog is mode 0 whatever the fog function");
        GLSMFog.enableFog();
        refresh(program);
        assertEquals(1, uniformInt(program, "angelica_FogMode"));
        GLSMFog.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
        refresh(program);
        assertEquals(2, uniformInt(program, "angelica_FogMode"));
        GLSMFog.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP2);
        refresh(program);
        assertEquals(3, uniformInt(program, "angelica_FogMode"));
        GLSMFog.disableFog();
        refresh(program);
        assertEquals(0, uniformInt(program, "angelica_FogMode"));

        GLSMFog.glFogf(GL11.GL_FOG_START, 2f);
        GLSMFog.glFogf(GL11.GL_FOG_END, 10f);
        refresh(program);
        final FloatBuffer params = BufferUtils.createFloatBuffer(4);
        GL20.glGetUniform(program, GL20.glGetUniformLocation(program, "angelica_FogParams"), params);
        assertEquals(-1f / 8f, params.get(0), 1e-6f, "a fog range change re-uploads the params");
        TesrShaderPrograms.release(entry);
        GLSMFog.glFogf(GL11.GL_FOG_START, 0f);
        GLSMFog.glFogf(GL11.GL_FOG_END, 1f);
    }

    @Test
    void lightUniformsReachAProgramWithoutAnAmbientField() {
        final TesrShaderPrograms.Entry entry = entry(LIT_VERTEX, LIT_FRAGMENT, null);
        assertTrue(TesrShaderPrograms.bindBaked(entry, false));
        final int program = entry.baked.program;
        final FloatBuffer diffuse = BufferUtils.createFloatBuffer(4);
        diffuse.put(0.25f).put(0.5f).put(0.75f).put(1f).flip();
        GLSMLighting.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, diffuse);
        refresh(program);

        final int location = GL20.glGetUniformLocation(program, "angelica_LightSource[1].diffuse");
        assertNotEquals(-1, location);
        final FloatBuffer uploaded = BufferUtils.createFloatBuffer(4);
        GL20.glGetUniform(program, location, uploaded);
        assertEquals(0.5f, uploaded.get(1), 1e-6f);
        assertEquals(0.75f, uploaded.get(2), 1e-6f);
        TesrShaderPrograms.release(entry);
        diffuse.clear();
        diffuse.put(0f).put(0f).put(0f).put(1f).flip();
        GLSMLighting.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, diffuse);
    }
}

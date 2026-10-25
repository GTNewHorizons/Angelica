package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.TestPaths;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;
import com.gtnewhorizons.angelica.rendering.TextureSamplers;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.sampler.GlSampler;
import net.coderbot.iris.gl.sampler.SamplerHolder;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import net.coderbot.iris.gl.texture.TextureAccess;
import net.coderbot.iris.gl.texture.TextureWrapper;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.texture.format.LabPBRTextureFormat;
import net.coderbot.iris.texture.format.TextureFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

@GLCoreTest
class PbrSamplerBindingGLTest {
    private static final int ANISOTROPY = EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT;
    private static final int NORMAL_UNIT = 3;
    private static final int SPECULAR_UNIT = 4;

    private final Notifier normalNotifier = new Notifier();
    private final Notifier specularNotifier = new Notifier();
    private final DeferredWorldRenderingPipeline pipeline = mock(DeferredWorldRenderingPipeline.class, CALLS_REAL_METHODS);
    private TextureFormat format;
    private int anisotropy = 2;
    private int program;
    private int normal;
    private int specular;
    private int custom;

    @BeforeAll
    static void initSamplers() {
        TextureSamplers.initRenderer();
        IrisSamplers.initRenderer();
    }

    @BeforeEach
    void setUp() {
        assumeTrue(BackendManager.RENDER_BACKEND.isAnisotropicSupported());
        assumeTrue(GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT) >= 4);
        BlockRenderingSettings.INSTANCE.setBreaksAnisotropy(false);
        program = GlTestSupport.program(GlTestSupport.TRIVIAL_VS,
            TestPaths.readString("src/test/resources/assets/angelica/shaders/pbrSamplerBindingTest.fsh"));
        GLStateManager.glUseProgram(program);
        normal = texture();
        specular = texture();
        custom = texture();
    }

    @AfterEach
    void tearDown() {
        ProgramSamplers.clearActiveSamplers();
        BlockRenderingSettings.INSTANCE.setBreaksAnisotropy(false);
        GLStateManager.glUseProgram(0);
        GLStateManager.glDeleteProgram(program);
        GLStateManager.glDeleteTextures(normal);
        GLStateManager.glDeleteTextures(specular);
        GLStateManager.glDeleteTextures(custom);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static int texture() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        final int texture = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return texture;
    }

    private ProgramSamplers.Builder builder() {
        return ProgramSamplers.builder(program, Set.of(0, 1, 2));
    }

    private void addPbrSamplers(SamplerHolder holder) {
        refreshPbrSamplers();
        holder.addDynamicSampler(TextureType.TEXTURE_2D, () -> normal, normalNotifier,
            pipeline::getNormalSampler, "normals");
        holder.addDynamicSampler(TextureType.TEXTURE_2D, () -> specular, specularNotifier,
            pipeline::getSpecularSampler, "specular");
    }

    private void refreshPbrSamplers() {
        pipeline.updatePbrSamplers(format, anisotropy);
    }

    private static int boundSampler(int unit) {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        return GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
    }

    private static void assertFiltering(int unit, int texture, float expectedAnisotropy, int minFilter) {
        final int sampler = boundSampler(unit);
        assertNotEquals(0, sampler);
        assertEquals(texture, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        assertEquals(expectedAnisotropy, GL33.glGetSamplerParameterf(sampler, ANISOTROPY));
        assertEquals(minFilter, GL33.glGetSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER));
        assertEquals(1.0f, GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, ANISOTROPY));
        assertEquals(GL11.GL_NEAREST, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
    }

    @Test
    void anisotropyChangesReachBothUnitsWithoutRebuildingTheProgramOrChangingTextures() {
        final ProgramSamplers.Builder builder = builder();
        addPbrSamplers(builder);
        final ProgramSamplers samplers = builder.build();
        samplers.update();
        assertFiltering(NORMAL_UNIT, normal, 2, GL11.GL_NEAREST_MIPMAP_LINEAR);
        assertFiltering(SPECULAR_UNIT, specular, 2, GL11.GL_NEAREST_MIPMAP_LINEAR);
        final int before = boundSampler(NORMAL_UNIT);

        anisotropy = 4;
        refreshPbrSamplers();
        normalNotifier.fire();
        specularNotifier.fire();
        assertNotEquals(before, boundSampler(NORMAL_UNIT));
        assertFiltering(NORMAL_UNIT, normal, 4, GL11.GL_NEAREST_MIPMAP_LINEAR);
        assertFiltering(SPECULAR_UNIT, specular, 4, GL11.GL_NEAREST_MIPMAP_LINEAR);

        anisotropy = 1;
        refreshPbrSamplers();
        samplers.update();
        assertFiltering(NORMAL_UNIT, normal, 1, GL11.GL_NEAREST_MIPMAP_LINEAR);
        assertFiltering(SPECULAR_UNIT, specular, 1, GL11.GL_NEAREST_MIPMAP_LINEAR);
    }

    @Test
    void labPbrFormatChangeStopsSpecularInterpolationWithoutDisablingNormalAnisotropy() {
        final ProgramSamplers.Builder builder = builder();
        addPbrSamplers(builder);
        final ProgramSamplers samplers = builder.build();
        samplers.update();

        format = new LabPBRTextureFormat("lab-pbr", "1.3");
        refreshPbrSamplers();
        normalNotifier.fire();
        specularNotifier.fire();
        assertFiltering(NORMAL_UNIT, normal, 2, GL11.GL_NEAREST_MIPMAP_LINEAR);
        assertFiltering(SPECULAR_UNIT, specular, 1, GL11.GL_NEAREST_MIPMAP_NEAREST);

        format = null;
        refreshPbrSamplers();
        samplers.update();
        assertFiltering(SPECULAR_UNIT, specular, 2, GL11.GL_NEAREST_MIPMAP_LINEAR);
    }

    @Test
    void linearTerrainSamplingDoesNotChangeTheSharedPackAndPbrSampler() {
        anisotropy = 4;
        final ProgramSamplers.Builder builder = builder();
        addPbrSamplers(builder);
        builder.build().update();
        final GlSampler nearest = IrisSamplers.getTerrainSampler(4);
        final GlSampler linear = TextureSamplers.getTerrainSampler(true, 4);

        assertNotEquals(nearest.getId(), linear.getId());
        RenderSystem.bindSamplerToUnit(0, linear.getId());
        RenderSystem.bindTextureToUnit(GL11.GL_TEXTURE_2D, 0, normal);
        assertFiltering(0, normal, 4, GL11.GL_LINEAR_MIPMAP_LINEAR);
        assertEquals(GL11.GL_LINEAR, GL33.glGetSamplerParameteri(linear.getId(), GL11.GL_TEXTURE_MAG_FILTER));

        assertSame(linear, TextureSamplers.getTerrainSampler(true, 4));
        assertEquals(nearest.getId(), boundSampler(NORMAL_UNIT));
        assertFiltering(NORMAL_UNIT, normal, 4, GL11.GL_NEAREST_MIPMAP_LINEAR);
        assertFiltering(SPECULAR_UNIT, specular, 4, GL11.GL_NEAREST_MIPMAP_LINEAR);
        assertEquals(GL11.GL_NEAREST, GL33.glGetSamplerParameteri(nearest.getId(), GL11.GL_TEXTURE_MAG_FILTER));
    }

    @ParameterizedTest
    @EnumSource(Registration.class)
    void customTextureDiscardsTheCallersCompareSamplerAndNotifier(Registration registration) {
        final boolean defaultUnit = registration == Registration.DEFAULT || registration == Registration.EXTERNAL_DEFAULT;
        final int unit = defaultUnit ? 0 : NORMAL_UNIT;
        final GlSampler shadowSampler = new GlSampler(false, false, false, true, true, true);
        RenderSystem.bindSamplerToUnit(unit, shadowSampler.getId());
        final Object2ObjectOpenHashMap<String, TextureAccess> overrides = new Object2ObjectOpenHashMap<>();
        overrides.put("normals", new TextureWrapper(() -> custom, TextureType.TEXTURE_2D));
        final ProgramSamplers.Builder builder = defaultUnit
            ? ProgramSamplers.builder(program, Set.of()) : builder();
        final SamplerHolder holder = ProgramSamplers.customTextureSamplerInterceptor(builder, overrides);
        try {
            switch (registration) {
                case DEFAULT -> holder.addDefaultSampler(TextureType.TEXTURE_2D, () -> normal,
                    normalNotifier, () -> shadowSampler, "normals");
                case DYNAMIC -> holder.addDynamicSampler(TextureType.TEXTURE_2D, () -> normal,
                    () -> shadowSampler, "normals");
                case NOTIFIER -> holder.addDynamicSampler(TextureType.TEXTURE_2D, () -> normal,
                    normalNotifier, () -> shadowSampler, "normals");
                case EXTERNAL_DEFAULT -> holder.addExternalSampler(0, "normals");
                case EXTERNAL_DYNAMIC -> holder.addExternalSampler(2, "normals");
            }
            builder.build().update();

            assertEquals(0, boundSampler(unit));
            assertEquals(custom, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            assertEquals(GL11.GL_NEAREST, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
            assertNull(normalNotifier.listener);
        } finally {
            shadowSampler.destroy();
        }
    }

    @ParameterizedTest
    @EnumSource(ProgramKind.class)
    void programExitClearsSamplersIncludingUnitsUsedByThePreviousProgram(ProgramKind kind) {
        final ProgramSamplers.Builder first = builder();
        addPbrSamplers(first);
        first.build().update();
        assertNotEquals(0, boundSampler(NORMAL_UNIT));
        assertNotEquals(0, boundSampler(SPECULAR_UNIT));

        final ProgramSamplers.Builder second = ProgramSamplers.builder(program, Set.of(0, 1, 2, 3, 4));
        addPbrSamplers(second);
        second.build().update();
        assertNotEquals(0, boundSampler(5));
        assertNotEquals(0, boundSampler(6));

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
        switch (kind) {
            case GRAPHICS -> Program.unbind();
            case COMPUTE -> ComputeProgram.unbind();
        }
        assertEquals(GL13.GL_TEXTURE2, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        for (int unit = NORMAL_UNIT; unit <= 6; unit++) {
            assertEquals(0, boundSampler(unit));
        }
        assertNull(normalNotifier.listener);
        assertNull(specularNotifier.listener);
    }

    @Test
    void failedBindingClearsPartialSamplerStateAndRestoresTheActiveTextureUnit() {
        final ProgramSamplers.Builder builder = builder();
        builder.addDynamicSampler(TextureType.TEXTURE_2D, () -> normal, normalNotifier,
            () -> IrisSamplers.getTerrainSampler(2), "normals");
        builder.addDynamicSampler(TextureType.TEXTURE_2D, () -> specular, specularNotifier,
            () -> { throw new IllegalStateException("Failed sampler lookup"); }, "specular");
        final ProgramSamplers samplers = builder.build();

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
        assertThrows(IllegalStateException.class, samplers::update);
        assertEquals(GL13.GL_TEXTURE2, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(0, boundSampler(NORMAL_UNIT));
        assertEquals(0, boundSampler(SPECULAR_UNIT));
        assertNull(normalNotifier.listener);
        assertNull(specularNotifier.listener);
    }

    @Test
    void packAnisotropyOptOutAppliesToTerrainAndPbrWithoutChangingCachedSamplers() {
        final GlSampler original = IrisSamplers.getTerrainSampler(4);
        final ProgramSamplers.Builder builder = builder();
        anisotropy = 4;
        addPbrSamplers(builder);
        final ProgramSamplers samplers = builder.build();
        samplers.update();
        assertEquals(original.getId(), boundSampler(NORMAL_UNIT));

        BlockRenderingSettings.INSTANCE.setBreaksAnisotropy(true);
        refreshPbrSamplers();
        samplers.update();
        assertEquals(IrisSamplers.getTerrainSampler(4).getId(), boundSampler(NORMAL_UNIT));
        assertFiltering(NORMAL_UNIT, normal, 1, GL11.GL_NEAREST_MIPMAP_LINEAR);
        assertFiltering(SPECULAR_UNIT, specular, 1, GL11.GL_NEAREST_MIPMAP_LINEAR);
        assertEquals(4.0f, GL33.glGetSamplerParameterf(original.getId(), ANISOTROPY));
        assertSame(original, TextureSamplers.getTerrainSampler(false, 4));

        BlockRenderingSettings.INSTANCE.setBreaksAnisotropy(false);
        refreshPbrSamplers();
        samplers.update();
        assertSame(original, IrisSamplers.getTerrainSampler(4));
        assertFiltering(NORMAL_UNIT, normal, 4, GL11.GL_NEAREST_MIPMAP_LINEAR);
        assertFiltering(SPECULAR_UNIT, specular, 4, GL11.GL_NEAREST_MIPMAP_LINEAR);
    }

    private enum Registration { DEFAULT, DYNAMIC, NOTIFIER, EXTERNAL_DEFAULT, EXTERNAL_DYNAMIC }

    private enum ProgramKind { GRAPHICS, COMPUTE }

    private static final class Notifier implements ValueUpdateNotifier {
        private Runnable listener;

        @Override
        public void setListener(Runnable listener) {
            this.listener = listener;
        }

        void fire() {
            if (listener != null) listener.run();
        }
    }
}

package net.coderbot.iris.celeritas;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.pipeline.PatchedShaderPrinter;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

public class CeleritasTerrainPipeline {
    private final EnumMap<IrisTerrainPass, PassInfo> passInfoMap = new EnumMap<>(IrisTerrainPass.class);

    @Getter
    private final CustomUniforms customUniforms;

    private final IntFunction<ProgramSamplers> createTerrainSamplers;
    private final IntFunction<ProgramSamplers> createShadowSamplers;

    private final IntFunction<ProgramImages> createTerrainImages;
    private final IntFunction<ProgramImages> createShadowImages;

    @Getter
    @Accessors(fluent = true)
    public static final class PassInfo {
        private final EnumMap<PatchShaderType, Optional<String>> sources = new EnumMap<>(PatchShaderType.class);
        private GlFramebuffer framebuffer;
        private BlendModeOverride blendModeOverride;
        private float alphaReference;

        private PassInfo() {
            for (PatchShaderType type : PatchShaderType.VALUES) {
                sources.put(type, Optional.empty());
            }
        }

        private void setSource(PatchShaderType type, @Nullable String source) {
            sources.put(type, Optional.ofNullable(source));
        }
    }

    public CeleritasTerrainPipeline(
            IntFunction<ProgramSamplers> createTerrainSamplers,
            IntFunction<ProgramSamplers> createShadowSamplers,
            IntFunction<ProgramImages> createTerrainImages,
            IntFunction<ProgramImages> createShadowImages,
            CustomUniforms customUniforms,
            String terrainSourceName,
            String translucentSourceName,
            String shadowSourceName,
            CompletableFuture<Map<PatchShaderType, String>> terrainFuture,
            CompletableFuture<Map<PatchShaderType, String>> translucentFuture,
            CompletableFuture<Map<PatchShaderType, String>> shadowFuture,
            RenderTargets renderTargets,
            ImmutableSet<Integer> flippedAfterPrepare,
            ImmutableSet<Integer> flippedAfterTranslucent,
            @Nullable GlFramebuffer shadowFramebuffer,
            int[] terrainDrawBuffers
    ) {
        this.customUniforms = customUniforms;

        // Initialize PassInfo for each pass
        for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
            passInfoMap.put(pass, new PassInfo());
        }

        // Process terrain shaders (used for GBUFFER_SOLID and GBUFFER_CUTOUT)
        if (terrainFuture != null) {
            try {
                final Map<PatchShaderType, String> result = terrainFuture.join();
                final PassInfo solidInfo = passInfoMap.get(IrisTerrainPass.GBUFFER_SOLID);
                final PassInfo cutoutInfo = passInfoMap.get(IrisTerrainPass.GBUFFER_CUTOUT);

                solidInfo.setSource(PatchShaderType.VERTEX, result.get(PatchShaderType.VERTEX));
                solidInfo.setSource(PatchShaderType.GEOMETRY, result.get(PatchShaderType.GEOMETRY));
                solidInfo.setSource(PatchShaderType.FRAGMENT, result.get(PatchShaderType.FRAGMENT));

                cutoutInfo.setSource(PatchShaderType.VERTEX, result.get(PatchShaderType.VERTEX));
                cutoutInfo.setSource(PatchShaderType.GEOMETRY, result.get(PatchShaderType.GEOMETRY));
                cutoutInfo.setSource(PatchShaderType.FRAGMENT, result.get(PatchShaderType.FRAGMENT));

                PatchedShaderPrinter.debugPatchedShaders(terrainSourceName + "_celeritas",
                    result.get(PatchShaderType.VERTEX), result.get(PatchShaderType.GEOMETRY), result.get(PatchShaderType.FRAGMENT));
            } catch (Exception e) {
                Iris.logger.error("Failed to transform terrain shader for Celeritas: {}", terrainSourceName, e);
                throw new RuntimeException("Shader transformation failed for " + terrainSourceName, e);
            }
        }

        // Process translucent shaders (used for GBUFFER_TRANSLUCENT)
        if (translucentFuture != null) {
            try {
                final Map<PatchShaderType, String> result = translucentFuture.join();
                final PassInfo translucentInfo = passInfoMap.get(IrisTerrainPass.GBUFFER_TRANSLUCENT);

                translucentInfo.setSource(PatchShaderType.VERTEX, result.get(PatchShaderType.VERTEX));
                translucentInfo.setSource(PatchShaderType.GEOMETRY, result.get(PatchShaderType.GEOMETRY));
                translucentInfo.setSource(PatchShaderType.FRAGMENT, result.get(PatchShaderType.FRAGMENT));

                PatchedShaderPrinter.debugPatchedShaders(translucentSourceName + "_celeritas",
                    result.get(PatchShaderType.VERTEX), result.get(PatchShaderType.GEOMETRY), result.get(PatchShaderType.FRAGMENT));
            } catch (Exception e) {
                Iris.logger.error("Failed to transform translucent shader for Celeritas: {}", translucentSourceName, e);
                throw new RuntimeException("Shader transformation failed for " + translucentSourceName, e);
            }
        }

        // Process shadow shaders (used for SHADOW and SHADOW_CUTOUT)
        if (shadowFuture != null) {
            try {
                final Map<PatchShaderType, String> result = shadowFuture.join();
                final PassInfo shadowInfo = passInfoMap.get(IrisTerrainPass.SHADOW);
                final PassInfo shadowCutoutInfo = passInfoMap.get(IrisTerrainPass.SHADOW_CUTOUT);

                shadowInfo.setSource(PatchShaderType.VERTEX, result.get(PatchShaderType.VERTEX));
                shadowInfo.setSource(PatchShaderType.GEOMETRY, result.get(PatchShaderType.GEOMETRY));
                shadowInfo.setSource(PatchShaderType.FRAGMENT, result.get(PatchShaderType.FRAGMENT));

                shadowCutoutInfo.setSource(PatchShaderType.VERTEX, result.get(PatchShaderType.VERTEX));
                shadowCutoutInfo.setSource(PatchShaderType.GEOMETRY, result.get(PatchShaderType.GEOMETRY));
                shadowCutoutInfo.setSource(PatchShaderType.FRAGMENT, result.get(PatchShaderType.FRAGMENT));

                PatchedShaderPrinter.debugPatchedShaders(shadowSourceName + "_celeritas",
                    result.get(PatchShaderType.VERTEX), result.get(PatchShaderType.GEOMETRY), result.get(PatchShaderType.FRAGMENT));
            } catch (Exception e) {
                Iris.logger.error("Failed to transform shadow shader for Celeritas: {}", shadowSourceName, e);
                throw new RuntimeException("Shader transformation failed for " + shadowSourceName, e);
            }
        }

        // Set up framebuffers for each pass
        // Shadow passes use the shadow framebuffer
        if (shadowFramebuffer != null) {
            passInfoMap.get(IrisTerrainPass.SHADOW).framebuffer = shadowFramebuffer;
            passInfoMap.get(IrisTerrainPass.SHADOW_CUTOUT).framebuffer = shadowFramebuffer;
        }

        // GBuffer passes use render target framebuffers
        if (renderTargets != null && terrainDrawBuffers != null) {
            final GlFramebuffer solidFb = renderTargets.createGbufferFramebuffer(flippedAfterPrepare, terrainDrawBuffers);
            final GlFramebuffer translucentFb = renderTargets.createGbufferFramebuffer(flippedAfterTranslucent, terrainDrawBuffers);

            passInfoMap.get(IrisTerrainPass.GBUFFER_SOLID).framebuffer = solidFb;
            passInfoMap.get(IrisTerrainPass.GBUFFER_CUTOUT).framebuffer = solidFb;
            passInfoMap.get(IrisTerrainPass.GBUFFER_TRANSLUCENT).framebuffer = translucentFb;
        }

        // Set blend mode overrides
        passInfoMap.get(IrisTerrainPass.GBUFFER_SOLID).blendModeOverride = BlendModeOverride.OFF;
        passInfoMap.get(IrisTerrainPass.GBUFFER_CUTOUT).blendModeOverride = BlendModeOverride.OFF;
        passInfoMap.get(IrisTerrainPass.GBUFFER_TRANSLUCENT).blendModeOverride = null; // Default blending
        passInfoMap.get(IrisTerrainPass.SHADOW).blendModeOverride = BlendModeOverride.OFF;
        passInfoMap.get(IrisTerrainPass.SHADOW_CUTOUT).blendModeOverride = BlendModeOverride.OFF;

        // Set alpha references
        passInfoMap.get(IrisTerrainPass.GBUFFER_SOLID).alphaReference = 0.0f;
        passInfoMap.get(IrisTerrainPass.GBUFFER_CUTOUT).alphaReference = 0.1f;
        passInfoMap.get(IrisTerrainPass.GBUFFER_TRANSLUCENT).alphaReference = 0.0f;
        passInfoMap.get(IrisTerrainPass.SHADOW).alphaReference = 0.0f;
        passInfoMap.get(IrisTerrainPass.SHADOW_CUTOUT).alphaReference = 0.1f;

        this.createTerrainSamplers = createTerrainSamplers;
        this.createShadowSamplers = createShadowSamplers;
        this.createTerrainImages = createTerrainImages;
        this.createShadowImages = createShadowImages;
    }

    public PassInfo getPassInfo(IrisTerrainPass pass) {
        return passInfoMap.get(pass);
    }

    public ProgramUniforms.Builder initUniforms(int programId) {
        final ProgramUniforms.Builder uniforms = ProgramUniforms.builder("<celeritas shaders>", programId);

        CommonUniforms.addDynamicUniforms(uniforms);
        customUniforms.assignTo(uniforms);

        BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
        return uniforms;
    }

    public boolean hasShadowPass() {
        return createShadowSamplers != null;
    }

    public ProgramSamplers initTerrainSamplers(int programId) {
        return createTerrainSamplers.apply(programId);
    }

    public ProgramSamplers initShadowSamplers(int programId) {
        return createShadowSamplers.apply(programId);
    }

    public ProgramImages initTerrainImages(int programId) {
        return createTerrainImages.apply(programId);
    }

    public ProgramImages initShadowImages(int programId) {
        return createShadowImages.apply(programId);
    }
}

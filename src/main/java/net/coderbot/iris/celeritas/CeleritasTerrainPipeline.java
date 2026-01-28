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
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.coderbot.iris.shaderpack.ProgramSource;
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
            Optional<ProgramSource> terrainSource,
            Optional<ProgramSource> translucentSource,
            Optional<ProgramSource> shadowSource,
            CompletableFuture<Map<PatchShaderType, String>> terrainFuture,
            CompletableFuture<Map<PatchShaderType, String>> translucentFuture,
            CompletableFuture<Map<PatchShaderType, String>> shadowFuture,
            RenderTargets renderTargets,
            ImmutableSet<Integer> flippedAfterPrepare,
            ImmutableSet<Integer> flippedAfterTranslucent,
            @Nullable GlFramebuffer shadowFramebuffer
    ) {
        this.customUniforms = customUniforms;
        this.createTerrainSamplers = createTerrainSamplers;
        this.createShadowSamplers = createShadowSamplers;
        this.createTerrainImages = createTerrainImages;
        this.createShadowImages = createShadowImages;

        // Build program source map (local - only needed during construction)
        final EnumMap<IrisTerrainPass, Optional<ProgramSource>> gbufferProgramSource = new EnumMap<>(IrisTerrainPass.class);
        gbufferProgramSource.put(IrisTerrainPass.GBUFFER_SOLID, terrainSource);
        gbufferProgramSource.put(IrisTerrainPass.GBUFFER_CUTOUT, terrainSource);
        gbufferProgramSource.put(IrisTerrainPass.GBUFFER_TRANSLUCENT, translucentSource.isPresent() ? translucentSource : terrainSource);
        gbufferProgramSource.put(IrisTerrainPass.SHADOW, shadowSource);

        // Initialize PassInfo, framebuffers, blend modes, and alpha in single pass
        for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
            PassInfo passInfo = new PassInfo();
            passInfoMap.put(pass, passInfo);

            // Set up framebuffer
            if (pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT) {
                passInfo.framebuffer = shadowFramebuffer;
            } else if (renderTargets != null) {
                ImmutableSet<Integer> flipped = pass == IrisTerrainPass.GBUFFER_TRANSLUCENT ? flippedAfterTranslucent : flippedAfterPrepare;

                Optional<ProgramSource> programSource = gbufferProgramSource.get(pass);
                programSource.ifPresentOrElse(source -> passInfo.framebuffer = renderTargets.createGbufferFramebuffer(flipped, source.getDirectives().getDrawBuffers()), () -> passInfo.framebuffer = renderTargets.createGbufferFramebuffer(flipped, new int[] {0}));
            }

            // Set blend mode override
            passInfo.blendModeOverride = switch (pass) {
                case GBUFFER_TRANSLUCENT -> null; // Default blending
                default -> BlendModeOverride.OFF;
            };

            // Set alpha reference
            passInfo.alphaReference = switch (pass) {
                case GBUFFER_CUTOUT, SHADOW_CUTOUT -> 0.1f;
                default -> 0.0f;
            };
        }

        // Process and apply shader sources
        processShaderFuture(terrainFuture, terrainSource, passInfoMap.get(IrisTerrainPass.GBUFFER_SOLID), passInfoMap.get(IrisTerrainPass.GBUFFER_CUTOUT));
        processShaderFuture(translucentFuture, translucentSource, passInfoMap.get(IrisTerrainPass.GBUFFER_TRANSLUCENT));
        processShaderFuture(shadowFuture, shadowSource, passInfoMap.get(IrisTerrainPass.SHADOW), passInfoMap.get(IrisTerrainPass.SHADOW_CUTOUT));
    }

    private void processShaderFuture(@Nullable CompletableFuture<Map<PatchShaderType, String>> future, Optional<ProgramSource> source, PassInfo... targets) {
        if (future == null || source.isEmpty()) {
            return;
        }
        final String sourceName = source.get().getName();
        try {
            final Map<PatchShaderType, String> result = future.join();
            for (PassInfo target : targets) {
                target.setSource(PatchShaderType.VERTEX, result.get(PatchShaderType.VERTEX));
                target.setSource(PatchShaderType.GEOMETRY, result.get(PatchShaderType.GEOMETRY));
                target.setSource(PatchShaderType.FRAGMENT, result.get(PatchShaderType.FRAGMENT));
            }
            PatchedShaderPrinter.debugPatchedShaders(sourceName + "_celeritas",
                result.get(PatchShaderType.VERTEX), result.get(PatchShaderType.GEOMETRY), result.get(PatchShaderType.FRAGMENT));
        } catch (Exception e) {
            Iris.logger.error("Failed to transform shader for Celeritas: {}", sourceName, e);
            throw new RuntimeException("Shader transformation failed for " + sourceName, e);
        }
    }

    public PassInfo getPassInfo(IrisTerrainPass pass) {
        return passInfoMap.get(pass);
    }

    public ProgramUniforms.Builder initUniforms(int programId) {
        final ProgramUniforms.Builder uniforms = ProgramUniforms.builder("<celeritas shaders>", programId);

        CommonUniforms.addDynamicUniforms(uniforms, FogMode.PER_VERTEX);
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

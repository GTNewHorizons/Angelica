package net.coderbot.iris.shadows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.features.FeatureFlags;
import net.coderbot.iris.gl.buffer.ShaderStorageBufferHolder;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.framebuffer.ViewportData;
import net.coderbot.iris.gl.image.GlImage;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.gl.texture.TextureAccess;
import net.coderbot.iris.pipeline.PatchedShaderPrinter;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import net.coderbot.iris.postprocess.FullScreenQuadRenderer;
import net.coderbot.iris.rendertarget.RenderTarget;
import net.coderbot.iris.samplers.IrisImages;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shaderpack.ComputeSource;
import net.coderbot.iris.shaderpack.FilledIndirectPointer;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackRenderTargetDirectives;
import net.coderbot.iris.shaderpack.ProgramDirectives;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ShadowCompositeRenderer {

    private final ShadowRenderTargets renderTargets;

    private final ImmutableList<Pass> passes;
    private final TextureAccess noiseTexture;
    private final FrameUpdateNotifier updateNotifier;
    private final Object2ObjectMap<String, TextureAccess> customTextureIds;
    private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
    private final CustomUniforms customUniforms;
    private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
    private final WorldRenderingPipeline pipeline;
    private final Set<GlImage> irisCustomImages;
    @Nullable
    private final ShaderStorageBufferHolder ssboHolder;

    public ShadowCompositeRenderer(WorldRenderingPipeline pipeline, PackDirectives packDirectives, ProgramSource[] sources, ComputeSource[][] computes,
            ShadowRenderTargets renderTargets, @Nullable ShaderStorageBufferHolder ssboHolder, TextureAccess noiseTexture, FrameUpdateNotifier updateNotifier,
            Object2ObjectMap<String, TextureAccess> customTextureIds, Set<GlImage> customImages, ImmutableMap<Integer, Boolean> explicitPreFlips,
            Object2ObjectMap<String, TextureAccess> irisCustomTextures, CustomUniforms customUniforms) {
        this.pipeline = pipeline;
        this.noiseTexture = noiseTexture;
        this.updateNotifier = updateNotifier;
        this.renderTargets = renderTargets;
        this.ssboHolder = ssboHolder;
        this.customTextureIds = customTextureIds;
        this.irisCustomTextures = irisCustomTextures;
        this.irisCustomImages = customImages;
        this.customUniforms = customUniforms;

        final PackRenderTargetDirectives renderTargetDirectives = packDirectives.getRenderTargetDirectives();
        final Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings = renderTargetDirectives.getRenderTargetSettings();

        final ImmutableList.Builder<Pass> passes = ImmutableList.builder();
        final ImmutableSet.Builder<Integer> flippedAtLeastOnce = new ImmutableSet.Builder<>();

        explicitPreFlips.forEach((buffer, shouldFlip) -> {
            if (shouldFlip) {
                renderTargets.flip(buffer);
                // NB: Flipping deferred_pre or composite_pre does NOT cause the "flippedAtLeastOnce" flag to trigger
            }
        });

        for (int i = 0, sourcesLength = sources.length; i < sourcesLength; i++) {
            ProgramSource source = sources[i];

            ImmutableSet<Integer> flipped = renderTargets.snapshot();
            ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();

            if (source == null || !source.isValid()) {
                if (computes[i] != null) {
                    ComputeOnlyPass pass = new ComputeOnlyPass();
                    pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, renderTargets);
                    passes.add(pass);
                }
                continue;
            }

            Pass pass = new Pass();
            ProgramDirectives directives = source.getDirectives();

            pass.program = createProgram(source, flipped, flippedAtLeastOnceSnapshot, renderTargets);
            pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, renderTargets);
            int[] drawBuffers = directives.hasUnknownDrawBuffers() ? new int[] { 0, 1 } : directives.getDrawBuffers();

            GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flipped, drawBuffers);

            pass.stageReadsFromAlt = flipped;
            pass.framebuffer = framebuffer;
            pass.viewportScale = directives.getViewportScale();
            pass.mipmappedBuffers = directives.getMipmappedBuffers();
            pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;

            passes.add(pass);

            ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();

            // Flip the buffers that this shader wrote to
            for (int buffer : drawBuffers) {
                // compare with boxed Boolean objects to avoid NPEs
                if (explicitFlips.get(buffer) == Boolean.FALSE) {
                    continue;
                }

                renderTargets.flip(buffer);
                flippedAtLeastOnce.add(buffer);
            }

            explicitFlips.forEach((buffer, shouldFlip) -> {
                if (shouldFlip) {
                    renderTargets.flip(buffer);
                    flippedAtLeastOnce.add(buffer);
                }
            });
        }

        this.passes = passes.build();
        this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();

        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    private static void setupMipmapping(RenderTarget target, boolean readFromAlt) {
        if (target == null) {
            return;
        }

        int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();

        // TODO: Only generate the mipmap if a valid mipmap hasn't been generated or if we've written to the buffer
        // (since the last mipmap was generated)
        //
        // NB: We leave mipmapping enabled even if the buffer is written to again, this appears to match the
        // behavior of ShadersMod/OptiFine, however I'm not sure if it's desired behavior. It's possible that a
        // program could use mipmapped sampling with a stale mipmap, which probably isn't great. However, the
        // sampling mode is always reset between frames, so this only persists after the first program to use
        // mipmapping on this buffer.
        //
        // Also note that this only applies to one of the two buffers in a render target buffer pair - making it
        // unlikely that this issue occurs in practice with most shader packs.
        RenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);
        RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, target.getInternalFormat().getPixelFormat().isInteger() ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR);
    }

    private static void resetRenderTarget(RenderTarget target) {
        // Resets the sampling mode of the given render target and then unbinds it to prevent accidental sampling of it
        // elsewhere.

        int filter = GL11.GL_LINEAR;
        if (target.getInternalFormat().getPixelFormat().isInteger()) {
            filter = GL11.GL_NEAREST;
        }

        RenderSystem.texParameteri(target.getMainTexture(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        RenderSystem.texParameteri(target.getAltTexture(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
    }

    public ImmutableSet<Integer> getFlippedAtLeastOnceFinal() {
        return this.flippedAtLeastOnceFinal;
    }

    public void renderAll() {
        GLStateManager.disableBlend();

        FullScreenQuadRenderer.INSTANCE.begin();

        for (Pass renderPass : passes) {
            boolean ranCompute = false;
            for (ComputeProgram computeProgram : renderPass.computes) {
                if (computeProgram != null) {
                    ranCompute = true;
                    final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();
                    computeProgram.use();
                    this.customUniforms.push(computeProgram);
                    computeProgram.dispatch(main.framebufferWidth, main.framebufferHeight);
                }
            }

            if (ranCompute) {
                // Memory barrier for shader image access and texture fetch
                RenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
            }

            Program.unbind();

            if (renderPass instanceof ComputeOnlyPass) {
                continue;
            }

            if (!renderPass.mipmappedBuffers.isEmpty()) {
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

                for (int index : renderPass.mipmappedBuffers) {
                    setupMipmapping(renderTargets.get(index), renderPass.stageReadsFromAlt.contains(index));
                }
            }

            float scaledWidth = renderTargets.getResolution() * renderPass.viewportScale.scale();
            float scaledHeight = renderTargets.getResolution() * renderPass.viewportScale.scale();
            int beginWidth = (int) (renderTargets.getResolution() * renderPass.viewportScale.viewportX());
            int beginHeight = (int) (renderTargets.getResolution() * renderPass.viewportScale.viewportY());
            GLStateManager.glViewport(beginWidth, beginHeight, (int) scaledWidth, (int) scaledHeight);

            renderPass.framebuffer.bind();
            renderPass.program.use();

            this.customUniforms.push(renderPass.program);

            FullScreenQuadRenderer.INSTANCE.renderQuad();
        }

        FullScreenQuadRenderer.INSTANCE.end();

        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();

        for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
            // Reset mipmapping states at the end of the frame.
            if (renderTargets.get(i) != null) {
                resetRenderTarget(renderTargets.get(i));
            }
        }

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }

    // TODO: Don't just copy this from DeferredWorldRenderingPipeline
    private Program createProgram(ProgramSource source, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
            ShadowRenderTargets targets) {
        // TODO: Properly handle empty shaders
        Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(
                source.getVertexSource().orElseThrow(NullPointerException::new),
                source.getGeometrySource().orElse(null),
                source.getFragmentSource().orElseThrow(NullPointerException::new));
        String vertex = transformed.get(PatchShaderType.VERTEX);
        String geometry = transformed.get(PatchShaderType.GEOMETRY);
        String fragment = transformed.get(PatchShaderType.FRAGMENT);
        PatchedShaderPrinter.debugPatchedShaders(source.getName(), vertex, geometry, fragment);

        Objects.requireNonNull(flipped);
        ProgramBuilder builder;

        try {
            builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
        } catch (RuntimeException e) {
            // TODO: Better error handling
            throw new RuntimeException("Shader compilation failed for shadow composite " + source.getName() + "!", e);
        }

        ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
                builder,
                customTextureIds,
                flippedAtLeastOnceSnapshot);

        CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
        this.customUniforms.assignTo(builder);

        IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
        IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

        IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, targets, flipped, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
        IrisImages.addShadowColorImages(builder, targets, flipped);
        IrisImages.addCustomImages(builder, irisCustomImages);
        IrisSamplers.addCustomImages(customTextureSamplerInterceptor, irisCustomImages);
        Program build = builder.build();
        this.customUniforms.mapholderToPass(builder, build);

        return build;
    }

    private ComputeProgram[] createComputes(ComputeSource[] sources, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
            ShadowRenderTargets targets) {
        if (sources == null) {
            return new ComputeProgram[0];
        }

        ComputeProgram[] programs = new ComputeProgram[sources.length];
        for (int i = 0; i < programs.length; i++) {
            ComputeSource source = sources[i];
            if (source == null || !source.getSource().isPresent()) {
                continue;
            } else {
                Objects.requireNonNull(flipped);
                ProgramBuilder builder;

                try {
                    String transformed = TransformPatcher.patchCompute(source.getName(), source.getSource().orElse(null),
                        TextureStage.SHADOWCOMP, pipeline.getTextureMap());
                    PatchedShaderPrinter.debugPatchedShaders(source.getName() + "_compute", null, null, null, transformed);
                    builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
                } catch (RuntimeException e) {
                    // TODO: Better error handling
                    throw new RuntimeException("Shader compilation failed for shadowcomp compute " + source.getName() + "!", e);
                }

                ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(
                        builder,
                        customTextureIds,
                        flippedAtLeastOnceSnapshot);

                CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
                this.customUniforms.assignTo(builder);
                IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
                IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

                IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, targets, flipped, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
                IrisImages.addShadowColorImages(builder, targets, flipped);

                IrisImages.addCustomImages(builder, irisCustomImages);
                IrisSamplers.addCustomImages(customTextureSamplerInterceptor, irisCustomImages);
                programs[i] = builder.buildCompute();

                this.customUniforms.mapholderToPass(builder, programs[i]);

                programs[i].setWorkGroupInfo(
                        source.getWorkGroupRelative(),
                        source.getWorkGroups(),
                        ssboHolder != null ? FilledIndirectPointer.basedOff(ssboHolder, source.getIndirectPointer()) : null);
            }
        }

        return programs;
    }

    public void destroy() {
        for (Pass renderPass : passes) {
            renderPass.destroy();
        }
    }

    private static class Pass {

        Program program;
        GlFramebuffer framebuffer;
        ImmutableSet<Integer> flippedAtLeastOnce;
        ImmutableSet<Integer> stageReadsFromAlt;
        ImmutableSet<Integer> mipmappedBuffers;
        ViewportData viewportScale;
        ComputeProgram[] computes;

        protected void destroy() {
            this.program.destroy();
            for (ComputeProgram compute : this.computes) {
                if (compute != null) {
                    compute.destroy();
                }
            }
        }
    }

    private static class ComputeOnlyPass extends Pass {

        @Override
        protected void destroy() {
            for (ComputeProgram compute : this.computes) {
                if (compute != null) {
                    compute.destroy();
                }
            }
        }
    }
}

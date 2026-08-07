package net.coderbot.iris.postprocess;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.features.FeatureFlags;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.framebuffer.ViewportData;
import net.coderbot.iris.gl.image.GlImage;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.sampler.SamplerLimits;
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.gl.texture.TextureAccess;
import net.coderbot.iris.pipeline.PatchedShaderPrinter;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.PreRasterComputeDispatcher;
import net.coderbot.iris.pipeline.transform.RwImageStoreExtractor;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import org.lwjgl.opengl.GL20;
import net.coderbot.iris.rendertarget.RenderTarget;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.samplers.IrisImages;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shaderpack.ComputeSource;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.ProgramDirectives;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.profiler.Profiler;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public class CompositeRenderer {
	private static final Tracy.ZoneId Z_GEN_MIPMAP = Tracy.zoneId("genMipmap", Tracy.COLOR_IRIS);

	private final RenderTargets renderTargets;

	private final ImmutableList<Pass> passes;
	private final TextureAccess noiseTexture;
	private final FrameUpdateNotifier updateNotifier;
	private final CenterDepthSampler centerDepthSampler;
    private final CustomUniforms customUniforms;
	private final Object2ObjectMap<String, TextureAccess> customTextureIds;
	@Nullable private final Set<GlImage> customImages;
	@Nullable private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
	@Nullable private final WorldRenderingPipeline pipeline;
	private final TextureStage textureStage;
	@Getter private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
	@Getter private int samplerUsage;

	public CompositeRenderer(PackDirectives packDirectives, ProgramSource[] sources, ComputeSource[][] computes, RenderTargets renderTargets,
							 TextureAccess noiseTexture, FrameUpdateNotifier updateNotifier,
							 CenterDepthSampler centerDepthSampler, BufferFlipper bufferFlipper,
							 Supplier<ShadowRenderTargets> shadowTargetsSupplier,
							 Object2ObjectMap<String, TextureAccess> customTextureIds, ImmutableMap<Integer, Boolean> explicitPreFlips,
							 CustomUniforms customUniforms,
							 Map<Integer, CompletableFuture<Map<PatchShaderType, String>>> precomputedTransformFutures) {
		this(sources, computes, bufferFlipper, new ProgramBuildContext(renderTargets, noiseTexture, updateNotifier, centerDepthSampler, shadowTargetsSupplier, customTextureIds, customUniforms, null, null, null), explicitPreFlips, precomputedTransformFutures, "unknown", TextureStage.COMPOSITE_AND_FINAL);
	}

	public CompositeRenderer(ProgramSource[] sources, ComputeSource[][] computes, BufferFlipper bufferFlipper, ProgramBuildContext context, ImmutableMap<Integer, Boolean> explicitPreFlips, Map<Integer, CompletableFuture<Map<PatchShaderType, String>>> precomputedTransformFutures, String stageName, TextureStage textureStage) {
		this.noiseTexture = context.noiseTexture();
		this.updateNotifier = context.updateNotifier();
		this.centerDepthSampler = context.centerDepthSampler();
		this.renderTargets = context.renderTargets();
		this.customTextureIds = context.customTextureIds();
        this.customUniforms = context.customUniforms();
		this.customImages = context.customImages();
		this.irisCustomTextures = context.irisCustomTextures();
		this.pipeline = context.pipeline();
		this.textureStage = textureStage;

		final ImmutableList.Builder<Pass> passes = ImmutableList.builder();
		final ImmutableSet.Builder<Integer> flippedAtLeastOnce = new ImmutableSet.Builder<>();

		Map<Integer, CompletableFuture<Map<PatchShaderType, String>>> transformFutures = precomputedTransformFutures;

		explicitPreFlips.forEach((buffer, shouldFlip) -> {
			if (shouldFlip) {
				bufferFlipper.flip(buffer);
				// NB: Flipping deferred_pre or composite_pre does NOT cause the "flippedAtLeastOnce" flag to trigger
			}
		});

		for (int i = 0; i < sources.length; i++) {
			final ProgramSource source = sources[i];

			ImmutableSet<Integer> flipped = bufferFlipper.snapshot();
			ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();

			if (source == null || !source.isValid()) {
				if (computes[i] != null) {
					final ComputeOnlyPass pass = new ComputeOnlyPass();
					pass.name = computeOnlyPassName(computes[i], stageName, i);
					pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, context.shadowTargetsSupplier());
					passes.add(pass);
				}
				continue;
			}

			final Pass pass = new Pass();
			pass.name = "iris_" + source.getName();
			final ProgramDirectives directives = source.getDirectives();

			final long _passStart = System.nanoTime();
			Map<PatchShaderType, String> transformed = getTransformed(source, transformFutures, i, stageName);
			final long _afterTx = System.nanoTime();
			pass.program = createProgramFromTransformed(source, transformed, flipped, flippedAtLeastOnceSnapshot, context.shadowTargetsSupplier());
			final long _afterProg = System.nanoTime();
			final String preComputeSrc = transformed.get(PatchShaderType.COMPUTE);
			if (preComputeSrc != null) {
				pass.preRasterMode = RwImageStoreExtractor.parseSentinel(preComputeSrc);
				if (pass.preRasterMode != null) {
					pass.preRasterCompute = buildPreRasterCompute(source.getName(), preComputeSrc, flipped, flippedAtLeastOnceSnapshot, context.shadowTargetsSupplier());
				}
			}
			pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, context.shadowTargetsSupplier());
			final long _afterComp = System.nanoTime();
			final int[] drawBuffers = directives.getDrawBuffers();

			final GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flipped, drawBuffers);
			final long _afterFb = System.nanoTime();
			Iris.logger.info("[Load #{}] {}[{}] tx={}ms prog={}ms compute={}ms fb={}ms total={}ms",
				Iris.getShaderPackLoadId(), stageName, i,
				String.format("%.1f", (_afterTx - _passStart) / 1_000_000.0),
				String.format("%.1f", (_afterProg - _afterTx) / 1_000_000.0),
				String.format("%.1f", (_afterComp - _afterProg) / 1_000_000.0),
				String.format("%.1f", (_afterFb - _afterComp) / 1_000_000.0),
				String.format("%.1f", (_afterFb - _passStart) / 1_000_000.0));

			int passWidth = 0, passHeight = 0;
			// Flip the buffers that this shader wrote to, and set pass width and height
			final ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();

			for (int buffer : drawBuffers) {
				final RenderTarget target = renderTargets.get(buffer);
				if ((passWidth > 0 && passWidth != target.getWidth()) || (passHeight > 0 && passHeight != target.getHeight())) {
					throw new IllegalStateException("Pass widths must match");
				}
				passWidth = target.getWidth();
				passHeight = target.getHeight();

				// compare with boxed Boolean objects to avoid NPEs
				if (explicitFlips.get(buffer) == Boolean.FALSE) {
					continue;
				}

				bufferFlipper.flip(buffer);
				flippedAtLeastOnce.add(buffer);
			}

			explicitFlips.forEach((buffer, shouldFlip) -> {
				if (shouldFlip) {
					bufferFlipper.flip(buffer);
					flippedAtLeastOnce.add(buffer);
				}
			});

			pass.drawBuffers = directives.getDrawBuffers();
			pass.viewWidth = passWidth;
			pass.viewHeight = passHeight;
			pass.stageReadsFromAlt = flipped;
			pass.framebuffer = framebuffer;
			pass.viewportScale = directives.getViewportScale();
			pass.mipmappedBuffers = directives.getMipmappedBuffers();

			passes.add(pass);
		}

		this.passes = passes.build();
		this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();

		if (Tracy.ENABLED) {
			for (Pass pass : this.passes) {
				Tracy.message("passGraph " + stageName + "/" + pass.name + " draw=" + java.util.Arrays.toString(pass.drawBuffers) + " mipmapped=" + pass.mipmappedBuffers + " readsAlt=" + pass.stageReadsFromAlt);
			}
		}

		OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_READ_FRAMEBUFFER, 0);
	}

	private Map<PatchShaderType, String> getTransformed(ProgramSource source, Map<Integer, CompletableFuture<Map<PatchShaderType, String>>> transformFutures, int index, String stageName) {
		if (transformFutures != null) {
			final CompletableFuture<Map<PatchShaderType, String>> future = transformFutures.get(index);
			if (future != null) {
				try {
					final Map<PatchShaderType, String> result = future.join();
					if (result != null) {
						return result;
					}
				} catch (CompletionException e) {
					throw new RuntimeException("Shader transformation failed for '" + source.getName() + "' in stage '" + stageName + "' (pass " + index + ")", e.getCause() != null ? e.getCause() : e);
				}
			}
		}

		// Fallback: transform synchronously
		return TransformPatcher.patchComposite(source.getVertexSource().orElseThrow(NullPointerException::new), source.getGeometrySource().orElse(null), source.getTessControlSource().orElse(null), source.getTessEvalSource().orElse(null), source.getFragmentSource().orElseThrow(NullPointerException::new), this.textureStage, pipeline != null ? pipeline.getTextureMap() : null);
	}

    public void recalculateSizes() {
		for (Pass pass : passes) {
			if (pass instanceof ComputeOnlyPass) {
				continue;
			}
			int passWidth = 0, passHeight = 0;
			for (int buffer : pass.drawBuffers) {
				final RenderTarget target = renderTargets.get(buffer);
				if ((passWidth > 0 && passWidth != target.getWidth()) || (passHeight > 0 && passHeight != target.getHeight())) {
					throw new IllegalStateException("Pass widths must match");
				}
				passWidth = target.getWidth();
				passHeight = target.getHeight();
			}
			renderTargets.destroyFramebuffer(pass.framebuffer);
            pass.framebuffer = renderTargets.createColorFramebuffer(pass.stageReadsFromAlt, pass.drawBuffers);
            pass.viewWidth = passWidth;
			pass.viewHeight = passHeight;
		}
	}

	private static String computeOnlyPassName(ComputeSource[] computes, String stageName, int index) {
		for (ComputeSource compute : computes) {
			if (compute != null) {
				return "iris_" + compute.getName();
			}
		}
		return "iris_" + stageName + "_compute" + index;
	}

	private static class Pass {
		String name;
		int[] drawBuffers;
		int viewWidth;
		int viewHeight;
		Program program;
		ComputeProgram[] computes;
		@Nullable ComputeProgram preRasterCompute;
		@Nullable RwImageStoreExtractor.RwExtractMode preRasterMode;
		int preRasterTargetSizeLoc = -2;
		GlFramebuffer framebuffer;
		ImmutableSet<Integer> stageReadsFromAlt;
		ImmutableSet<Integer> mipmappedBuffers;
		ViewportData viewportScale;
		protected void destroy() {
			this.program.destroy();
			for (ComputeProgram compute : this.computes) {
				if (compute != null) {
					compute.destroy();
				}
			}
			if (this.preRasterCompute != null) {
				this.preRasterCompute.destroy();
				this.preRasterCompute = null;
			}
		}
	}

	private class ComputeOnlyPass extends Pass {
		@Override
		protected void destroy() {
			for (ComputeProgram compute : this.computes) {
				if (compute != null) {
					compute.destroy();
				}
			}
		}
	}

	public void renderAll() {
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();

		FullScreenQuadRenderer.INSTANCE.begin();

		final Profiler profiler = Minecraft.getMinecraft().mcProfiler;

		for (Pass renderPass : passes) {
			profiler.startSection(renderPass.name);
			GLDebug.pushGroup(renderPass.name);
			try {
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
					RenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
					for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
						renderTargets.get(i).markBothDirty();
					}
				}

				Program.unbind();

				if (renderPass instanceof ComputeOnlyPass) {
					continue;
				}

				final ImmutableSet<Integer> readsFromAlt = renderTargets.parityResolve(renderPass.stageReadsFromAlt);

				if (!renderPass.mipmappedBuffers.isEmpty()) {
					GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

					for (int index : renderPass.mipmappedBuffers) {
						setupMipmapping(CompositeRenderer.this.renderTargets.get(index), readsFromAlt.contains(index));
					}
				}

				final float scaledWidth = renderPass.viewWidth * renderPass.viewportScale.scale();
				final float scaledHeight = renderPass.viewHeight * renderPass.viewportScale.scale();
				final float viewportX = renderPass.viewWidth * renderPass.viewportScale.viewportX();
				final float viewportY = renderPass.viewHeight * renderPass.viewportScale.viewportY();
				GLStateManager.glViewport((int) viewportX, (int) viewportY, (int) scaledWidth, (int) scaledHeight);

				if (renderPass.preRasterCompute != null && renderPass.preRasterMode != null) {
					dispatchPreRasterCompute(renderPass);
				}

				renderPass.framebuffer.bind();
				renderPass.program.use();

	            this.customUniforms.push(renderPass.program);
				FullScreenQuadRenderer.uploadCompositeMatrices();

				FullScreenQuadRenderer.INSTANCE.renderQuad();

				for (int buffer : renderPass.drawBuffers) {
					final boolean writeIsAlt = !readsFromAlt.contains(buffer);
					CompositeRenderer.this.renderTargets.get(buffer).markDirty(writeIsAlt);
				}
			} finally {
				GLDebug.popGroup();
				profiler.endSection();
			}
		}

		FullScreenQuadRenderer.end();

		// Make sure to reset the viewport to how it was before... Otherwise weird issues could occur.
		// Also bind the "main" framebuffer if it isn't already bound.
        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		GLStateManager.glUseProgram(0);

		// NB: Unbinding all of these textures is necessary for proper shaderpack reloading.
		final int maxUnit = Math.min(SamplerLimits.get().getMaxTextureUnits() - 1, GLStateManager.getMaxBoundTextureUnit());
		for (int i = 0; i <= maxUnit; i++) {
			// Unbind all textures that we may have used.
			// NB: This is necessary for shader pack reloading to work propely
			GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + i);
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		}

		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
	}

	private void dispatchPreRasterCompute(Pass pass) {
		pass.preRasterTargetSizeLoc = PreRasterComputeDispatcher.dispatch(pass.preRasterCompute, pass.preRasterMode, pass.preRasterTargetSizeLoc, pass.viewWidth, pass.viewHeight, this.customUniforms);
	}

	private static void setupMipmapping(RenderTarget target, boolean readFromAlt) {
		int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();

		// Only regen when the sampled side has been written since the last regen.
		if (target.isDirty(readFromAlt)) {
			if (Tracy.ENABLED) {
				Tracy.beginZone(Z_GEN_MIPMAP);
				Tracy.zoneValue(texture);
			}
			try {
				RenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);
			} finally {
				if (Tracy.ENABLED) Tracy.endZone();
			}
			target.clearDirty(readFromAlt);
		}

		int filter = GL11.GL_LINEAR_MIPMAP_LINEAR;
		if (target.getInternalFormat().getPixelFormat().isInteger()) {
			filter = GL11.GL_NEAREST_MIPMAP_NEAREST;
		}

		RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
	}

	// TODO: Don't just copy this from DeferredWorldRenderingPipeline
	private Program createProgramFromTransformed(ProgramSource source, Map<PatchShaderType, String> transformed,
												 ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
												 Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
		String vertex = transformed.get(PatchShaderType.VERTEX);
		String geometry = transformed.get(PatchShaderType.GEOMETRY);
		String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
		String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
		String fragment = transformed.get(PatchShaderType.FRAGMENT);
		PatchedShaderPrinter.debugPatchedShaders(source.getName(), vertex, geometry, tessControl, tessEval, fragment);

		Objects.requireNonNull(flipped);
		ProgramBuilder builder;

		final long _cpStart = System.nanoTime();
		try {
			builder = ProgramBuilder.begin(source.getName(), vertex, geometry, tessControl, tessEval, fragment,
				IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
		} catch (RuntimeException e) {
			// TODO: Better error handling
			throw new RuntimeException("Shader compilation failed!", e);
		}
		final long _afterBegin = System.nanoTime();

        CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
        this.customUniforms.assignTo(builder);

		ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> renderTargets.parityResolve(flipped), renderTargets, true, pipeline);
		IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
		IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

		IrisImages.addRenderTargetImages(builder, () -> renderTargets.parityResolve(flipped), renderTargets);
		IrisImages.addCustomImages(builder, customImages);

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
		samplerUsage |= IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
			samplerUsage |= IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, pipeline != null && pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
			IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
		}

		// TODO: Don't duplicate this with FinalPassRenderer
		centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));
		final long _afterSamplers = System.nanoTime();

		Program build = builder.build();
		final long _afterBuild = System.nanoTime();

	    this.customUniforms.mapholderToPass(builder, build);
		final long _afterMapholder = System.nanoTime();
		Iris.logger.info("[Load #{}]   createProg name={} begin={}ms samplers={}ms build={}ms mapholder={}ms",
			Iris.getShaderPackLoadId(), source.getName(),
			String.format("%.1f", (_afterBegin - _cpStart) / 1_000_000.0),
			String.format("%.1f", (_afterSamplers - _afterBegin) / 1_000_000.0),
			String.format("%.1f", (_afterBuild - _afterSamplers) / 1_000_000.0),
			String.format("%.1f", (_afterMapholder - _afterBuild) / 1_000_000.0));

        return build;
    }

	private ComputeProgram buildPreRasterCompute(String name, String computeSource, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot, Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
		PatchedShaderPrinter.debugPatchedShaders(name + "_pre_compute", null, null, null, computeSource);
		final ProgramBuilder builder;
		try {
			builder = ProgramBuilder.beginCompute(name + "_pre", computeSource, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
		} catch (RuntimeException e) {
			throw new RuntimeException("Pre-raster compute compilation failed for " + name, e);
		}
		final ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

		CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
		this.customUniforms.assignTo(builder);

		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true, pipeline);
		IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
		IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

		IrisImages.addRenderTargetImages(builder, () -> flipped, renderTargets);
		IrisImages.addCustomImages(builder, customImages);

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
		IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
			IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, pipeline != null && pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
			IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
		}

		final ComputeProgram cp = builder.buildCompute();
		this.customUniforms.mapholderToPass(builder, cp);
		return cp;
	}

	private ComputeProgram[] createComputes(ComputeSource[] compute, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot, Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
		ComputeProgram[] programs = new ComputeProgram[compute.length];
		for (int i = 0; i < programs.length; i++) {
			ComputeSource source = compute[i];
			if (source == null || !source.getSource().isPresent()) {
				continue;
			} else {
				// TODO: Properly handle empty shaders
				Objects.requireNonNull(flipped);
				ProgramBuilder builder;

				try {
					String transformed = TransformPatcher.patchCompute(source.getName(), source.getSource().orElse(null), this.textureStage, pipeline != null ? pipeline.getTextureMap() : null);
					PatchedShaderPrinter.debugPatchedShaders(source.getName() + "_compute", null, null, null, transformed);
					builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
				} catch (RuntimeException e) {
					// TODO: Better error handling
					throw new RuntimeException("Shader compilation failed!", e);
				}

				ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

				CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);

                this.customUniforms.assignTo(builder);

				IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> renderTargets.parityResolve(flipped), renderTargets, true, pipeline);
				IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
				IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

				IrisImages.addRenderTargetImages(builder, () -> renderTargets.parityResolve(flipped), renderTargets);
				IrisImages.addCustomImages(builder, customImages);

				IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
				samplerUsage |= IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

				if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
					samplerUsage |= IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, pipeline != null && pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
					IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
				}

				// TODO: Don't duplicate this with FinalPassRenderer
				centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

				programs[i] = builder.buildCompute();

                customUniforms.mapholderToPass(builder, programs[i]);

				programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups(), null);
			}
		}


		return programs;
	}

	public void destroy() {
		for (Pass renderPass : passes) {
			renderPass.destroy();
		}
	}
}

package net.coderbot.iris.postprocess;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.stereo.StereoState;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.features.FeatureFlags;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
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
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.coderbot.iris.rendertarget.RenderTarget;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.samplers.IrisImages;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shaderpack.ComputeSource;
import net.coderbot.iris.shaderpack.ProgramDirectives;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;

import java.nio.IntBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public class FinalPassRenderer {
	private final RenderTargets renderTargets;

	@Nullable
	private final Pass finalPass;
	private final ImmutableList<SwapPass> swapPasses;
	private final GlFramebuffer baseline;
	private final GlFramebuffer colorHolder;
	private int lastColorTextureId;
	private int lastColorTextureVersion;
	private final TextureAccess noiseTexture;
	private final FrameUpdateNotifier updateNotifier;
	private final CenterDepthSampler centerDepthSampler;
	private final Object2ObjectMap<String, TextureAccess> customTextureIds;
    private final CustomUniforms customUniforms;
	@Nullable private final Set<GlImage> customImages;
	@Nullable private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
	@Nullable private final WorldRenderingPipeline pipeline;

	// Stereo: an eye-sized scratch color texture used as the final-pass shader's output target
	// instead of the main FB color, so the shader's gl_FragCoord-based UV sampling stays correct
	// at viewport (0,0,halfW,fullH). After the shader runs we blit the scratch into the eye's
	// region of the main FB color. Lazily (re-)allocated to match the current eye size.
	private int stereoScratchTexture = -1;
	private GlFramebuffer stereoScratchFb;
	private int stereoScratchW;
	private int stereoScratchH;

	public FinalPassRenderer(ProgramSet pack, RenderTargets renderTargets, TextureAccess noiseTexture,
							 FrameUpdateNotifier updateNotifier, ImmutableSet<Integer> flippedBuffers,
							 CenterDepthSampler centerDepthSampler,
							 Supplier<ShadowRenderTargets> shadowTargetsSupplier,
							 Object2ObjectMap<String, TextureAccess> customTextureIds,
							 ImmutableSet<Integer> flippedAtLeastOnce, CustomUniforms customUniforms,
							 CompletableFuture<Map<PatchShaderType, String>> precomputedTransformFuture) {
		this(pack, new ProgramBuildContext(renderTargets, noiseTexture, updateNotifier, centerDepthSampler, shadowTargetsSupplier, customTextureIds, customUniforms, null, null, null), flippedBuffers, flippedAtLeastOnce, precomputedTransformFuture, "final");
	}

	public FinalPassRenderer(ProgramSet pack, ProgramBuildContext context, ImmutableSet<Integer> flippedBuffers, ImmutableSet<Integer> flippedAtLeastOnce, CompletableFuture<Map<PatchShaderType, String>> precomputedTransformFuture, String stageName) {
		this.updateNotifier = context.updateNotifier();
		this.centerDepthSampler = context.centerDepthSampler();
		this.customTextureIds = context.customTextureIds();

		this.noiseTexture = context.noiseTexture();
		this.renderTargets = context.renderTargets();
        this.customUniforms = context.customUniforms();
		this.customImages = context.customImages();
		this.irisCustomTextures = context.irisCustomTextures();
		this.pipeline = context.pipeline();
		this.finalPass = pack.getCompositeFinal().filter(ProgramSource::isValid).map(source -> {
			final Pass pass = new Pass();
			final ProgramDirectives directives = source.getDirectives();

			final Map<PatchShaderType, String> transformed = getTransformed(source, precomputedTransformFuture, stageName);
			pass.program = createProgramFromTransformed(source, transformed, flippedBuffers, flippedAtLeastOnce, context.shadowTargetsSupplier());
			pass.computes = createComputes(pack.getFinalCompute(), flippedBuffers, flippedAtLeastOnce, context.shadowTargetsSupplier());
			pass.stageReadsFromAlt = flippedBuffers;
			pass.mipmappedBuffers = directives.getMipmappedBuffers();

			return pass;
		}).orElse(null);

		final IntList buffersToBeCleared = pack.getPackDirectives().getRenderTargetDirectives().getBuffersToBeCleared();

		// The name of this method might seem a bit odd here, but we want a framebuffer with color attachments that line
		// up with whatever was written last (since we're reading from these framebuffers) instead of trying to create
		// a framebuffer with color attachments different from what was written last (as we do with normal composite
		// passes that write to framebuffers).
        final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();
		this.baseline = renderTargets.createGbufferFramebuffer(flippedBuffers, new int[] {0});
		this.colorHolder = new GlFramebuffer();
        this.lastColorTextureId = main.framebufferTexture;
		this.lastColorTextureVersion = ((IRenderTargetExt)main).iris$getColorBufferVersion();
		this.colorHolder.addColorAttachment(0, lastColorTextureId);

		// TODO: We don't actually fully swap the content, we merely copy it from alt to main
		// This works for the most part, but it's not perfect. A better approach would be creating secondary
		// framebuffers for every other frame, but that would be a lot more complex...

		final ImmutableList.Builder<SwapPass> swapPasses = ImmutableList.builder();

		flippedBuffers.forEach((i) -> {
			final int target = i;

			if (buffersToBeCleared.contains(target)) {
				return;
			}

			final SwapPass swap = new SwapPass();
			final RenderTarget target1 = renderTargets.get(target);
			swap.target = target;
			swap.width = target1.getWidth();
			swap.height = target1.getHeight();
			// Non-flipped buffers write to ALT, copy ALT→MAIN to preserve data
			swap.from = renderTargets.createColorFramebuffer(ImmutableSet.of(), new int[] {target});
			swap.targetTexture = renderTargets.get(target).getMainTexture();

			swapPasses.add(swap);
		});

		this.swapPasses = swapPasses.build();

		GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
	}

	private Map<PatchShaderType, String> getTransformed(ProgramSource source, CompletableFuture<Map<PatchShaderType, String>> precomputedTransformFuture, String stageName) {
		if (precomputedTransformFuture != null) {
			try {
				final Map<PatchShaderType, String> result = precomputedTransformFuture.join();
				if (result != null) {
					return result;
				}
			} catch (CompletionException e) {
				throw new RuntimeException("Shader transformation failed for '" + source.getName() + "' in stage '" + stageName + "'", e.getCause() != null ? e.getCause() : e);
			}
		}
		return TransformPatcher.patchComposite(
			source.getVertexSource().orElseThrow(NullPointerException::new),
			source.getGeometrySource().orElse(null),
			source.getTessControlSource().orElse(null),
			source.getTessEvalSource().orElse(null),
			source.getFragmentSource().orElseThrow(NullPointerException::new),
			TextureStage.COMPOSITE_AND_FINAL,
			pipeline != null ? pipeline.getTextureMap() : null);
	}

	private static final class Pass {
		Program program;
		ComputeProgram[] computes;
		ImmutableSet<Integer> stageReadsFromAlt;
		ImmutableSet<Integer> mipmappedBuffers;

		private void destroy() {
			this.program.destroy();
		}
	}

	private static final class SwapPass {
		public int target;
		public int width;
		public int height;
		GlFramebuffer from;
		int targetTexture;
	}

	public void renderFinalPass() {
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        GLStateManager.glDepthMask(false);

        final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();
		// During a stereo eye pass, Iris's intermediate framebuffers are sized to the eye's
		// viewport (half the display width for SBS_HALF). The compute dispatch and copy sizes
		// below match that. When a shader final pass exists, we direct it to an eye-sized
		// scratch texture (so its viewport-relative gl_FragCoord math stays correct) and blit
		// the scratch into the eye's region of the main FB color afterwards.
		final StereoState stereoFinal = StereoState.INSTANCE;
		final int baseWidth = stereoFinal.irisFbWidth(main.framebufferWidth);
		final int baseHeight = stereoFinal.irisFbHeight(main.framebufferHeight);
		final boolean stereoActive = stereoFinal.isInWorldPass() && stereoFinal.isActive();

		// Note that since DeferredWorldRenderingPipeline uses the depth texture of the main Minecraft framebuffer,
		// we'll be writing to that depth buffer directly automatically and won't need to futz around with copying
		// depth buffer content.
		//
		// Previously, we had our own depth texture and then copied its content to the main Minecraft framebuffer.
		// This worked with vanilla, but broke with mods that used the stencil buffer.
		//
		// This approach is a fairly succinct solution to the issue of having to deal with the main Minecraft
		// framebuffer potentially having a depth-stencil buffer or similar - we'll automatically enable that to
		// work properly since we re-use the depth buffer instead of trying to make our own.
		//
		// This is not a concern for depthtex1 / depthtex2 since the copy call extracts the depth values, and the
		// shader pack only ever uses them to read the depth values.
		if (((IRenderTargetExt)main).iris$getColorBufferVersion() != lastColorTextureVersion || main.framebufferTexture != lastColorTextureId) {
			lastColorTextureVersion = ((IRenderTargetExt)main).iris$getColorBufferVersion();
			this.lastColorTextureId = main.framebufferTexture;
			colorHolder.addColorAttachment(0, lastColorTextureId);
		}

		if (this.finalPass != null) {
			// If there is a final pass, we use the shader-based full screen quad rendering pathway instead of just copying the color buffer.

			if (stereoActive) {
				ensureStereoScratch(baseWidth, baseHeight);
				stereoScratchFb.bind();
				// CompositeRenderer.renderAll's final bindFramebuffer fires MixinFramebuffer_Stereo's
				// viewport restore, leaving us with an *eye-region* viewport in main-FB coords. That's
				// wrong for writing into the per-eye scratch — it would only fill the eye's sub-rect of
				// scratch (leaving the rest from the other eye or the previous frame). Reset to fill
				// the full scratch so the shader writes the complete eye view.
				GL11.glViewport(0, 0, baseWidth, baseHeight);
			} else {
				colorHolder.bind();
			}

			FullScreenQuadRenderer.INSTANCE.begin();

			for (ComputeProgram computeProgram : finalPass.computes) {
				if (computeProgram != null) {
                    computeProgram.use();
                    this.customUniforms.push(computeProgram);
					computeProgram.dispatch(baseWidth, baseHeight);
				}
			}

			RenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);

			if (!finalPass.mipmappedBuffers.isEmpty()) {
				GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

				for (int index : finalPass.mipmappedBuffers) {
					setupMipmapping(renderTargets.get(index), finalPass.stageReadsFromAlt.contains(index));
				}
			}

			finalPass.program.use();

            this.customUniforms.push(finalPass.program);
			FullScreenQuadRenderer.uploadCompositeMatrices();

			FullScreenQuadRenderer.INSTANCE.renderQuad();

			FullScreenQuadRenderer.end();

			if (stereoActive) {
				// Blit the eye-sized scratch into the eye's region of the main FB color texture.
				final int dx0 = stereoFinal.getEyeVpX();
				final int dy0 = stereoFinal.getEyeVpY();
				final int dx1 = dx0 + stereoFinal.getEyeVpW();
				final int dy1 = dy0 + stereoFinal.getEyeVpH();
				RenderSystem.blitFramebuffer(stereoScratchFb.getId(), main.framebufferObject,
					0, 0, baseWidth, baseHeight,
					dx0, dy0, dx1, dy1,
					GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
			}
		} else {
			// If there are no passes, we somehow need to transfer the content of the Iris color render targets into
			// the main Minecraft framebuffer.
			//
			// Thus, the following call transfers the content of colortex0 into the main Minecraft framebuffer.
			//
			// Note that glCopyTexSubImage2D is not as strict as glBlitFramebuffer, so we don't have to worry about
			// colortex0 having a weird format. This should just work.
			//
			// We could have used a shader here, but it should be about the same performance either way:
			// https://stackoverflow.com/a/23994979/18166885
			this.baseline.bindAsReadBuffer();

			final int destX = stereoActive ? stereoFinal.getEyeVpX() : 0;
			final int destY = stereoActive ? stereoFinal.getEyeVpY() : 0;
			RenderSystem.copyTexSubImage2D(main.framebufferTexture, GL11.GL_TEXTURE_2D, 0, destX, destY, 0, 0, baseWidth, baseHeight);
		}

		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

		for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
			// Reset mipmapping states at the end of the frame.
			resetRenderTarget(renderTargets.get(i));
		}

		for (SwapPass swapPass : swapPasses) {
			// NB: We need to use bind(), not bindAsReadBuffer()... Previously we used bindAsReadBuffer() here which
			//     broke TAA on many packs and on many drivers.
			//
			// Note that glCopyTexSubImage2D reads from the current GL_READ_BUFFER (given by glReadBuffer()) for the
			// current framebuffer bound to GL_FRAMEBUFFER, but that is distinct from the current GL_READ_FRAMEBUFFER,
			// which is what bindAsReadBuffer() binds.
			//
			// Also note that RenderTargets already calls readBuffer(0) for us.
			swapPass.from.bind();

			// Resolve the destination texture dynamically per eye: in stereo, renderTargets.get(...)
			// returns the current eye's RenderTarget, whereas a pre-captured texture ID would point
			// at the eye that was active when the SwapPass was first built — causing each eye to
			// scribble into the other eye's color target each frame.
			final RenderTarget target = renderTargets.get(swapPass.target);
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, target.getMainTexture());
            GLStateManager.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, swapPass.width, swapPass.height);
		}

		// Make sure to reset the viewport to how it was before... Otherwise weird issues could occur.
		// Also bind the "main" framebuffer if it isn't already bound.
        main.bindFramebuffer(true);
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		GLStateManager.glUseProgram(0);

		for (int i = 0; i < SamplerLimits.get().getMaxTextureUnits(); i++) {
			// Unbind all textures that we may have used.
			// NB: This is necessary for shader pack reloading to work properly
			GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + i);
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		}

		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
	}

	/**
	 * Lazily allocates (or resizes) the eye-sized scratch color texture and its framebuffer.
	 * Used as the final-pass shader's output target during stereo so its viewport-relative
	 * sampling stays correct. We re-attach to the framebuffer on resize since GlFramebuffer's
	 * addColorAttachment is idempotent for the same attachment index.
	 */
	private void ensureStereoScratch(int width, int height) {
		if (stereoScratchTexture < 0) {
			stereoScratchTexture = GL11.glGenTextures();
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, stereoScratchTexture);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
			stereoScratchW = width;
			stereoScratchH = height;
			stereoScratchFb = new GlFramebuffer();
			stereoScratchFb.addColorAttachment(0, stereoScratchTexture);
		} else if (width != stereoScratchW || height != stereoScratchH) {
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, stereoScratchTexture);
			GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
			stereoScratchW = width;
			stereoScratchH = height;
		}
	}

	public void recalculateSwapPassSize() {
		for (SwapPass swapPass : swapPasses) {
			final RenderTarget target = renderTargets.get(swapPass.target);
			renderTargets.destroyFramebuffer(swapPass.from);
			// Match constructor logic - swap passes are only for non-flipped buffers
			// Copy ALT→MAIN to preserve data written to ALT
			swapPass.from = renderTargets.createColorFramebuffer(ImmutableSet.of(), new int[] {swapPass.target});
			swapPass.width = target.getWidth();
			swapPass.height = target.getHeight();
			swapPass.targetTexture = target.getMainTexture();
		}
	}

	private static void setupMipmapping(RenderTarget target, boolean readFromAlt) {
		final int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();

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

		int filter = GL11.GL_LINEAR_MIPMAP_LINEAR;
		if (target.getInternalFormat().getPixelFormat().isInteger()) {
			filter = GL11.GL_NEAREST_MIPMAP_NEAREST;
		}

		RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
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

		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	// TODO: Don't just copy this from DeferredWorldRenderingPipeline
	private Program createProgramFromTransformed(ProgramSource source, Map<PatchShaderType, String> transformed,
												 ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
												 Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
		final String vertex = transformed.get(PatchShaderType.VERTEX);
		final String geometry = transformed.get(PatchShaderType.GEOMETRY);
		final String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
		final String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
		final String fragment = transformed.get(PatchShaderType.FRAGMENT);
		PatchedShaderPrinter.debugPatchedShaders(source.getName(), vertex, geometry, tessControl, tessEval, fragment);

		Objects.requireNonNull(flipped);
		final ProgramBuilder builder;

		try {
			builder = ProgramBuilder.begin(source.getName(), vertex, geometry, tessControl, tessEval, fragment,
				IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
		} catch (RuntimeException e) {
			// TODO: Better error handling
			throw new RuntimeException("Shader compilation failed!", e);
		}

        this.customUniforms.assignTo(builder);

		final ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

		CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true, pipeline);
		// Bind custom images as samplers BEFORE render target images
		IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);
		// Bind custom textures (PNG files from shader pack)
		IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

		IrisImages.addRenderTargetImages(builder, () -> flipped, renderTargets);
		// Bind custom images as image units AFTER render target images
		IrisImages.addCustomImages(builder, customImages);

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
		IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
			IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, pipeline != null && pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
			IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
		}

		// TODO: Don't duplicate this with CompositeRenderer
		centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

        Program build = builder.build();

        this.customUniforms.mapholderToPass(builder, build);

		return build;
	}

	private ComputeProgram[] createComputes(ComputeSource[] compute, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot, Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
		final ComputeProgram[] programs = new ComputeProgram[compute.length];
		for (int i = 0; i < programs.length; i++) {
			final ComputeSource source = compute[i];
			if (source == null || !source.getSource().isPresent()) {
				continue;
			} else {
				// TODO: Properly handle empty shaders
				Objects.requireNonNull(flipped);
				final ProgramBuilder builder;

				try {
					String transformed = TransformPatcher.patchCompute(source.getName(), source.getSource().orElse(null), TextureStage.COMPOSITE_AND_FINAL, pipeline != null ? pipeline.getTextureMap() : null);
					PatchedShaderPrinter.debugPatchedShaders(source.getName() + "_compute", null, null, null, transformed);
					builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
				} catch (RuntimeException e) {
					// TODO: Better error handling
					throw new RuntimeException("Shader compilation failed!", e);
				}

                ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

                customUniforms.assignTo(builder);

				CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
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

				// TODO: Don't duplicate this with FinalPassRenderer
				centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

				programs[i] = builder.buildCompute();

                this.customUniforms.mapholderToPass(builder, programs[i]);

				programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups(), null);
			}
		}


		return programs;
	}

	public void destroy() {
		if (finalPass != null) {
			finalPass.destroy();
		}
		colorHolder.destroy();
		if (stereoScratchFb != null) {
			stereoScratchFb.destroy();
			stereoScratchFb = null;
		}
		if (stereoScratchTexture >= 0) {
			GLStateManager.glDeleteTextures(stereoScratchTexture);
			stereoScratchTexture = -1;
		}
	}
}

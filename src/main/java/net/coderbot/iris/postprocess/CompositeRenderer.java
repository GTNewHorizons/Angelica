package net.coderbot.iris.postprocess;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import lombok.Getter;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.sampler.SamplerLimits;
import net.coderbot.iris.pipeline.PatchedShaderPrinter;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import net.coderbot.iris.rendertarget.RenderTarget;
import net.coderbot.iris.rendertarget.RenderTargets;
import net.coderbot.iris.samplers.IrisImages;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shaderpack.ComputeSource;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackRenderTargetDirectives;
import net.coderbot.iris.shaderpack.ProgramDirectives;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class CompositeRenderer {
	private final RenderTargets renderTargets;

	private final ImmutableList<Pass> passes;
	private final IntSupplier noiseTexture;
	private final FrameUpdateNotifier updateNotifier;
	private final CenterDepthSampler centerDepthSampler;
	private final Object2ObjectMap<String, IntSupplier> customTextureIds;
	@Getter
    private final ImmutableSet<Integer> flippedAtLeastOnceFinal;

	public CompositeRenderer(PackDirectives packDirectives, ProgramSource[] sources, ComputeSource[][] computes, RenderTargets renderTargets,
							 IntSupplier noiseTexture, FrameUpdateNotifier updateNotifier,
							 CenterDepthSampler centerDepthSampler, BufferFlipper bufferFlipper,
							 Supplier<ShadowRenderTargets> shadowTargetsSupplier,
							 Object2ObjectMap<String, IntSupplier> customTextureIds, ImmutableMap<Integer, Boolean> explicitPreFlips) {
		this.noiseTexture = noiseTexture;
		this.updateNotifier = updateNotifier;
		this.centerDepthSampler = centerDepthSampler;
		this.renderTargets = renderTargets;
		this.customTextureIds = customTextureIds;

		final PackRenderTargetDirectives renderTargetDirectives = packDirectives.getRenderTargetDirectives();
		final Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings = renderTargetDirectives.getRenderTargetSettings();

		final ImmutableList.Builder<Pass> passes = ImmutableList.builder();
		final ImmutableSet.Builder<Integer> flippedAtLeastOnce = new ImmutableSet.Builder<>();

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
					ComputeOnlyPass pass = new ComputeOnlyPass();
					pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier);
					passes.add(pass);
				}
				continue;
			}

			Pass pass = new Pass();
			ProgramDirectives directives = source.getDirectives();

			pass.program = createProgram(source, flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier);
			pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier);
			int[] drawBuffers = directives.getDrawBuffers();

			GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flipped, drawBuffers);

			int passWidth = 0, passHeight = 0;
			// Flip the buffers that this shader wrote to, and set pass width and height
			ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();

			for (int buffer : drawBuffers) {
				RenderTarget target = renderTargets.get(buffer);
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
			pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;

			passes.add(pass);
		}

		this.passes = passes.build();
		this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();

		OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_READ_FRAMEBUFFER, 0);
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

	private static class Pass {
		int[] drawBuffers;
		int viewWidth;
		int viewHeight;
		Program program;
		ComputeProgram[] computes;
		GlFramebuffer framebuffer;
		ImmutableSet<Integer> flippedAtLeastOnce;
		ImmutableSet<Integer> stageReadsFromAlt;
		ImmutableSet<Integer> mipmappedBuffers;
		float viewportScale;

		protected void destroy() {
			this.program.destroy();
			for (ComputeProgram compute : this.computes) {
				if (compute != null) {
					compute.destroy();
				}
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

		for (Pass renderPass : passes) {
			boolean ranCompute = false;
			for (ComputeProgram computeProgram : renderPass.computes) {
				if (computeProgram != null) {
					ranCompute = true;
                    final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();
					computeProgram.dispatch(main.framebufferWidth, main.framebufferHeight);
				}
			}

			if (ranCompute) {
				RenderSystem.memoryBarrier(40);
			}

			Program.unbind();

			if (renderPass instanceof ComputeOnlyPass) {
				continue;
			}

			if (!renderPass.mipmappedBuffers.isEmpty()) {
				GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

				for (int index : renderPass.mipmappedBuffers) {
					setupMipmapping(CompositeRenderer.this.renderTargets.get(index), renderPass.stageReadsFromAlt.contains(index));
				}
			}

			final float scaledWidth = renderPass.viewWidth * renderPass.viewportScale;
			final float scaledHeight = renderPass.viewHeight * renderPass.viewportScale;
			GL11.glViewport(0, 0, (int) scaledWidth, (int) scaledHeight);

			renderPass.framebuffer.bind();
			renderPass.program.use();

			FullScreenQuadRenderer.INSTANCE.renderQuad();
		}

		FullScreenQuadRenderer.end();

		// Make sure to reset the viewport to how it was before... Otherwise weird issues could occur.
		// Also bind the "main" framebuffer if it isn't already bound.
        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		GL20.glUseProgram(0);

		// NB: Unbinding all of these textures is necessary for proper shaderpack reloading.
		for (int i = 0; i < SamplerLimits.get().getMaxTextureUnits(); i++) {
			// Unbind all textures that we may have used.
			// NB: This is necessary for shader pack reloading to work propely
			GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + i);
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		}

		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
	}

	private static void setupMipmapping(RenderTarget target, boolean readFromAlt) {
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

		int filter = GL11.GL_LINEAR_MIPMAP_LINEAR;
		if (target.getInternalFormat().getPixelFormat().isInteger()) {
			filter = GL11.GL_NEAREST_MIPMAP_NEAREST;
		}

		RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
	}

	// TODO: Don't just copy this from DeferredWorldRenderingPipeline
	private Program createProgram(ProgramSource source, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
														   Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
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
			builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment,
				IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
		} catch (RuntimeException e) {
			// TODO: Better error handling
			throw new RuntimeException("Shader compilation failed!", e);
		}

		ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

		CommonUniforms.addCommonUniforms(builder, source.getParent().getPack().getIdMap(), source.getParent().getPackDirectives(), updateNotifier);
		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true);
		IrisImages.addRenderTargetImages(builder, () -> flipped, renderTargets);

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
		IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
			IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get());
			IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get());
		}

		// TODO: Don't duplicate this with FinalPassRenderer
		centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

		return builder.build();
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
					builder = ProgramBuilder.beginCompute(source.getName(), source.getSource().orElse(null), IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
				} catch (RuntimeException e) {
					// TODO: Better error handling
					throw new RuntimeException("Shader compilation failed!", e);
				}

				ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

				CommonUniforms.addCommonUniforms(builder, source.getParent().getPack().getIdMap(), source.getParent().getPackDirectives(), updateNotifier);
				IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true);
				IrisImages.addRenderTargetImages(builder, () -> flipped, renderTargets);

				IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
				IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

				if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
					IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get());
					IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get());
				}

				// TODO: Don't duplicate this with FinalPassRenderer
				centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

				programs[i] = builder.buildCompute();

				programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());
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

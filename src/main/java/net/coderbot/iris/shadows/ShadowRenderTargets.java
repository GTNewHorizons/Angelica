package net.coderbot.iris.shadows;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import net.coderbot.iris.gl.texture.DepthCopyStrategy;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.rendertarget.DepthTexture;
import net.coderbot.iris.rendertarget.RenderTarget;
import net.coderbot.iris.shaderpack.PackShadowDirectives;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class ShadowRenderTargets {
	private final RenderTarget[] targets;
	private final PackShadowDirectives shadowDirectives;
	private final DepthTexture mainDepth;
	private final DepthTexture noTranslucents;
	private final GlFramebuffer depthSourceFb;
	private final GlFramebuffer noTranslucentsDestFb;
	private final boolean[] flipped;

	private final List<GlFramebuffer> ownedFramebuffers;
	private final int resolution;

	private boolean fullClearRequired;
	private boolean translucentDepthDirty;
	private final boolean[] hardwareFiltered;
	private final boolean[] linearFiltered;
	private final InternalTextureFormat[] formats;
	private final IntList buffersToBeCleared;

	public ShadowRenderTargets(int resolution, PackShadowDirectives shadowDirectives) {
		this.shadowDirectives = shadowDirectives;
		this.resolution = resolution;

		final int size = shadowDirectives.getColorSamplingSettings().size();
		targets = new RenderTarget[size];
		formats = new InternalTextureFormat[size];
		flipped = new boolean[size];
		hardwareFiltered = new boolean[shadowDirectives.getDepthSamplingSettings().size()];
		linearFiltered = new boolean[shadowDirectives.getDepthSamplingSettings().size()];
		buffersToBeCleared = new IntArrayList();

		this.mainDepth = new DepthTexture(resolution, resolution, DepthBufferFormat.DEPTH);
		this.noTranslucents = new DepthTexture(resolution, resolution, DepthBufferFormat.DEPTH);

		this.ownedFramebuffers = new ArrayList<>();

		// Populate hardware/linear filtering settings from depth sampling settings
		for (int i = 0; i < shadowDirectives.getDepthSamplingSettings().size(); i++) {
			this.hardwareFiltered[i] = shadowDirectives.getDepthSamplingSettings().get(i).getHardwareFiltering();
			this.linearFiltered[i] = !shadowDirectives.getDepthSamplingSettings().get(i).getNearest();
		}

		// NB: Make sure all buffers are cleared so that they don't contain undefined
		// data. Otherwise very weird things can happen.
		fullClearRequired = true;

		// Create target 0 eagerly - it's needed for framebuffer creation and almost always used
		createIfEmpty(0);

		this.depthSourceFb = createFramebufferWritingToMain(new int[] {0});

		this.noTranslucentsDestFb = createFramebufferWritingToMain(new int[] {0});
		this.noTranslucentsDestFb.addDepthAttachment(this.noTranslucents.getTextureId());

		this.translucentDepthDirty = true;
	}

	private void create(int index) {
		if (index >= targets.length) {
			throw new IllegalStateException("Tried to create shadow color buffer " + index + " but only " + targets.length + " are supported.");
		}

		PackShadowDirectives.SamplingSettings settings = shadowDirectives.getColorSamplingSettings().get(index);
		targets[index] = RenderTarget.builder()
			.setDimensions(resolution, resolution)
			.setInternalFormat(settings.getFormat())
			.setPixelFormat(settings.getFormat().getPixelFormat()).build();
		formats[index] = settings.getFormat();

		if (settings.getClear()) {
			buffersToBeCleared.add(index);
		}

		fullClearRequired = true;
	}

	public void createIfEmpty(int index) {
		if (targets[index] == null) {
			create(index);
		}
	}

	public RenderTarget getOrCreate(int index) {
		createIfEmpty(index);
		return targets[index];
	}

	// TODO: Actually flip. This is required for shadow composites!
	public void flip(int target) {
		flipped[target] = !flipped[target];
	}

	public boolean isFlipped(int target) {
		return flipped[target];
	}

	public void destroy() {
		for (GlFramebuffer owned : ownedFramebuffers) {
			owned.destroy();
		}

		for (RenderTarget target : targets) {
			if (target != null) {
				target.destroy();
			}
		}

		mainDepth.destroy();
		noTranslucents.destroy();
	}

	public int getRenderTargetCount() {
		return targets.length;
	}

	public RenderTarget get(int index) {
		return targets[index];
	}

	public int getResolution() {
		return resolution;
	}

	public DepthTexture getDepthTexture() {
		return mainDepth;
	}

	public DepthTexture getDepthTextureNoTranslucents() {
		return noTranslucents;
	}

	public GlFramebuffer getDepthSourceFb() {
		return depthSourceFb;
	}

	public void copyPreTranslucentDepth() {
		if (translucentDepthDirty) {
			translucentDepthDirty = false;
			RenderSystem.blitFramebuffer(depthSourceFb.getId(), noTranslucentsDestFb.getId(), 0, 0, resolution, resolution,
				0, 0, resolution, resolution, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
		} else {
			DepthCopyStrategy.fastest(false).copy(depthSourceFb, mainDepth.getTextureId(), noTranslucentsDestFb, noTranslucents.getTextureId(),
				resolution, resolution);
		}
	}

	public boolean isFullClearRequired() {
		return fullClearRequired;
	}

	public void onFullClear() {
		fullClearRequired = false;
	}

	public GlFramebuffer createFramebufferWritingToMain(int[] drawBuffers) {
		return createFullFramebuffer(false, drawBuffers);
	}

	public GlFramebuffer createFramebufferWritingToAlt(int[] drawBuffers) {
		return createFullFramebuffer(true, drawBuffers);
	}

	private ImmutableSet<Integer> invert(ImmutableSet<Integer> base, int[] relevant) {
		ImmutableSet.Builder<Integer> inverted = ImmutableSet.builder();

		for (int i : relevant) {
			if (!base.contains(i)) {
				inverted.add(i);
			}
		}

		return inverted.build();
	}

	private GlFramebuffer createEmptyFramebuffer() {
		GlFramebuffer framebuffer = new GlFramebuffer();
		ownedFramebuffers.add(framebuffer);

		framebuffer.addDepthAttachment(mainDepth.getTextureId());

		// NB: Before OpenGL 3.0, all framebuffers are required to have a color
		// attachment no matter what.
		framebuffer.addColorAttachment(0, getOrCreate(0).getMainTexture());
		framebuffer.noDrawBuffers();

		return framebuffer;
	}

	public GlFramebuffer createShadowFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		ImmutableSet<Integer> stageWritesToMain = invert(stageWritesToAlt, drawBuffers);
        GlFramebuffer framebuffer =  createColorFramebuffer(stageWritesToMain, drawBuffers);
        framebuffer.addDepthAttachment(mainDepth.getTextureId());

		return framebuffer;
	}

	private GlFramebuffer createFullFramebuffer(boolean clearsAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();

		if (!clearsAlt) {
			stageWritesToMain = invert(ImmutableSet.of(), drawBuffers);
		}

		return createColorFramebufferWithDepth(stageWritesToMain, drawBuffers);
	}

	public GlFramebuffer createColorFramebufferWithDepth(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
		GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);

		framebuffer.addDepthAttachment(mainDepth.getTextureId());

		return framebuffer;
	}

	public GlFramebuffer createColorFramebuffer(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			throw new IllegalArgumentException("Framebuffer must have at least one color buffer");
		}

		GlFramebuffer framebuffer = new GlFramebuffer();
		ownedFramebuffers.add(framebuffer);

		int[] actualDrawBuffers = new int[drawBuffers.length];

		for (int i = 0; i < drawBuffers.length; i++) {
			actualDrawBuffers[i] = i;

			if (drawBuffers[i] >= getRenderTargetCount()) {
				// TODO: This causes resource leaks, also we should really verify this in the shaderpack parser...
				throw new IllegalStateException("Render target with index " + drawBuffers[i] + " is not supported, only "
					+ getRenderTargetCount() + " render targets are supported.");
			}

			RenderTarget target = this.getOrCreate(drawBuffers[i]);

			int textureId = stageWritesToMain.contains(drawBuffers[i]) ? target.getMainTexture() : target.getAltTexture();

			framebuffer.addColorAttachment(i, textureId);
		}

		framebuffer.drawBuffers(actualDrawBuffers);
		framebuffer.readBuffer(0);

		if (!framebuffer.isComplete()) {
			throw new IllegalStateException("Unexpected error while creating framebuffer");
		}

		return framebuffer;
	}

	public int getColorTextureId(int i) {
		RenderTarget target = get(i);
		if (target == null) {
			return 0;
		}
		return isFlipped(i) ? target.getAltTexture() : target.getMainTexture();
	}

	public boolean isHardwareFiltered(int i) {
		return hardwareFiltered[i];
	}

	public boolean isLinearFiltered(int i) {
		return linearFiltered[i];
	}

	public int getNumColorTextures() {
		return targets.length;
	}

	public InternalTextureFormat getColorTextureFormat(int index) {
		return formats[index];
	}

	public ImmutableSet<Integer> snapshot() {
		ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
		for (int i = 0; i < flipped.length; i++) {
			if (flipped[i]) {
				builder.add(i);
			}
		}

		return builder.build();
	}

	public IntList getBuffersToBeCleared() {
		return buffersToBeCleared;
	}
}

package net.coderbot.iris.rendertarget;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import net.coderbot.iris.gl.texture.DepthCopyStrategy;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackRenderTargetDirectives;
import net.minecraftforge.client.MinecraftForgeClient;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

// Owns Iris's color and depth render targets and the framebuffers wiring them together for the
// deferred pipeline. Supports both monoscopic (eyeCount == 1) and stereoscopic (eyeCount == 2);
// for stereo, each eye gets its own color textures and its own depth+stencil texture so
// kernel-based composite shaders (bloom, blur, SSAO) don't bleed across the eye boundary.
// setActiveEye(int) re-attaches every owned framebuffer to the new eye's textures so a single
// GlFramebuffer object handed to a CompositeRenderer pass simply targets the current eye.
// blitEyeDepthStencilToMain copies an eye's depth+stencil into that eye's region of the main FB
// depth+stencil at the end of the eye's pipeline, preserving stencil for mods that read main FB
// stencil after world rendering (Avaritia cosmic textures, Forge stencil effects, Thaumcraft).
public class RenderTargets {
	private final int eyeCount;
	private int currentEye;

	private final RenderTarget[][] eyeTargets;

	// Per-eye depth+stencil texture IDs. For monoscopic mode, eyeDepthTextures[0] holds the
	// main FB's depth texture (we don't own it; never delete on destroy). For stereo, each
	// entry is an Iris-allocated texture that lives only for the eye's intermediates — we
	// allocate, resize, and free these ourselves.
	private final int[] eyeDepthTextures;
	private final boolean ownsEyeDepthTextures;

	private DepthBufferFormat currentDepthFormat;
	private DepthCopyStrategy copyStrategy;

	private final DepthTexture[] eyeNoTranslucents;
	private final DepthTexture[] eyeNoHand;

	// Fixed framebuffers used internally for depth-copy operations. One instance per eye; we
	// pick the active one via currentEye instead of swapping attachments on it.
	private final GlFramebuffer[] eyeDepthSourceFb;
	private final GlFramebuffer[] eyeNoTranslucentsDestFb;
	private final GlFramebuffer[] eyeNoHandDestFb;

	// Externally-visible framebuffers handed out via createXxxFramebuffer(...). Single GL FBO
	// per call (so CompositeRenderer/FinalPassRenderer can cache a stable reference) whose
	// attachments are rebound to the current eye's textures on setActiveEye().
	private final List<OwnedFb> ownedFramebuffers;

	private int cachedWidth;
	private int cachedHeight;
	@Getter
	private boolean fullClearRequired;
	private final boolean[] eyeTranslucentDepthDirty;
	private final boolean[] eyeHandDepthDirty;

	private int cachedDepthBufferVersion;

	/** Holds an owned framebuffer plus the recipe for rebinding its attachments per-eye. */
	private static final class OwnedFb {
		final GlFramebuffer fb;
		final IntConsumer rebind;

		OwnedFb(GlFramebuffer fb, IntConsumer rebind) {
			this.fb = fb;
			this.rebind = rebind;
		}
	}

	public RenderTargets(int width, int height, int sharedDepthTexture, int depthBufferVersion,
						 DepthBufferFormat depthFormat,
						 Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargets,
						 PackDirectives packDirectives) {
		this(width, height, sharedDepthTexture, depthBufferVersion, depthFormat, renderTargets, packDirectives, 1);
	}

	public RenderTargets(int width, int height, int sharedDepthTexture, int depthBufferVersion,
						 DepthBufferFormat depthFormat,
						 Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargets,
						 PackDirectives packDirectives, int eyeCount) {
		if (eyeCount < 1 || eyeCount > 2) {
			throw new IllegalArgumentException("eyeCount must be 1 or 2, was " + eyeCount);
		}
		this.eyeCount = eyeCount;
		this.currentEye = 0;

		this.cachedWidth = width;
		this.cachedHeight = height;
		this.cachedDepthBufferVersion = depthBufferVersion;

		// Allocate per-eye color targets up-front.
		this.eyeTargets = new RenderTarget[eyeCount][renderTargets.size()];
		for (int e = 0; e < eyeCount; e++) {
			final RenderTarget[] perEye = eyeTargets[e];
			renderTargets.forEach((index, settings) -> {
				Vector2i dimensions = packDirectives.getTextureScaleOverride(index, width, height);
				// Apply format fallback for opengl versions with limited color-renderable format support (e.g., macOS GL 2.1)
				final var requestedFormat = settings.getInternalFormat();
				final var actualFormat = requestedFormat.getColorRenderableFallback();
				if (actualFormat != requestedFormat) {
					Iris.logger.info("Render target {} using fallback format {} (requested {})", index, actualFormat, requestedFormat);
				}
				perEye[index] = RenderTarget.builder().setDimensions(dimensions.x, dimensions.y)
						.setInternalFormat(actualFormat)
						.setPixelFormat(actualFormat.getPixelFormat()).build();
			});
		}

		// Depth texture(s): borrow main FB's in mono, own per-eye textures in stereo.
		this.eyeDepthTextures = new int[eyeCount];
		if (eyeCount == 1) {
			this.eyeDepthTextures[0] = sharedDepthTexture;
			this.ownsEyeDepthTextures = false;
			this.currentDepthFormat = depthFormat;
		} else {
			// Pick a stencil-bearing format if Forge wants stencil bits — matches what
			// MixinFramebuffer does for the main FB. This is what lets stencil-using mods write
			// through Iris's per-eye FBO bindings during world rendering.
			final boolean stencil = MinecraftForgeClient.getStencilBits() != 0;
			final DepthBufferFormat eyeFormat = stencil
				? DepthBufferFormat.DEPTH24_STENCIL8
				: depthFormat;
			this.currentDepthFormat = eyeFormat;
			this.ownsEyeDepthTextures = true;
			for (int e = 0; e < eyeCount; e++) {
				this.eyeDepthTextures[e] = allocateOwnedDepthTexture(width, height, eyeFormat);
			}
		}
		this.copyStrategy = DepthCopyStrategy.fastest(currentDepthFormat.isCombinedStencil());

		this.ownedFramebuffers = new ArrayList<>();
		fullClearRequired = true;

		this.eyeNoTranslucents = new DepthTexture[eyeCount];
		this.eyeNoHand = new DepthTexture[eyeCount];
		this.eyeDepthSourceFb = new GlFramebuffer[eyeCount];
		this.eyeNoTranslucentsDestFb = new GlFramebuffer[eyeCount];
		this.eyeNoHandDestFb = new GlFramebuffer[eyeCount];
		this.eyeTranslucentDepthDirty = new boolean[eyeCount];
		this.eyeHandDepthDirty = new boolean[eyeCount];

		for (int e = 0; e < eyeCount; e++) {
			this.eyeNoTranslucents[e] = new DepthTexture(width, height, currentDepthFormat);
			this.eyeNoHand[e] = new DepthTexture(width, height, currentDepthFormat);
			this.eyeTranslucentDepthDirty[e] = true;
			this.eyeHandDepthDirty[e] = true;

			final GlFramebuffer src = new GlFramebuffer();
			src.addColorAttachment(0, eyeTargets[e][0].getMainTexture());
			src.addDepthAttachment(eyeDepthTextures[e]);
			this.eyeDepthSourceFb[e] = src;

			final GlFramebuffer nt = new GlFramebuffer();
			nt.addColorAttachment(0, eyeTargets[e][0].getMainTexture());
			nt.addDepthAttachment(eyeNoTranslucents[e].getTextureId());
			this.eyeNoTranslucentsDestFb[e] = nt;

			final GlFramebuffer nh = new GlFramebuffer();
			nh.addColorAttachment(0, eyeTargets[e][0].getMainTexture());
			nh.addDepthAttachment(eyeNoHand[e].getTextureId());
			this.eyeNoHandDestFb[e] = nh;
		}
	}

	// Allocate an Iris-owned depth+stencil (or depth-only) texture for stereo per-eye depth so
	// each eye has its own stencil buffer during world rendering.
	private static int allocateOwnedDepthTexture(int width, int height, DepthBufferFormat format) {
		final int tex = GL11.glGenTextures();
		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);

		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, 0);
		GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format.getGlInternalFormat(),
			width, height, 0, format.getGlType(), format.getGlFormat(), (IntBuffer) null);

		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		return tex;
	}

	// Switch which eye's textures the owned framebuffers point at. No-op for mono. Caller must
	// invoke this before doing any work that should land in a particular eye's framebuffer; the
	// pipeline does this at the top of each eye's renderWorld pass.
	public void setActiveEye(int eye) {
		if (eye < 0 || eye >= eyeCount) {
			// Pipeline was built in mono but the user enabled stereo without reloading the
			// shader pack. Nothing to swap — clamp to the only available eye and continue;
			// reloading shaders will rebuild this as a proper 2-eye pipeline.
			return;
		}
		if (eye == currentEye) {
			return;
		}
		currentEye = eye;
		for (OwnedFb owned : ownedFramebuffers) {
			owned.rebind.accept(eye);
		}
	}

	// Clear the active eye's depth+stencil texture. No-op in monoscopic mode (Iris shares MC's
	// main FB depth and MC's per-frame glClear handles it). In stereo each eye owns a private
	// depth+stencil that MC never touches, so we have to clear it between eye passes — otherwise
	// stale depth/stencil causes wrong occlusion and stencil test failures.
	public void clearCurrentEyeDepthStencil() {
		if (!ownsEyeDepthTextures) return;
		eyeDepthSourceFb[currentEye].bind();
		GL11.glViewport(0, 0, cachedWidth, cachedHeight);
		// Restrict the clear to depth+stencil — don't clobber the color attachment we share with
		// the eye's colortex0 main texture (ClearPass handles color clearing separately).
		GL11.glColorMask(false, false, false, false);
		GL11.glDepthMask(true);
		final boolean stencil = currentDepthFormat.isCombinedStencil();
		if (stencil) {
			GL11.glStencilMask(0xFF);
			GL11.glClearStencil(0);
		}
		GL11.glClearDepth(1.0);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | (stencil ? GL11.GL_STENCIL_BUFFER_BIT : 0));
		GL11.glColorMask(true, true, true, true);
	}

	public int getCurrentEye() {
		return currentEye;
	}

	public int getEyeCount() {
		return eyeCount;
	}

	public void destroy() {
		for (OwnedFb owned : ownedFramebuffers) {
			owned.fb.destroy();
		}
		for (int e = 0; e < eyeCount; e++) {
			for (RenderTarget target : eyeTargets[e]) {
				if (target != null) {
					target.destroy();
				}
			}
			eyeNoTranslucents[e].destroy();
			eyeNoHand[e].destroy();
			eyeDepthSourceFb[e].destroy();
			eyeNoTranslucentsDestFb[e].destroy();
			eyeNoHandDestFb[e].destroy();
		}
		if (ownsEyeDepthTextures) {
			for (int e = 0; e < eyeCount; e++) {
				GLStateManager.glDeleteTextures(eyeDepthTextures[e]);
			}
		}
	}

	public int getRenderTargetCount() {
		return eyeTargets[currentEye].length;
	}

	public RenderTarget get(int index) {
		return eyeTargets[currentEye][index];
	}

	public int getDepthTexture() {
		return eyeDepthTextures[currentEye];
	}

	public DepthTexture getDepthTextureNoTranslucents() {
		return eyeNoTranslucents[currentEye];
	}

	public DepthTexture getDepthTextureNoHand() {
		return eyeNoHand[currentEye];
	}

	public boolean resizeIfNeeded(int newDepthBufferVersion, int newDepthTextureId, int newWidth, int newHeight, DepthBufferFormat newDepthFormat, PackDirectives packDirectives) {
		boolean recreateDepth = false;
		if (!ownsEyeDepthTextures && cachedDepthBufferVersion != newDepthBufferVersion) {
			// Monoscopic mode: shared main FB depth texture changed underneath us (window resize).
			recreateDepth = true;
			eyeDepthTextures[0] = newDepthTextureId;
			cachedDepthBufferVersion = newDepthBufferVersion;
		}

		final boolean sizeChanged = newWidth != cachedWidth || newHeight != cachedHeight;
		final boolean depthFormatChanged = newDepthFormat != currentDepthFormat && !ownsEyeDepthTextures;

		if (depthFormatChanged) {
			currentDepthFormat = newDepthFormat;
			copyStrategy = DepthCopyStrategy.fastest(currentDepthFormat.isCombinedStencil());
		}

		if (ownsEyeDepthTextures && sizeChanged) {
			// Stereo per-eye depth: reallocate storage at the new size, keeping texture IDs.
			for (int e = 0; e < eyeCount; e++) {
				GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, eyeDepthTextures[e]);
				GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, currentDepthFormat.getGlInternalFormat(),
					newWidth, newHeight, 0, currentDepthFormat.getGlType(), currentDepthFormat.getGlFormat(), (IntBuffer) null);
			}
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		}

		if (recreateDepth) {
			// Monoscopic: MC re-creates its depth texture on resize. Re-attach to all owned and
			// fixed framebuffers that reference the current depth texture.
			final int newId = eyeDepthTextures[0];
			for (int e = 0; e < eyeCount; e++) {
				eyeDepthSourceFb[e].addDepthAttachment(newId);
			}
			// Owned framebuffers: re-running their rebind picks up the new depth texture if they
			// reference getDepthTexture() through eyeDepthTextures.
			for (OwnedFb owned : ownedFramebuffers) {
				owned.rebind.accept(currentEye);
			}
		}

		if (depthFormatChanged || sizeChanged) {
			for (int e = 0; e < eyeCount; e++) {
				eyeNoTranslucents[e].resize(newWidth, newHeight, currentDepthFormat);
				eyeNoHand[e].resize(newWidth, newHeight, currentDepthFormat);
				eyeTranslucentDepthDirty[e] = true;
				eyeHandDepthDirty[e] = true;
			}
		}

		if (sizeChanged) {
			cachedWidth = newWidth;
			cachedHeight = newHeight;

			for (int e = 0; e < eyeCount; e++) {
				for (int i = 0; i < eyeTargets[e].length; i++) {
					eyeTargets[e][i].resize(packDirectives.getTextureScaleOverride(i, newWidth, newHeight));
				}
			}

			fullClearRequired = true;
		}

		return sizeChanged;
	}

	public void copyPreTranslucentDepth() {
		final int eye = currentEye;
		if (eyeTranslucentDepthDirty[eye]) {
			eyeTranslucentDepthDirty[eye] = false;
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, eyeNoTranslucents[eye].getTextureId());
			eyeDepthSourceFb[eye].bindAsReadBuffer();
			RenderSystem.copyTexImage2D(GL11.GL_TEXTURE_2D, 0, currentDepthFormat.getGlInternalFormat(), 0, 0, cachedWidth, cachedHeight, 0);
		} else {
			copyStrategy.copy(eyeDepthSourceFb[eye], getDepthTexture(), eyeNoTranslucentsDestFb[eye],
				eyeNoTranslucents[eye].getTextureId(), getCurrentWidth(), getCurrentHeight());
		}
	}

	public void copyPreHandDepth() {
		final int eye = currentEye;
		if (eyeHandDepthDirty[eye]) {
			eyeHandDepthDirty[eye] = false;
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, eyeNoHand[eye].getTextureId());
			eyeDepthSourceFb[eye].bindAsReadBuffer();
			RenderSystem.copyTexImage2D(GL11.GL_TEXTURE_2D, 0, currentDepthFormat.getGlInternalFormat(), 0, 0, cachedWidth, cachedHeight, 0);
		} else {
			copyStrategy.copy(eyeDepthSourceFb[eye], getDepthTexture(), eyeNoHandDestFb[eye],
				eyeNoHand[eye].getTextureId(), getCurrentWidth(), getCurrentHeight());
		}
	}

	// Copy the current eye's depth+stencil texture into the eye's region of the main FB's
	// depth+stencil. Called at the end of each eye's deferred + composite + final pass when stereo
	// is active. Preserves stencil so any code that reads main FB stencil after world rendering
	// (vanilla, mods, the second eye's pre-pass) sees a coherent state.
	public void blitEyeDepthStencilToMain(int mainFbGlId, int dstX0, int dstY0, int dstX1, int dstY1) {
		if (!ownsEyeDepthTextures) {
			// Mono shares main FB depth — nothing to copy.
			return;
		}
		final int mask = currentDepthFormat.isCombinedStencil()
			? (GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT)
			: GL11.GL_DEPTH_BUFFER_BIT;
		RenderSystem.blitFramebuffer(eyeDepthSourceFb[currentEye].getId(), mainFbGlId,
			0, 0, cachedWidth, cachedHeight,
			dstX0, dstY0, dstX1, dstY1,
			mask, GL11.GL_NEAREST);
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

	public GlFramebuffer createClearFramebuffer(boolean alt, int[] clearBuffers) {
		ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();

		if (!alt) {
			stageWritesToMain = invert(ImmutableSet.of(), clearBuffers);
		}

		return createColorFramebuffer(stageWritesToMain, clearBuffers);
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
		final GlFramebuffer framebuffer = new GlFramebuffer();

		final IntConsumer rebind = eye -> {
			framebuffer.addDepthAttachment(eyeDepthTextures[eye]);
			framebuffer.addColorAttachment(0, eyeTargets[eye][0].getMainTexture());
		};
		rebind.accept(currentEye);
		framebuffer.noDrawBuffers();

		ownedFramebuffers.add(new OwnedFb(framebuffer, rebind));
		return framebuffer;
	}

	public GlFramebuffer createDHFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		final ImmutableSet<Integer> stageWritesToMain = invert(stageWritesToAlt, drawBuffers);
		return createColorFramebufferInternal(stageWritesToMain, drawBuffers, false);
	}

	public GlFramebuffer createGbufferFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		final ImmutableSet<Integer> stageWritesToMain = invert(stageWritesToAlt, drawBuffers);
		return createColorFramebufferInternal(stageWritesToMain, drawBuffers, true);
	}

	private GlFramebuffer createFullFramebuffer(boolean clearsAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();

		if (!clearsAlt) {
			stageWritesToMain = invert(ImmutableSet.of(), drawBuffers);
		}

		return createColorFramebufferInternal(stageWritesToMain, drawBuffers, true);
	}

	public GlFramebuffer createColorFramebufferWithDepth(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
		return createColorFramebufferInternal(stageWritesToMain, drawBuffers, true);
	}

	public GlFramebuffer createColorFramebuffer(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
		return createColorFramebufferInternal(stageWritesToMain, drawBuffers, false);
	}

	private GlFramebuffer createColorFramebufferInternal(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers, boolean withDepth) {
		if (drawBuffers.length == 0) {
			throw new IllegalArgumentException("Framebuffer must have at least one color buffer");
		}

		final GlFramebuffer framebuffer = new GlFramebuffer();

		final int[] actualDrawBuffers = new int[drawBuffers.length];
		final int targetCount = eyeTargets[0].length;
		for (int i = 0; i < drawBuffers.length; i++) {
			actualDrawBuffers[i] = i;
			if (drawBuffers[i] >= targetCount) {
				// TODO: This causes resource leaks, also we should really verify this in the shaderpack parser...
				throw new IllegalStateException("Render target with index " + drawBuffers[i] + " is not supported, only "
						+ targetCount + " render targets are supported.");
			}
		}

		final int[] drawBuffersCopy = drawBuffers.clone();
		final ImmutableSet<Integer> stageWritesToMainCopy = stageWritesToMain;

		final IntConsumer rebind = eye -> {
			final RenderTarget[] perEye = eyeTargets[eye];
			for (int i = 0; i < drawBuffersCopy.length; i++) {
				final RenderTarget target = perEye[drawBuffersCopy[i]];
				final int textureId = stageWritesToMainCopy.contains(drawBuffersCopy[i]) ? target.getMainTexture() : target.getAltTexture();
				framebuffer.addColorAttachment(i, textureId);
			}
			if (withDepth) {
				framebuffer.addDepthAttachment(eyeDepthTextures[eye]);
			}
		};
		rebind.accept(currentEye);

		framebuffer.drawBuffers(actualDrawBuffers);
		framebuffer.readBuffer(0);

		if (!framebuffer.isComplete()) {
			throw new IllegalStateException("Unexpected error while creating framebuffer");
		}

		ownedFramebuffers.add(new OwnedFb(framebuffer, rebind));
		return framebuffer;
	}

	public void destroyFramebuffer(GlFramebuffer framebuffer) {
		framebuffer.destroy();
		ownedFramebuffers.removeIf(o -> o.fb == framebuffer);
	}

	public int getCurrentWidth() {
		return cachedWidth;
	}

	public int getCurrentHeight() {
		return cachedHeight;
	}
}

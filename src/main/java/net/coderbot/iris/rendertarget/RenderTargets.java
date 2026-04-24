package net.coderbot.iris.rendertarget;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.profiler.Profiler;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.framebuffer.ParityFramebuffer;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import net.coderbot.iris.gl.texture.DepthCopyStrategy;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackRenderTargetDirectives;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RenderTargets {
	private final RenderTarget[] targets;
	private int currentDepthTexture;
	private DepthBufferFormat currentDepthFormat;

    @Getter
	private final DepthTexture noTranslucents;
	private final DepthTexture noHand;
	private final GlFramebuffer depthSourceFb;
	private final GlFramebuffer noTranslucentsDestFb;
	private final GlFramebuffer noHandDestFb;
	private DepthCopyStrategy copyStrategy;

	private final List<GlFramebuffer> ownedFramebuffers;
	private ParityFlipState parity;
	private final List<PendingParityFramebuffer> pendingParity = new ArrayList<>();

	private int cachedWidth;
	private int cachedHeight;
	@Getter
    private boolean fullClearRequired;

	private int cachedDepthBufferVersion;

	private boolean translucentDepthDirty;
	private boolean handDepthDirty;

	public RenderTargets(int width, int height,  int depthTexture, int depthBufferVersion, DepthBufferFormat depthFormat, Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargets, PackDirectives packDirectives) {
        targets = new RenderTarget[renderTargets.size()];

		renderTargets.forEach((index, settings) -> {
			// TODO: Handle mipmapping?
			Vector2i dimensions = packDirectives.getTextureScaleOverride(index, width, height);
			// Apply format fallback for opengl versions with limited color-renderable format support (e.g., macOS GL 2.1)
			var requestedFormat = settings.getInternalFormat();
			var actualFormat = requestedFormat.getColorRenderableFallback();
			if (actualFormat != requestedFormat) {
				Iris.logger.info("Render target {} using fallback format {} (requested {})", index, actualFormat, requestedFormat);
			}
			targets[index] = RenderTarget.builder().setDimensions(dimensions.x, dimensions.y)
					.setInternalFormat(actualFormat)
					.setPixelFormat(actualFormat.getPixelFormat()).build();
		});
		this.currentDepthTexture = depthTexture;
		this.currentDepthFormat = depthFormat;
		this.copyStrategy = DepthCopyStrategy.fastest(copyDepthFormat().isCombinedStencil());
		if (Tracy.ENABLED) Tracy.message("depth copy strategy: " + copyStrategy.getClass().getSimpleName());

		this.cachedWidth = width;
		this.cachedHeight = height;
		this.cachedDepthBufferVersion = depthBufferVersion;

		this.ownedFramebuffers = new ArrayList<>();

		// NB: Make sure all buffers are cleared so that they don't contain undefined
		// data. Otherwise very weird things can happen.
		fullClearRequired = true;

		this.depthSourceFb = createFramebufferWritingToMain(new int[] {0});

		this.noTranslucents = new DepthTexture(width, height, copyDepthFormat());
		this.noHand = new DepthTexture(width, height, copyDepthFormat());

		this.noTranslucentsDestFb = createFramebufferWritingToMain(new int[] {0});
		this.noTranslucentsDestFb.addDepthAttachment(this.noTranslucents.getTextureId());

		this.noHandDestFb = createFramebufferWritingToMain(new int[] {0});
		this.noHandDestFb.addDepthAttachment(this.noHand.getTextureId());

		this.translucentDepthDirty = true;
		this.handDepthDirty = true;
	}

	public void destroy() {
		for (GlFramebuffer owned : ownedFramebuffers) {
			owned.destroy();
		}

		for (RenderTarget target : targets) {
			target.destroy();
		}

		noTranslucents.destroy();
		noHand.destroy();
	}

	public int getRenderTargetCount() {
		return targets.length;
	}

	public RenderTarget get(int index) {
		return targets[index];
	}

	public int getDepthTexture() {
		return currentDepthTexture;
	}

	public DepthTexture getDepthTextureNoTranslucents() {
		return noTranslucents;
	}

	public DepthTexture getDepthTextureNoHand() {
		return noHand;
	}

    public boolean resizeIfNeeded(int newDepthBufferVersion, int newDepthTextureId, int newWidth, int newHeight, DepthBufferFormat newDepthFormat, PackDirectives packDirectives) {
        boolean recreateDepth = false;
        if (cachedDepthBufferVersion != newDepthBufferVersion) {
            recreateDepth = true;
            currentDepthTexture = newDepthTextureId;
            cachedDepthBufferVersion = newDepthBufferVersion;
        }

        boolean sizeChanged = newWidth != cachedWidth || newHeight != cachedHeight;
        boolean depthFormatChanged = newDepthFormat != currentDepthFormat;

        if (depthFormatChanged) {
            currentDepthFormat = newDepthFormat;
            // Might need a new copy strategy
            copyStrategy = DepthCopyStrategy.fastest(copyDepthFormat().isCombinedStencil());
            if (Tracy.ENABLED) Tracy.message("depth copy strategy: " + copyStrategy.getClass().getSimpleName());
        }

        if (recreateDepth) {
            // Re-attach the depth textures with the new depth texture ID, since Minecraft re-creates
            // the depth texture when resizing its render targets.
            //
            // I'm not sure if our framebuffers holding on to the old depth texture between frames
            // could be a concern, in the case of resizing and similar. I think it should work
            // based on what I've seen of the spec, though - it seems like deleting a texture
            // automatically detaches it from its framebuffers.
            for (GlFramebuffer framebuffer : ownedFramebuffers) {
                if (framebuffer == noHandDestFb || framebuffer == noTranslucentsDestFb) {
                    // NB: Do not change the depth attachment of these framebuffers
                    // as it is intentionally different
                    continue;
                }

                if (framebuffer.hasDepthAttachment()) {
                    framebuffer.addDepthAttachment(newDepthTextureId);
                }
            }
        }

        if (depthFormatChanged || sizeChanged)  {
            // Reallocate depth buffers
            noTranslucents.resize(newWidth, newHeight, copyDepthFormat());
            noHand.resize(newWidth, newHeight, copyDepthFormat());
            this.translucentDepthDirty = true;
            this.handDepthDirty = true;
        }

        if (sizeChanged) {
            cachedWidth = newWidth;
            cachedHeight = newHeight;

            for (int i = 0; i < targets.length; i++) {
                targets[i].resize(packDirectives.getTextureScaleOverride(i, newWidth, newHeight));
            }

            fullClearRequired = true;
        }

        return sizeChanged;
    }

	public void copyPreTranslucentDepth() {
		if (translucentDepthDirty && !RenderSystem.isGLES()) {
			translucentDepthDirty = false;
			final Profiler profiler = Minecraft.getMinecraft().mcProfiler;
			profiler.startSection("iris_depth_realloc");
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, noTranslucents.getTextureId());
			depthSourceFb.bindAsReadBuffer();
			RenderSystem.copyTexImage2D(GL11.GL_TEXTURE_2D, 0, copyDepthFormat().getGlInternalFormat(), 0, 0, cachedWidth, cachedHeight, 0);
			profiler.endSection();
		} else {
			copyStrategy.copy(depthSourceFb, getDepthTexture(), noTranslucentsDestFb, noTranslucents.getTextureId(), getCurrentWidth(), getCurrentHeight());
		}
	}

	public void copyPreHandDepth() {
		if (handDepthDirty && !RenderSystem.isGLES()) {
			handDepthDirty = false;
			final Profiler profiler = Minecraft.getMinecraft().mcProfiler;
			profiler.startSection("iris_depth_realloc");
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, noHand.getTextureId());
			depthSourceFb.bindAsReadBuffer();
			RenderSystem.copyTexImage2D(GL11.GL_TEXTURE_2D, 0, copyDepthFormat().getGlInternalFormat(), 0, 0, cachedWidth, cachedHeight, 0);
			profiler.endSection();
		} else {
			copyStrategy.copy(depthSourceFb, getDepthTexture(), noHandDestFb, noHand.getTextureId(), getCurrentWidth(), getCurrentHeight());
		}
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
		GlFramebuffer framebuffer = new GlFramebuffer();
		ownedFramebuffers.add(framebuffer);

		framebuffer.addDepthAttachment(currentDepthTexture);

		// NB: Before OpenGL 3.0, all framebuffers are required to have a color attachment no matter what.
		framebuffer.addColorAttachment(0, get(0).getMainTexture());
		framebuffer.noDrawBuffers();

		return framebuffer;
	}

	public GlFramebuffer createDHFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		ImmutableSet<Integer> stageWritesToMain = invert(stageWritesToAlt, drawBuffers);

		return createColorFramebuffer(stageWritesToMain, drawBuffers);
	}

	public GlFramebuffer createGbufferFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			return createEmptyFramebuffer();
		}

		ImmutableSet<Integer> stageWritesToMain = invert(stageWritesToAlt, drawBuffers);
        GlFramebuffer framebuffer =  createColorFramebuffer(stageWritesToMain, drawBuffers);
        framebuffer.addDepthAttachment(currentDepthTexture);

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
        final GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);
        framebuffer.addDepthAttachment(currentDepthTexture);

		return framebuffer;
	}

	public GlFramebuffer createColorFramebuffer(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
		if (drawBuffers.length == 0) {
			throw new IllegalArgumentException("Framebuffer must have at least one color buffer");
		}

		if (parity == null || !parity.isEnabled()) {
			final GlFramebuffer framebuffer = new GlFramebuffer();
			ownedFramebuffers.add(framebuffer);
			attachColor(framebuffer, stageWritesToMain, drawBuffers, false);
			return framebuffer;
		}

		final ParityFramebuffer framebuffer = new ParityFramebuffer(parity);
		ownedFramebuffers.add(framebuffer);
		attachColor(framebuffer, stageWritesToMain, drawBuffers, false);
		if (!parity.isFinalized()) {
			pendingParity.add(new PendingParityFramebuffer(framebuffer, stageWritesToMain, drawBuffers));
		} else if (parity.affectsAny(drawBuffers)) {
			framebuffer.setOdd(buildOddVariant(stageWritesToMain, drawBuffers));
		}
		return framebuffer;
	}

	private GlFramebuffer buildOddVariant(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
		final GlFramebuffer odd = new GlFramebuffer();
		attachColor(odd, stageWritesToMain, drawBuffers, true);
		return odd;
	}

	private void attachColor(GlFramebuffer framebuffer, ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers, boolean flipParityBuffers) {
		final int[] actualDrawBuffers = new int[drawBuffers.length];

		for (int i = 0; i < drawBuffers.length; i++) {
			actualDrawBuffers[i] = i;

			if (drawBuffers[i] >= getRenderTargetCount()) {
				// TODO: This causes resource leaks, also we should really verify this in the shaderpack parser...
				throw new IllegalStateException("Render target with index " + drawBuffers[i] + " is not supported, only "
						+ getRenderTargetCount() + " render targets are supported.");
			}

			final RenderTarget target = this.get(drawBuffers[i]);

			boolean writesToMain = stageWritesToMain.contains(drawBuffers[i]);
			if (flipParityBuffers && parity.parityBuffers().contains(drawBuffers[i])) {
				writesToMain = !writesToMain;
			}
			framebuffer.addColorAttachment(i, writesToMain ? target.getMainTexture() : target.getAltTexture());
        }

		framebuffer.drawBuffers(actualDrawBuffers);
        framebuffer.readBuffer(0);

		final int status = framebuffer.getStatus();
		if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
			throw new IllegalStateException(String.format( "Unexpected error while creating framebuffer: glCheckFramebufferStatus=0x%X, size=%dx%d, drawBuffers=%d", status, cachedWidth, cachedHeight, drawBuffers.length));
		}
	}

	public void setParityState(ParityFlipState parity) {
		this.parity = parity;
	}

	public ParityFlipState getParityState() {
		return parity;
	}

	public ImmutableSet<Integer> parityResolve(ImmutableSet<Integer> even) {
		return parity != null ? parity.resolve(even) : even;
	}

	public void finalizeParity() {
		if (parity != null) {
			for (PendingParityFramebuffer pending : pendingParity) {
				if (parity.isEnabled() && parity.affectsAny(pending.drawBuffers)) {
					final GlFramebuffer odd = buildOddVariant(pending.stageWritesToMain, pending.drawBuffers);
					if (pending.framebuffer.hasDepthAttachment()) {
						odd.addDepthAttachment(currentDepthTexture);
					}
					pending.framebuffer.setOdd(odd);
				}
			}
		}
		pendingParity.clear();
	}

	private static final class PendingParityFramebuffer {
		final ParityFramebuffer framebuffer;
		final ImmutableSet<Integer> stageWritesToMain;
		final int[] drawBuffers;

		PendingParityFramebuffer(ParityFramebuffer framebuffer, ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
			this.framebuffer = framebuffer;
			this.stageWritesToMain = stageWritesToMain;
			this.drawBuffers = drawBuffers;
		}
	}

	public void destroyFramebuffer(GlFramebuffer framebuffer) {
		framebuffer.destroy();
		ownedFramebuffers.remove(framebuffer);
	}

	private DepthBufferFormat copyDepthFormat() {
		return GLStateManager.capabilities.GL_ARB_copy_image ? currentDepthFormat : currentDepthFormat.stripStencil();
	}

	public int getCurrentWidth() {
		return cachedWidth;
	}

	public int getCurrentHeight() {
		return cachedHeight;
	}
}

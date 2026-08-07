package net.coderbot.iris.gl.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import org.lwjgl.opengl.GL11;

public interface DepthCopyStrategy {
	// FB -> T
	class Gl20CopyTexture implements DepthCopyStrategy {
		private static final Tracy.ZoneId Z_DEPTH_COPY_BIND = Tracy.zoneId("depthCopyBind", Tracy.COLOR_IRIS);
		private static final Tracy.ZoneId Z_DEPTH_COPY_TEX_SUB = Tracy.zoneId("depthCopyTexSub", Tracy.COLOR_IRIS);

		private Gl20CopyTexture() {
			// private
		}

		@Override
		public boolean needsDestFramebuffer() {
			return false;
		}

		@Override
		public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
			if (Tracy.ENABLED) Tracy.beginZone(Z_DEPTH_COPY_BIND);
			try {
				sourceFb.bindAsReadBuffer();
			} finally {
				if (Tracy.ENABLED) Tracy.endZone();
			}

			if (Tracy.ENABLED) Tracy.beginZone(Z_DEPTH_COPY_TEX_SUB);
			try {
				RenderSystem.copyTexSubImage2D(
					destTexture,
					// target
					GL11.GL_TEXTURE_2D,
					// level
					0,
					// xoffset, yoffset
					0, 0,
					// x, y
					0, 0,
					// width
					width,
					// height
					height);
			} finally {
				if (Tracy.ENABLED) Tracy.endZone();
			}
		}
	}

	// FB -> FB
	class Gl30BlitFbCombinedDepthStencil implements DepthCopyStrategy {
		private static final Tracy.ZoneId Z_DEPTH_COPY_BLIT = Tracy.zoneId("depthCopyBlit", Tracy.COLOR_IRIS);

		private Gl30BlitFbCombinedDepthStencil() {
			// private
		}

		@Override
		public boolean needsDestFramebuffer() {
			return true;
		}

		@Override
		public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
			if (Tracy.ENABLED) Tracy.beginZone(Z_DEPTH_COPY_BLIT);
			try {
				RenderSystem.blitFramebuffer(sourceFb.getId(), destFb.getId(), 0, 0, width, height,
					0, 0, width, height,
					GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT,
					GL11.GL_NEAREST);
			} finally {
				if (Tracy.ENABLED) Tracy.endZone();
			}
		}
	}

	// T -> T
	// Fastest
	class Gl43CopyImage implements DepthCopyStrategy {
		private static final Tracy.ZoneId Z_DEPTH_COPY_IMAGE = Tracy.zoneId("depthCopyImage", Tracy.COLOR_IRIS);

		private Gl43CopyImage() {
			// private
		}

		@Override
		public boolean needsDestFramebuffer() {
			return false;
		}

		@Override
		public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
			if (Tracy.ENABLED) Tracy.beginZone(Z_DEPTH_COPY_IMAGE);
			try {
				GLStateManager.glCopyImageSubData(sourceTexture, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, destTexture, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
			} finally {
				if (Tracy.ENABLED) Tracy.endZone();
			}
		}
	}

	static DepthCopyStrategy fastest(boolean combinedStencilRequired) {
		if (GLStateManager.capabilities.GL_ARB_copy_image) {
			return new Gl43CopyImage();
		}

		if (combinedStencilRequired) {
			return new Gl30BlitFbCombinedDepthStencil();
		} else {
			return new Gl20CopyTexture();
		}
	}

	boolean needsDestFramebuffer();

	/**
	 * Executes the copy. May or may not clobber GL_READ_FRAMEBUFFER and GL_DRAW_FRAMEBUFFER bindings - the caller is
	 * responsible for ensuring that they are restored to sensible values, or that the previous values are not relied
	 * on. The callee is responsible for ensuring that texture bindings are not modified.
	 *
	 * @param destFb The destination framebuffer. If {@link #needsDestFramebuffer()} returns false, then this param
	 *               will not be used, and it can be null.
	 */
	void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height);
}

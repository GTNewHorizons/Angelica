package net.coderbot.iris.rendertarget;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class DepthTexture extends GlResource {
	public DepthTexture(int width, int height, DepthBufferFormat format) {
		super(IrisRenderSystem.createTexture(GL11.GL_TEXTURE_2D));
		final int texture = getGlId();

		resize(width, height, format);

		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	void resize(int width, int height, DepthBufferFormat format) {
		IrisRenderSystem.texImage2D(getTextureId(), GL11.GL_TEXTURE_2D, 0, format.getGlInternalFormat(), width, height, 0,
			format.getGlType(), format.getGlFormat(), null);
	}

	public int getTextureId() {
		return getGlId();
	}

	@Override
	protected void destroyInternal() {
		GLStateManager.glDeleteTextures(getGlId());
	}
}

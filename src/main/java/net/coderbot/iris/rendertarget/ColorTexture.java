package net.coderbot.iris.rendertarget;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.texture.TextureUploadHelper;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

public class ColorTexture extends GlResource {
	public ColorTexture(int width, int height) {
		super(IrisRenderSystem.createTexture(GL11.GL_TEXTURE_2D));
		final int texture = getGlId();

        IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

		TextureUploadHelper.resetTextureUploadState();
		IrisRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (ByteBuffer) null);

        // TODO: Message: GL_INVALID_OPERATION error generated. Cannot modify the default framebuffer object.
        EXTFramebufferObject.glFramebufferTexture2DEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, GL11.GL_TEXTURE_2D, texture, 0);
	}

	public int getTextureId() {
		return getGlId();
	}

	@Override
	protected void destroyInternal() {
		GLStateManager.glDeleteTextures(getGlId());
	}
}

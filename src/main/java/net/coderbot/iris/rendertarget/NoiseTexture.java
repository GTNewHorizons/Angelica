package net.coderbot.iris.rendertarget;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.texture.TextureUploadHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * An extremely simple noise texture. Each color channel contains a uniform random value from 0 to 255. Essentially just
 * dumps an array of random bytes into a texture and calls it a day, literally could not be any simpler than that.
 */
public class NoiseTexture extends GlResource {
	int width;
	int height;

	public NoiseTexture(int width, int height) {
		super(IrisRenderSystem.createTexture(GL11.GL_TEXTURE_2D));

		int texture = getGlId();
		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0);
		IrisRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD,0);
		IrisRenderSystem.texParameterf(texture, GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0.0F);
		resize(texture, width, height);

		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	void resize(int texture, int width, int height) {
		this.width = width;
		this.height = height;

		ByteBuffer pixels = generateNoise();

		TextureUploadHelper.resetTextureUploadState();

		// Since we're using tightly-packed RGB data, we must use an alignment of 1 byte instead of the usual 4 bytes.
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		IrisRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, width, height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);

		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	private ByteBuffer generateNoise() {
		byte[] pixels = new byte[3 * width * height];

		Random random = new Random(0);
		random.nextBytes(pixels);

		ByteBuffer buffer = ByteBuffer.allocateDirect(pixels.length);
		buffer.put(pixels);
		buffer.flip();

		return buffer;
	}

	public int getTextureId() {
		return getGlId();
	}

	@Override
	protected void destroyInternal() {
		GLStateManager.glDeleteTextures(getGlId());
	}
}

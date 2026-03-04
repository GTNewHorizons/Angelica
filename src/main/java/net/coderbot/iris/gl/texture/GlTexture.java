package net.coderbot.iris.gl.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.shaderpack.texture.TextureFilteringData;
import org.embeddedt.embeddium.impl.gl.GlObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

public class GlTexture extends GlObject implements TextureAccess {
	private final TextureType target;

	public GlTexture(TextureType target, int sizeX, int sizeY, int sizeZ, int internalFormat, int format, int pixelType, byte[] pixels, TextureFilteringData filteringData) {
		this.setHandle(GL11.glGenTextures());
		bindTextureForSetup(target.getGlType(), handle());

		TextureUploadHelper.resetTextureUploadState();

		final ByteBuffer buffer = memAlloc(pixels.length);
		buffer.put(pixels);
		buffer.flip();
		target.apply(this.handle(), sizeX, sizeY, sizeZ, internalFormat, format, pixelType, buffer);
		memFree(buffer);

		final int texture = this.handle();

		RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_MIN_FILTER, filteringData.shouldBlur() ? GL11.GL_LINEAR : GL11.GL_NEAREST);
		RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_MAG_FILTER, filteringData.shouldBlur() ? GL11.GL_LINEAR : GL11.GL_NEAREST);
		RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_WRAP_S, filteringData.shouldClamp() ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);

		if (sizeY > 0) {
			RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_WRAP_T, filteringData.shouldClamp() ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
		}

		if (sizeZ > 0) {
			RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_WRAP_R, filteringData.shouldClamp() ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
		}

		RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_MAX_LEVEL, 0);
		RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_MIN_LOD, 0);
		RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_MAX_LOD, 0);
		RenderSystem.texParameterf(texture, target.getGlType(), GL14.GL_TEXTURE_LOD_BIAS, 0.0F);

		bindTextureForSetup(target.getGlType(), 0);

		this.target = target;
	}

	public static void bindTextureForSetup(int glType, int glId) {
		GLStateManager.glBindTexture(glType, glId);
	}

	public TextureType getTarget() {
		return target;
	}

	public void bind(int unit) {
		RenderSystem.bindTextureToUnit(target.getGlType(), unit, handle());
	}

	@Override
	public TextureType getType() {
		return target;
	}

	@Override
	public IntSupplier getTextureId() {
		return this::handle;
	}

	@Override
	protected void destroyInternal() {
		GLStateManager.glDeleteTextures(handle());
	}
}

package net.coderbot.iris.rendertarget;

import com.gtnewhorizons.angelica.compat.mojang.NativeImage;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.shaderpack.texture.CustomTextureData;
import net.coderbot.iris.shaderpack.texture.TextureFilteringData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.nio.ByteBuffer;

public class NativeImageBackedCustomTexture extends DynamicTexture {
	public NativeImageBackedCustomTexture(CustomTextureData.PngData textureData) throws IOException {
		this(decode(textureData.getContent()), textureData.getFilteringData());
	}

	public NativeImageBackedCustomTexture(NativeImage image, TextureFilteringData filtering) {
		super(image);

		// By default, images are unblurred and not clamped.

		if (filtering.shouldBlur()) {
			RenderSystem.texParameteri(getGlTextureId(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			RenderSystem.texParameteri(getGlTextureId(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		}

		if (filtering.shouldClamp()) {
			RenderSystem.texParameteri(getGlTextureId(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			RenderSystem.texParameteri(getGlTextureId(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		}
	}

	public static NativeImage decode(byte[] content) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocateDirect(content.length);
		buffer.put(content);
		buffer.flip();

		return NativeImage.read(buffer);
	}
}

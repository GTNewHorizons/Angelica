package net.coderbot.iris.texture.util;

import com.gtnewhorizons.angelica.compat.mojang.NativeImage;
import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import net.coderbot.iris.Iris;

import net.minecraft.client.Minecraft;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.GL11;

import java.io.File;

public class TextureExporter {
	public static void exportTextures(String directory, String filename, int textureId, int mipLevel, int width, int height) {
		String extension = FilenameUtils.getExtension(filename);
		String baseName = filename.substring(0, filename.length() - extension.length() - 1);
		for (int level = 0; level <= mipLevel; ++level) {
			exportTexture(directory, baseName + "_" + level + "." + extension, textureId, level, width >> level, height >> level);
		}
	}

	public static void exportTexture(String directory, String filename, int textureId, int level, int width, int height)  {
		NativeImage nativeImage = new NativeImage(width, height, false);
		GLTextureManager.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
		nativeImage.downloadTexture(level, false);

		File dir = new File(Minecraft.getMinecraft().mcDataDir, directory);
		dir.mkdirs();
		File file = new File(dir, filename);

        try {
            nativeImage.writeToFile(file);
        } catch (Exception e) {
            Iris.logger.error("Could not export texture", e);
        }
	}
}

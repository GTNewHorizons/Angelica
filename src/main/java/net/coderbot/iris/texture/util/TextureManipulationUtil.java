package net.coderbot.iris.texture.util;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class TextureManipulationUtil {
	private static int colorFillFBO = -1;

	public static void fillWithColor(int textureId, int maxLevel, int rgba) {
		if (colorFillFBO == -1) {
			colorFillFBO = OpenGlHelper.func_153165_e/*glGenFramebuffers*/();
		}

		int previousFramebufferId = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
		float[] previousClearColor = new float[4];
		IrisRenderSystem.getFloatv(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);
		int previousTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		int[] previousViewport = new int[4];
		IrisRenderSystem.getIntegerv(GL11.GL_VIEWPORT, previousViewport);

		OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, colorFillFBO);
		GL11.glClearColor(
				(rgba >> 24 & 0xFF) / 255.0f,
				(rgba >> 16 & 0xFF) / 255.0f,
				(rgba >> 8 & 0xFF) / 255.0f,
				(rgba & 0xFF) / 255.0f
		);
		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
		for (int level = 0; level <= maxLevel; ++level) {
			int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
			int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
			GL11.glViewport(0, 0, width, height);
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureId, level);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            if (Minecraft.isRunningOnMac) {
                GL11.glGetError();
            }


            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, level);
		}

		OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, previousFramebufferId);
		GL11.glClearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureId);
		GL11.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
	}
}

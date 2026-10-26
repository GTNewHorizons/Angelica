package net.coderbot.iris.texture.util;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack.stackPush;

public class TextureManipulationUtil {
	private static int colorFillFBO = -1;

	public static void fillWithColor(int textureId, int maxLevel, int rgba) {
        try(final MemoryStack stack = stackPush()) {
            if (colorFillFBO == -1) {
                colorFillFBO = OpenGlHelper.func_153165_e/*glGenFramebuffers*/();
            }

            final int previousFramebufferId = GLSMQueries.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            final FloatBuffer previousClearColorBuffer = stack.mallocFloat(4);
            GLSMQueries.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, previousClearColorBuffer);
            final int previousTextureId = GLSMQueries.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final IntBuffer previousViewportBuffer = stack.mallocInt(4);
            GLSMQueries.glGetInteger(GL11.GL_VIEWPORT, previousViewportBuffer);

            OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, colorFillFBO);
            GLSMBlend.glClearColor(
                (rgba >> 24 & 0xFF) / 255.0f,
                (rgba >> 16 & 0xFF) / 255.0f,
                (rgba >> 8 & 0xFF) / 255.0f,
                (rgba & 0xFF) / 255.0f);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            for (int level = 0; level <= maxLevel; ++level) {
                final int width = GLSMTextures.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
                final int height = GLSMTextures.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
                GLSMRasterizer.glViewport(0, 0, width, height);
                GLSMFramebuffer.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    textureId,
                    level);
                GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);

                GLSMFramebuffer.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    0,
                    level);
            }

            OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, previousFramebufferId);
            GLSMBlend.glClearColor(
                previousClearColorBuffer.get(0),
                previousClearColorBuffer.get(1),
                previousClearColorBuffer.get(2),
                previousClearColorBuffer.get(3));
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureId);
            GLSMRasterizer.glViewport(
                previousViewportBuffer.get(0),
                previousViewportBuffer.get(1),
                previousViewportBuffer.get(2),
                previousViewportBuffer.get(3));
        }
	}
}

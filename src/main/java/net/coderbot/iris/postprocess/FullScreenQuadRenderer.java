package net.coderbot.iris.postprocess;

import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.compat.mojang.DefaultVertexFormat;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.FloatBuffer;

/**
 * Renders a full-screen textured quad to the screen. Used in composite / deferred rendering.
 */
public class FullScreenQuadRenderer {
	private final int quadBuffer;

	public static final FullScreenQuadRenderer INSTANCE = new FullScreenQuadRenderer();

	private FullScreenQuadRenderer() {
		this.quadBuffer = createQuad();
	}

	public void render() {
		begin();

		renderQuad();

		end();
	}

	@SuppressWarnings("deprecation")
	public void begin() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
		// scale the quad from [0, 1] to [-1, 1]
		GL11.glTranslatef(-1.0F, -1.0F, 0.0F);
        GL11.glScalef(2.0F, 2.0F, 0.0F);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadBuffer);
		DefaultVertexFormat.POSITION_TEX.setupBufferState(0L);
	}

	public void renderQuad() {
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
	}

	@SuppressWarnings("deprecation")
	public static void end() {
		DefaultVertexFormat.POSITION_TEX.clearBufferState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
	}

	/**
	 * Creates and uploads a vertex buffer containing a single full-screen quad
	 */
	private static int createQuad() {
        FloatBuffer vertices = BufferUtils.createFloatBuffer(20);
        vertices.put(new float[] {
                // Vertex 0: Top right corner
                1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
                // Vertex 1: Top left corner
                0.0F, 1.0F, 0.0F, 0.0F, 1.0F,
                // Vertex 2: Bottom right corner
                1.0F, 0.0F, 0.0F, 1.0F, 0.0F,
                // Vertex 3: Bottom left corner
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F }).rewind();

		return IrisRenderSystem.bufferStorage(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
	}
}

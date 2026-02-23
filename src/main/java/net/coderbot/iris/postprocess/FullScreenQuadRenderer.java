package net.coderbot.iris.postprocess;


import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

/**
 * Renders a full-screen textured quad to the screen. Used in composite / deferred rendering.
 */
public class FullScreenQuadRenderer {
	private final int quadBuffer;
	private final int vao;

	public static final FullScreenQuadRenderer INSTANCE = new FullScreenQuadRenderer();

	private FullScreenQuadRenderer() {
		this.vao = org.lwjgl.opengl.GL30.glGenVertexArrays();
		GLStateManager.glBindVertexArray(vao);
		this.quadBuffer = createQuad();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadBuffer);
		DefaultVertexFormat.POSITION_TEXTURE.setupBufferState(0L);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GLStateManager.glBindVertexArray(0);
	}

	public void render() {
		begin();

		renderQuad();

		end();
	}

	public void begin() {
        GLStateManager.disableDepthTest();

        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
		// scale the quad from [0, 1] to [-1, 1]
		GLStateManager.glTranslatef(-1.0F, -1.0F, 0.0F);
        GLStateManager.glScalef(2.0F, 2.0F, 1.0F);

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();

		GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GLStateManager.glBindVertexArray(vao);
	}

	public void renderQuad() {
        GLStateManager.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
	}

	public static void end() {
        GLStateManager.glBindVertexArray(0);

        GLStateManager.enableDepthTest();

        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
		GLStateManager.glPopMatrix();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPopMatrix();
	}

	private static final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);

	/** Per-program cached uniform locations: [mvLoc, projLoc]. Populated on first miss. */
	private static final Int2ObjectOpenHashMap<int[]> compositeLocCache = new Int2ObjectOpenHashMap<>();

	public static void uploadCompositeMatrices() {
		final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		if (program == 0) return;

		int[] locs = compositeLocCache.get(program);
		if (locs == null) {
			locs = new int[] {
				GL20.glGetUniformLocation(program, "iris_ModelViewMatrix"),
				GL20.glGetUniformLocation(program, "iris_ProjectionMatrix")
			};
			compositeLocCache.put(program, locs);
		}

		if (locs[0] != -1) {
			GLStateManager.getModelViewMatrix().get(matBuf);
			matBuf.rewind();
			GL20.glUniformMatrix4(locs[0], false, matBuf);
		}

		if (locs[1] != -1) {
			GLStateManager.getProjectionMatrix().get(matBuf);
			matBuf.rewind();
			GL20.glUniformMatrix4(locs[1], false, matBuf);
		}
	}

	/**
	 * Creates and uploads a vertex buffer containing a single full-screen quad
	 */
	private static int createQuad() {
        final FloatBuffer vertices = BufferUtils.createFloatBuffer(20);
        vertices.put(new float[] {
                // Vertex 0: Top right corner
                1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
                // Vertex 1: Top left corner
                0.0F, 1.0F, 0.0F, 0.0F, 1.0F,
                // Vertex 2: Bottom right corner
                1.0F, 0.0F, 0.0F, 1.0F, 0.0F,
                // Vertex 3: Bottom left corner
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F }).rewind();

		return RenderSystem.bufferStorage(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
	}
}

package net.coderbot.iris.postprocess;


import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
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
		this.vao = GLSMVertexInput.glGenVertexArrays();
		GLSMVertexInput.glBindVertexArray(vao);
		this.quadBuffer = createQuad();
		GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadBuffer);
		DefaultVertexFormat.POSITION_TEXTURE.setupBufferState(0L);
		GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GLSMVertexInput.glBindVertexArray(0);
	}

	public void render() {
		begin();

		renderQuad();

		end();
	}

	public void begin() {
        GLSMDepthStencilAlpha.disableDepthTest();

        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();
		// scale the quad from [0, 1] to [-1, 1]
		GLSMMatrices.glTranslatef(-1.0F, -1.0F, 0.0F);
        GLSMMatrices.glScalef(2.0F, 2.0F, 1.0F);

        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();

		GLSMCurrentAttribs.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GLSMVertexInput.glBindVertexArray(vao);
	}

	public void renderQuad() {
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
	}

	public static void end() {
        GLSMVertexInput.glBindVertexArray(0);

        GLSMDepthStencilAlpha.enableDepthTest();

        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
		GLSMMatrices.glPopMatrix();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPopMatrix();
	}

	private static final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);

	/** Per-program cached uniform locations: [mvLoc, projLoc]. Populated on first miss. */
	private static final Int2ObjectOpenHashMap<int[]> compositeLocCache = new Int2ObjectOpenHashMap<>();

	/** Clear cached uniform locations. Must be called when composite programs are destroyed (e.g. shader reload). */
	public static void clearLocCache() {
		compositeLocCache.clear();
	}

	public static void uploadCompositeMatrices() {
		final int program = GLSMQueries.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		if (program == 0) return;

		int[] locs = compositeLocCache.get(program);
		if (locs == null) {
			locs = new int[] {
				GLSMPrograms.glGetUniformLocation(program, "iris_ModelViewMatrix"),
				GLSMPrograms.glGetUniformLocation(program, "iris_ProjectionMatrix")
			};
			compositeLocCache.put(program, locs);
		}

		if (locs[0] != -1) {
			GLSMMatrices.getModelViewMatrix().get(matBuf);
			matBuf.rewind();
			GLSMPrograms.glUniformMatrix4(locs[0], false, matBuf);
		}

		if (locs[1] != -1) {
			GLSMMatrices.getProjectionMatrix().get(matBuf);
			matBuf.rewind();
			GLSMPrograms.glUniformMatrix4(locs[1], false, matBuf);
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

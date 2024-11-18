package com.gtnewhorizons.angelica.debug;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;

public class FrametimeGraph {
    public static final int NUM_FRAMETIMES = 240;
    public static final long[] frametimes = new long[NUM_FRAMETIMES];
    // Circular buffer holding the last 240 frametimes, in nanoseconds
    public static int frametimesHead = 0; // one ahead of the position of the last frametime
    private boolean initialized = false;
    private ShaderProgram shader;
    private static final int WEIGHT = 2; // in pixels
    private static final int SAMPLES_WIDTH = NUM_FRAMETIMES * WEIGHT;
    private int aPos;
    private int uFBWidth;
    private int vertBuf;
    // Two floats (x,y)
    private static final int VERT_FLOATS = 2;
    private static final int VERT_COUNT = 4;


    private void init() {
        shader = new ShaderProgram(
            "angelica",
            "shaders/frametimes.vert.glsl",
            "shaders/frametimes.frag.glsl");
        shader.use();

        // Register attributes
        aPos = shader.getAttribLocation("pos");

        // Register uniforms
        uFBWidth = shader.getUniformLocation("fbWidth");

        // Load vertex buffer
        vertBuf = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vertBuf);
        // Since we use a triangle strip, we only need 4 verts
        final ByteBuffer vertexes = BufferUtils.createByteBuffer(4 * VERT_COUNT * VERT_FLOATS);
        // The Y coords are simple - the rect is from top to bottom, 1.0 to -1.0
        // The X coord get scaled by the framebuffer size in the vert shader
        for (int y = -1; y < 2; y += 2) {
            for (int x = 0; x < 2; ++x) {
                vertexes.putFloat(x == 0 ? 2 : 482);
                vertexes.putFloat(y);
            }
        }
        vertexes.rewind();
        glBufferData(GL_ARRAY_BUFFER, vertexes, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Load initial value for uniforms
        glUniform1f(uFBWidth, Minecraft.getMinecraft().displayWidth);

        ShaderProgram.clear();
    }

    public void render() {
        if (!initialized) {
            init();
            initialized = true;
        }
        /**
         * We try to copy modern vanilla's tracker.
         * It is 484 wide by 124 tall, including the 2px borders.
         * The background is ARGB 90505050, the borders FFFFFFFF, and the text FFE0E0E0.
         * First the samples are rendered, then the background/borders, then the text. The samples are rendered by a
         * shader on a transparent rect 480px wide and as high as the framebuffer. Next, the background is drawn via a
         * 484x124 translucent rect, and finally FontRenderer slaps the text on top. The shader pipeline is only needed
         * for the first draw.
         */
        shader.use();

        // Load uniforms
        glUniform1f(uFBWidth, Minecraft.getMinecraft().displayWidth);

        // Draw!
        glBindBuffer(GL_ARRAY_BUFFER, vertBuf);
        glEnableVertexAttribArray(aPos);
        glVertexAttribPointer(aPos, VERT_FLOATS, GL_FLOAT, false, VERT_FLOATS * 4, 0);
        glEnableClientState(GL_VERTEX_ARRAY);
        glDisable(GL_BLEND);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, VERT_COUNT);

        glEnable(GL_BLEND);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableVertexAttribArray(aPos);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        ShaderProgram.clear();
    }
}

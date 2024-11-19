package com.gtnewhorizons.angelica.debug;

import static org.lwjgl.opengl.GL11.GL_ALPHA_TEST;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
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
import static org.lwjgl.opengl.GL20.glUniform1;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;

/**
 * Pretty much just {@link FrametimeGraph} but on the left
 */
public class TPSGraph {
    public static final int NUM_SAMPLES = 240;
    // Circular buffer holding the last 240 samples, in nanoseconds
    public int samplesHead = 0; // one ahead of the position of the last sample
    private boolean initialized = false;
    private ShaderProgram shader;
    private int aPos;
    private int uFBWidth;
    private int uFBHeight;
    private int uScaleFactor;
    private int uHeadIdx;
    private int uSamples;
    private int vertBuf;
    // Two floats (x,y)
    private static final int VERT_FLOATS = 2;
    private static final int VERT_COUNT = 4;
    // Due to GLSL 120 limitations, it's just easier to use floats
    private final FloatBuffer sampleBuf = BufferUtils.createFloatBuffer(NUM_SAMPLES);
    private static final int BORDER = 1;
    private static final int HEIGHT = 60;
    private static final int WIDTH = NUM_SAMPLES;
    private static final int FONT_COLOR = 0xFFE0E0E0;
    private static final ResourceLocation TEXTURE = new ResourceLocation("angelica:textures/tps_fg.png");
    // At 1x scale, 20 TPS should be 60 px. 20 FPS = 50_000_000ns per frame, 60px/50_000_000ns = 0.0000012 px/ns
    private static final float PIXELS_PER_NS = 0.0000012f;

    public void putSample(long time) {
        sampleBuf.put(samplesHead, (float) time);
        samplesHead = (samplesHead + 1) % NUM_SAMPLES;
    }

    private void init() {
        shader = new ShaderProgram(
            "angelica",
            "shaders/debug_graph.vert.glsl",
            "shaders/debug_graph.frag.glsl");
        shader.use();

        // Register attributes
        aPos = shader.getAttribLocation("pos");

        // Register uniforms
        uFBWidth = shader.getUniformLocation("fbWidth");
        uFBHeight = shader.getUniformLocation("fbHeight");
        uScaleFactor = shader.getUniformLocation("scaleFactor");
        uSamples = shader.getUniformLocation("samples");
        uHeadIdx = shader.getUniformLocation("headIdx");
        final int uPxPerNs = shader.getUniformLocation("pxPerNs");

        // Load vertex buffer
        vertBuf = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vertBuf);
        final FloatBuffer vertices = BufferUtils.createFloatBuffer(VERT_COUNT * VERT_FLOATS);
        // Since we use a triangle strip, we only need 4 vertices
        //@formatter:off
        vertices.put(new float[]{
            BORDER        , BORDER,
            WIDTH + BORDER, BORDER,
            BORDER        , HEIGHT + BORDER,
            WIDTH + BORDER, HEIGHT + BORDER
        });
        //@formatter:on
        vertices.rewind();

        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        final Minecraft mc = Minecraft.getMinecraft();
        final ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

        // Load initial value for uniforms
        glUniform1f(uFBWidth, mc.displayWidth);
        glUniform1f(uFBHeight, mc.displayHeight);
        glUniform1f(uScaleFactor, sr.getScaleFactor());
        glUniform1i(uHeadIdx, samplesHead);
        glUniform1(uSamples, sampleBuf);
        glUniform1f(uPxPerNs, PIXELS_PER_NS);

        ShaderProgram.clear();
    }

    public void render() {
        if (!initialized) {
            init();
            initialized = true;
        }

        double min, max, sum;
        min = max = sum = sampleBuf.get(0);
        for (int i = 1; i < NUM_SAMPLES; i++) {
            min = Math.min(min, sampleBuf.get(i));
            max = Math.max(max, sampleBuf.get(i));
            sum += sampleBuf.get(i);
        }

        /*
         * We try to copy modern vanilla's tracker.
         * It is 240 (FRAME_TIMES) wide by 60 tall, including the 1px borders.
         * We instead make it 242x62 including borders to prevent the borders from overlapping the samples.
         * The background is ARGB 90505050, the borders FFFFFFFF, and the text FFE0E0E0.
         * First the background is rendered, then the samples, then the foreground texture and the text.
         * The background is drawn via a 242x62 translucent rect.
         * Next, the samples are rendered by a shader on a transparent 240x60 rect.
         * Finally, the foreground texture and the text is rendered on top.
         * The shader pipeline is only needed for the sample draw.
         */

        final Minecraft mc = Minecraft.getMinecraft();
        final ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        final int height = sr.getScaledHeight();

        // Setup GL state
        final Tessellator tess = Tessellator.instance;
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_ALPHA_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Draw background
        glDisable(GL_TEXTURE_2D);
        glColor4f(0x50 / 255.0F, 0x50 / 255.0F, 0x50 / 255.0F, 0x90 / 255.0F);
        tess.startDrawingQuads();
        tess.addVertex(WIDTH + BORDER * 2, height - HEIGHT - BORDER * 2, 0.0D);
        tess.addVertex(0, height - HEIGHT - BORDER * 2, 0.0D);
        tess.addVertex(0, height, 0.0D);
        tess.addVertex(WIDTH + BORDER * 2, height, 0.0D);
        tess.draw();
        glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        glEnable(GL_TEXTURE_2D);


        // Draw samples
        shader.use();

        // Load uniforms
        glUniform1f(uFBWidth, mc.displayWidth);
        glUniform1f(uFBHeight, mc.displayHeight);
        glUniform1f(uScaleFactor, sr.getScaleFactor());
        glUniform1i(uHeadIdx, samplesHead);
        glUniform1(uSamples, sampleBuf);

        glBindBuffer(GL_ARRAY_BUFFER, vertBuf);
        glEnableVertexAttribArray(aPos);
        glVertexAttribPointer(aPos, VERT_FLOATS, GL_FLOAT, false, VERT_FLOATS * 4, 0);
        glEnableClientState(GL_VERTEX_ARRAY);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, VERT_COUNT);

        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableVertexAttribArray(aPos);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        ShaderProgram.clear();

        // Draw foreground

        mc.getTextureManager().bindTexture(TEXTURE);
        tess.startDrawingQuads();
        tess.addVertexWithUV(WIDTH + BORDER * 2, height - HEIGHT - BORDER * 2, 0.0D, 1, 0);
        tess.addVertexWithUV(0, height - HEIGHT - BORDER * 2, 0.0D, 0, 0);
        tess.addVertexWithUV(0, height, 0.0D, 0, 1);
        tess.addVertexWithUV(WIDTH + BORDER * 2, height, 0.0D, 1, 1);
        tess.draw();

        // Reset GL state
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_BLEND);

        // Draw text
        String minStr = (int) min / 1_000_000 + " ms min";
        String avgStr = (int) sum / 1_000_000 / NUM_SAMPLES + " ms avg";
        String maxStr = (int) max / 1_000_000 + " ms max";
        final FontRenderer fr = mc.fontRenderer;
        final int top = height - HEIGHT - BORDER * 2 - fr.FONT_HEIGHT;
        fr.drawString(minStr, BORDER * 2, top, FONT_COLOR, true);
        fr.drawString(avgStr, BORDER + WIDTH / 2 - fr.getStringWidth(avgStr) / 2, top, FONT_COLOR, true);
        fr.drawString(maxStr, BORDER * 2 + WIDTH - fr.getStringWidth(maxStr), top, FONT_COLOR, true);
    }
}

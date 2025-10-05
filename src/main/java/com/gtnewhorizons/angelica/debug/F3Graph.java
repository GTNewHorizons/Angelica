package com.gtnewhorizons.angelica.debug;

import static org.lwjgl.opengl.GL11.GL_ALPHA_TEST;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
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
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack.*;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;

public abstract class F3Graph {
    private static final int NUM_SAMPLES = 240;
    // Two floats (x,y)
    private static final int VERT_FLOATS = 2;
    private static final int VERT_COUNT = 4;
    private static final int BORDER = 1;
    private static final int HEIGHT = 60;
    private static final int WIDTH = NUM_SAMPLES;
    private static final int FONT_COLOR = 0xFFE0E0E0;
    // Due to GLSL 120 limitations, it's just easier to use floats
    private final FloatBuffer sampleBuf = BufferUtils.createFloatBuffer(NUM_SAMPLES);
    private final long[] samples = new long[NUM_SAMPLES]; // long version for calculations
    // Circular buffer holding the last 240 samples, in nanoseconds
    private int samplesHead = 0; // one ahead of the position of the last sample
    private boolean initialized = false;
    private ShaderProgram shader;
    private int aPos;
    private int uFBWidth;
    private int uFBHeight;
    private int uScaleFactor;
    private int uHeadIdx;
    private int uSamples;
    private int vertBuf;
    private final ResourceLocation texture;
    private final float pxPerNs;
    private final boolean left;
    private long sum;

    protected F3Graph(ResourceLocation texture, float pxPerNs, boolean left) {
        this.texture = texture;
        this.pxPerNs = pxPerNs;
        this.left = left;
    }

    public void putSample(long time) {
        // Manage running trackers
        sum -= samples[samplesHead];
        sum += time;
        samples[samplesHead] = time;

        sampleBuf.put(samplesHead, (float) time);
        samplesHead = (samplesHead + 1) % NUM_SAMPLES;
    }

    private int getVertX(ScaledResolution sres, int idx) {
        if (left) {
            return switch (idx) {
                case 0, 3 -> WIDTH + BORDER * 2;
                case 1, 2 -> 0;
                default -> throw new RuntimeException("Tried to get out-of-bounds vertex for graph!");
            };
        }

        final int displayWidth = sres.getScaledWidth();
        return switch (idx) {
            case 0, 3 -> displayWidth;
            case 1, 2 -> displayWidth - WIDTH - BORDER * 2;
            default -> throw new RuntimeException("Tried to get out-of-bounds vertex for graph!");
        };
    }

    private int getVertY(ScaledResolution sres, int idx) {
        final int displayHeight = sres.getScaledHeight();
        return switch (idx) {
            case 0, 1 -> displayHeight - HEIGHT - BORDER * 2;
            case 2, 3 -> displayHeight;
            default -> throw new RuntimeException("Tried to get out-of-bounds vertex for graph!");
        };
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
        final int uLeft = shader.getUniformLocation("left");

        // Load vertex buffer
        vertBuf = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vertBuf);
        try (final MemoryStack stack = stackPush()) {
            final FloatBuffer vertices = stack.mallocFloat(VERT_COUNT * VERT_FLOATS);
            // Since we use a triangle strip, we only need 4 vertices. The quad extends to the top of the screen so spikes
            // don't get truncated. The max height is replaced in the vert shader, no need to be precise.
            vertices.put(new float[]{
                BORDER,         BORDER,
                WIDTH + BORDER, BORDER,
                BORDER,         Float.MAX_VALUE,
                WIDTH + BORDER, Float.MAX_VALUE
            });
            vertices.rewind();

            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        final Minecraft mc = Minecraft.getMinecraft();
        final ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

        // Load initial value for uniforms
        glUniform1f(uFBWidth, mc.displayWidth);
        glUniform1f(uFBHeight, mc.displayHeight);
        glUniform1f(uScaleFactor, sr.getScaleFactor());
        glUniform1i(uHeadIdx, samplesHead);
        glUniform1(uSamples, sampleBuf);
        glUniform1f(uPxPerNs, pxPerNs);
        glUniform1i(uLeft, left ? 1 : 0); // this is how you load bool uniforms

        ShaderProgram.clear();
    }

    public void render() {
        if (!initialized) {
            init();
            initialized = true;
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
        for (int i = 0; i < 4; ++i) {
            tess.addVertex(getVertX(sr, i), getVertY(sr, i), 0);
        }
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

        // Left side graphs are inverted, so temporarily disable culling
        if (!left) glDisable(GL_CULL_FACE);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, VERT_COUNT);

        if (!left) glEnable(GL_CULL_FACE);

        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableVertexAttribArray(aPos);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        ShaderProgram.clear();

        // Draw foreground

        mc.getTextureManager().bindTexture(texture);
        tess.startDrawingQuads();
        tess.addVertexWithUV(getVertX(sr, 0), getVertY(sr, 0), 0.0D, 1, 0);
        tess.addVertexWithUV(getVertX(sr, 1), getVertY(sr, 1), 0.0D, 0, 0);
        tess.addVertexWithUV(getVertX(sr, 2), getVertY(sr, 2), 0.0D, 0, 1);
        tess.addVertexWithUV(getVertX(sr, 3), getVertY(sr, 3), 0.0D, 1, 1);
        tess.draw();

        // Reset GL state
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_BLEND);

        // Draw text
        // Ensure running counters are reset on first sample
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < NUM_SAMPLES; ++i) {
            min = Math.min(min, samples[i]);
            max = Math.max(max, samples[i]);
        }
        String minStr = String.format("%.1f ms min", min / 1_000_000D);
        String avgStr = String.format("%.1f ms avg", sum / 1_000_000 / (double) NUM_SAMPLES);
        String maxStr = String.format("%.1f ms max", max / 1_000_000D);
        final FontRenderer fr = mc.fontRenderer;
        final int top = sr.getScaledHeight() - HEIGHT - BORDER * 2 - fr.FONT_HEIGHT;
        final int scaledWidth = sr.getScaledWidth();

        if (left) {
            fr.drawString(minStr, BORDER * 2, top, FONT_COLOR, true);
            fr.drawString(avgStr, BORDER + WIDTH / 2 - fr.getStringWidth(avgStr) / 2, top, FONT_COLOR, true);
            fr.drawString(maxStr, BORDER * 2 + WIDTH - fr.getStringWidth(maxStr), top, FONT_COLOR, true);
        } else {
            fr.drawString(minStr, scaledWidth - (BORDER * 2 + WIDTH), top, FONT_COLOR, true);
            fr.drawString(avgStr, scaledWidth - (BORDER + WIDTH / 2 + fr.getStringWidth(avgStr) / 2), top, FONT_COLOR, true);
            fr.drawString(maxStr, scaledWidth - (BORDER * 2 + fr.getStringWidth(maxStr)), top, FONT_COLOR, true);
        }
    }
}

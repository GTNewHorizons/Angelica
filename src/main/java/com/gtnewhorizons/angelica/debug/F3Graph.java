package com.gtnewhorizons.angelica.debug;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.debug.render.DebugDrawScope;
import com.gtnewhorizons.angelica.debug.render.DebugGeometry;
import com.gtnewhorizons.angelica.debug.render.DebugPrograms;
import com.gtnewhorizons.angelica.debug.render.NativeScratch;
import com.gtnewhorizons.angelica.debug.screen.DebugNumbers;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack.stackPush;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;

public abstract class F3Graph {
    private static final int NUM_SAMPLES = 240;
    // Two floats (x,y)
    private static final int VERT_FLOATS = 2;
    private static final int VERT_COUNT = 4;
    private static final int BORDER = 1;
    private static final int HEIGHT = 60;
    private static final int WIDTH = NUM_SAMPLES;
    private static final int FONT_COLOR = 0xFFE0E0E0;
    private static final int BACKGROUND_ARGB = 0x90505050;
    private static final int FOREGROUND_ARGB = 0xFFFFFFFF;
    private static final int COLOR_FLAGS = VertexFlags.COLOR_BIT;
    private static final int TEXTURED_FLAGS = VertexFlags.TEXTURE_BIT | VertexFlags.COLOR_BIT;
    private static final VertexFormat COLOR_FORMAT = DefaultVertexFormat.ALL_FORMATS[COLOR_FLAGS];
    private static final VertexFormat TEXTURED_FORMAT = DefaultVertexFormat.ALL_FORMATS[TEXTURED_FLAGS];
    private static final int COLOR_STRIDE = COLOR_FORMAT.getVertexSize();
    private static final int TEXTURED_STRIDE = TEXTURED_FORMAT.getVertexSize();
    private static final int TEXTURED_UV_OFFSET = DebugGeometry.offsetOf(TEXTURED_FORMAT, VertexFormatElement.Usage.PRIMARY_UV);
    private static final int TEXTURED_COLOR_OFFSET = DebugGeometry.offsetOf(TEXTURED_FORMAT, VertexFormatElement.Usage.COLOR);
    private static final int COLOR_COLOR_OFFSET = DebugGeometry.offsetOf(COLOR_FORMAT, VertexFormatElement.Usage.COLOR);
    private static final int QUAD_BYTES = 4 * Math.max(COLOR_STRIDE, TEXTURED_STRIDE);
    // Due to GLSL 120 limitations, it's just easier to use floats
    private final FloatBuffer sampleBuf = BufferUtils.createFloatBuffer(NUM_SAMPLES);
    private final long[] samples = new long[NUM_SAMPLES]; // long version for calculations
    // Circular buffer holding the last 240 samples, in nanoseconds
    private int samplesHead = 0; // one ahead of the position of the last sample
    private boolean initialized = false;
    private int program;
    private int uFBWidth;
    private int uFBHeight;
    private int uScaleFactor;
    private int uHeadIdx;
    private int uSamples;
    private int vertBuf;
    private int vao;
    private final DebugDrawScope scope = new DebugDrawScope();
    private final NativeScratch scratch = new NativeScratch(QUAD_BYTES);
    private final Matrix4f mvp = new Matrix4f();
    private final ResourceLocation texture;
    private final float pxPerNs;
    private final boolean left;
    private long sum;
    private final StringBuilder label = new StringBuilder(16);
    private String minString;
    private String avgString;
    private String maxString;
    private int labelTick = Integer.MIN_VALUE;
    private int avgWidth;
    private int maxWidth;

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

    public static int reservedHeight(FontRenderer fontRenderer) {
        return HEIGHT + BORDER * 2 + fontRenderer.FONT_HEIGHT;
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
        final int handle = DebugPrograms.link("angelica:debug_graph", "angelica:debug_graph.vert.glsl", "angelica:debug_graph.frag.glsl", ShaderConstants.EMPTY, new String[]{"pos"}, new int[]{0});
        program = handle;
        GLStateManager.glUseProgram(handle);

        uFBWidth = GLStateManager.glGetUniformLocation(handle, "fbWidth");
        uFBHeight = GLStateManager.glGetUniformLocation(handle, "fbHeight");
        uScaleFactor = GLStateManager.glGetUniformLocation(handle, "scaleFactor");
        uSamples = GLStateManager.glGetUniformLocation(handle, "samples");
        uHeadIdx = GLStateManager.glGetUniformLocation(handle, "headIdx");
        final int uPxPerNs = GLStateManager.glGetUniformLocation(handle, "pxPerNs");
        final int uLeft = GLStateManager.glGetUniformLocation(handle, "left");

        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);

        vertBuf = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL_ARRAY_BUFFER, vertBuf);
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

            GLStateManager.glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        }

        // Configure vertex attribute in the VAO
        GLStateManager.glVertexAttribPointer(0, VERT_FLOATS, GL_FLOAT, false, VERT_FLOATS * 4, 0);
        GLStateManager.glEnableVertexAttribArray(0);

        GLStateManager.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);

        GLStateManager.glUniform1f(uPxPerNs, pxPerNs);
        GLStateManager.glUniform1i(uLeft, left ? 1 : 0);
    }

    public void render(ScaledResolution sr, int tick) {
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

        GLStateManager.getProjectionMatrix().mul(GLStateManager.getModelViewMatrix(), mvp);

        scope.beginHud();
        try {
            if (!initialized) {
                init();
                initialized = true;
            }
            drawGraph(mc, sr);
        } finally {
            scope.end();
        }


        // Draw text
        final FontRenderer fr = mc.fontRenderer;
        if (tick != labelTick) {
            labelTick = tick;
            updateLabels(fr);
        }
        final int top = sr.getScaledHeight() - reservedHeight(fr);
        final int scaledWidth = sr.getScaledWidth();
        final int minX;
        final int avgX;
        final int maxX;
        if (left) {
            minX = BORDER * 2;
            avgX = BORDER + WIDTH / 2 - avgWidth / 2;
            maxX = BORDER * 2 + WIDTH - maxWidth;
        } else {
            minX = scaledWidth - (BORDER * 2 + WIDTH);
            avgX = scaledWidth - (BORDER + WIDTH / 2 + avgWidth / 2);
            maxX = scaledWidth - (BORDER * 2 + maxWidth);
        }
        fr.drawString(minString, minX, top, FONT_COLOR, true);
        fr.drawString(avgString, avgX, top, FONT_COLOR, true);
        fr.drawString(maxString, maxX, top, FONT_COLOR, true);
    }

    private void drawGraph(Minecraft mc, ScaledResolution sr) {
        scratch.ensure(QUAD_BYTES);
        long p = scratch.address();
        for (int i = 0; i < 4; ++i, p += COLOR_STRIDE) {
            DebugGeometry.putPosition(p, getVertX(sr, i), getVertY(sr, i), 0.0F);
            DebugGeometry.putColor(p + COLOR_COLOR_OFFSET, BACKGROUND_ARGB);
        }
        final var color = DebugPrograms.color();
        color.use();
        color.mvp(mvp);
        TessellatorStreamingDrawer.drawPacked(scratch.window(4 * COLOR_STRIDE), GL11.GL_QUADS, COLOR_FLAGS, 4);

        GLStateManager.glUseProgram(program);
        GLStateManager.glUniform1f(uFBWidth, mc.displayWidth);
        GLStateManager.glUniform1f(uFBHeight, mc.displayHeight);
        GLStateManager.glUniform1f(uScaleFactor, sr.getScaleFactor());
        GLStateManager.glUniform1i(uHeadIdx, samplesHead);
        GLStateManager.glUniform1(uSamples, sampleBuf);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glDrawArrays(GL_TRIANGLE_STRIP, 0, VERT_COUNT);

        mc.getTextureManager().bindTexture(texture);
        p = scratch.address();
        for (int i = 0; i < 4; ++i, p += TEXTURED_STRIDE) {
            DebugGeometry.putPosition(p, getVertX(sr, i), getVertY(sr, i), 0.0F);
            memPutFloat(p + TEXTURED_UV_OFFSET, (i == 0 || i == 3) ? 1.0F : 0.0F);
            memPutFloat(p + TEXTURED_UV_OFFSET + 4, i >= 2 ? 1.0F : 0.0F);
            DebugGeometry.putColor(p + TEXTURED_COLOR_OFFSET, FOREGROUND_ARGB);
        }
        final var textured = DebugPrograms.textured();
        textured.use();
        textured.mvp(mvp);
        TessellatorStreamingDrawer.drawPacked(scratch.window(4 * TEXTURED_STRIDE), GL11.GL_QUADS, TEXTURED_FLAGS, 4);
    }

    private void updateLabels(FontRenderer fr) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < NUM_SAMPLES; ++i) {
            min = Math.min(min, samples[i]);
            max = Math.max(max, samples[i]);
        }
        minString = label(min / 1_000_000D, " ms min");
        avgString = label(sum / 1_000_000 / (double) NUM_SAMPLES, " ms avg");
        maxString = label(max / 1_000_000D, " ms max");
        avgWidth = fr.getStringWidth(avgString);
        maxWidth = fr.getStringWidth(maxString);
    }

    private String label(double millis, String suffix) {
        label.setLength(0);
        DebugNumbers.appendFixed(label, millis, 1);
        return label.append(suffix).toString();
    }
}

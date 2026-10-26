package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.FontProvider;
import com.gtnewhorizons.angelica.client.font.GlyphSettings;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.debug.render.DebugGeometry;
import com.gtnewhorizons.angelica.debug.render.DebugProgram;
import com.gtnewhorizons.angelica.debug.render.DebugPrograms;
import com.gtnewhorizons.angelica.debug.render.NativeScratch;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;

final class DebugLabelBatch {

    static final int MAX_LABELS = 8192;
    static final int MAX_FALLBACK_LABELS = 256;
    static final int MAX_VALUE = 99;
    static final float SCALE = 0.02F;
    static final float NEAR = 0.05F;
    static final float MIN_PIXELS = 4.0F;
    static final float FRUSTUM_MARGIN = 0.1F;

    private static final int DIGITS = 10;
    private static final int FLAGS = VertexFlags.TEXTURE_BIT | VertexFlags.COLOR_BIT;
    private static final VertexFormat FORMAT = DefaultVertexFormat.ALL_FORMATS[FLAGS];
    private static final int STRIDE = FORMAT.getVertexSize();
    private static final int TEXTURE_OFFSET = DebugGeometry.offsetOf(FORMAT, VertexFormatElement.Usage.PRIMARY_UV);
    private static final int COLOR_OFFSET = DebugGeometry.offsetOf(FORMAT, VertexFormatElement.Usage.COLOR);
    private static final int QUAD_BYTES = 4 * STRIDE;
    private static final int INITIAL_BYTES = 0x4000;
    private static final String[] NUMBERS = numbers();

    private final float[] glyphU = new float[DIGITS];
    private final float[] glyphV = new float[DIGITS];
    private final float[] glyphUSize = new float[DIGITS];
    private final float[] glyphVSize = new float[DIGITS];
    private final float[] glyphWidth = new float[DIGITS];
    private final float[] glyphTop = new float[DIGITS];
    private final float[] glyphHeight = new float[DIGITS];
    private final float[] glyphAdvance = new float[DIGITS];
    private final int[] glyphBucket = new int[DIGITS];
    private final int[] bucketTexture = new int[DIGITS];
    private final NativeScratch[] bucketScratch = new NativeScratch[DIGITS];
    private final int[] bucketBytes = new int[DIGITS];
    private final Matrix4f labelMatrix = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f fallbackModelView = new Matrix4f();
    private final float[] fallbackColor = new float[4];
    private GlyphSettings metrics;
    private boolean metricsValid;
    private boolean metricsUnicode;
    private float metricsLineHeight;
    private int bucketCount;
    private int visible;
    private int limit;
    private float projectionX;
    private float projectionY;
    private int viewportHeight;
    private float lineHeight;
    private FontRenderer font;
    private BatchingFontRenderer batcher;

    DebugLabelBatch() {
        for (int i = 0; i < DIGITS; i++) bucketScratch[i] = new NativeScratch(INITIAL_BYTES);
    }

    private static String[] numbers() {
        final String[] out = new String[MAX_VALUE + 1];
        for (int i = 0; i <= MAX_VALUE; i++) {
            out[i] = Integer.toString(i);
        }
        return out;
    }

    static int digitCount(int value) {
        return value >= 10 ? 2 : 1;
    }

    static int digitAt(int value, int index) {
        if (value < 10) return value;
        return index == 0 ? value / 10 : value % 10;
    }

    static float stringWidth(int value, float[] advance) {
        return value >= 10 ? advance[value / 10] + advance[value % 10] : advance[value];
    }

    static float digitLeft(int value, int index, float[] advance) {
        final float left = -stringWidth(value, advance) * 0.5F;
        return index == 0 ? left : left + advance[value / 10];
    }

    static boolean isVisible(float eyeX, float eyeY, float eyeZ, float halfWidth, float height, float projectionX, float projectionY, int viewportHeight) {
        final float depth = -eyeZ;
        if (depth < NEAR) return false;
        if (height * projectionY * viewportHeight * 0.5F < MIN_PIXELS * depth) return false;
        final float reach = depth * (1.0F + FRUSTUM_MARGIN);
        final float limitX = reach / projectionX + halfWidth;
        if (eyeX > limitX || eyeX < -limitX) return false;
        final float limitY = reach / projectionY + height;
        return eyeY <= limitY && eyeY >= -limitY;
    }

    void begin(FontRenderer fontRenderer, Matrix4fc projection, int viewportHeight) {
        this.font = fontRenderer;
        this.projection.set(projection);
        this.projectionX = projection.m00();
        this.projectionY = projection.m11();
        this.viewportHeight = viewportHeight;
        this.lineHeight = fontRenderer.FONT_HEIGHT - 1.0F;
        this.visible = 0;
        this.batcher = AngelicaConfig.enableFontRenderer && fontRenderer instanceof FontRendererAccessor accessor ? accessor.angelica$getBatcher() : null;
        if (batcher == null) {
            limit = MAX_FALLBACK_LABELS;
            metricsValid = false;
            bucketCount = 0;
            for (int d = 0; d < DIGITS; d++) {
                glyphAdvance[d] = fontRenderer.getCharWidth((char) ('0' + d));
            }
            pinFallback();
            return;
        }
        limit = MAX_LABELS;
        final boolean unicode = fontRenderer.getUnicodeFlag();
        final GlyphSettings settings = GlyphSettings.current();
        if (metricsValid && unicode == metricsUnicode && lineHeight == metricsLineHeight && settings.sameMetricsAs(metrics)) return;
        metrics = settings;
        metricsValid = false;
        bucketCount = 0;
        final float scaleX = batcher.getGlyphScaleX();
        final float scaleY = batcher.getGlyphScaleY();
        for (int d = 0; d < DIGITS; d++) {
            final char chr = (char) ('0' + d);
            final FontProvider provider = batcher.glyphProvider(chr, unicode);
            glyphU[d] = provider.getUStart(chr);
            glyphV[d] = provider.getVStart(chr);
            glyphUSize[d] = provider.getUSize(chr);
            glyphVSize[d] = provider.getVSize(chr);
            glyphWidth[d] = provider.getGlyphW(chr) * scaleX - 1.0F;
            glyphTop[d] = BatchingFontRenderer.glyphTop(provider, 0.0F, lineHeight, scaleY);
            glyphHeight[d] = BatchingFontRenderer.glyphHeight(provider, lineHeight, scaleY);
            glyphAdvance[d] = batcher.glyphAdvance(provider, chr, false, scaleX);
            glyphBucket[d] = bucketFor(provider.getTexture(chr));
        }
        metricsUnicode = unicode;
        metricsLineHeight = lineHeight;
        metricsValid = true;
    }

    private int bucketFor(int texture) {
        for (int i = 0; i < bucketCount; i++) {
            if (bucketTexture[i] == texture) return i;
        }
        final int bucket = bucketCount++;
        bucketTexture[bucket] = texture;
        bucketBytes[bucket] = 0;
        return bucket;
    }

    void add(DebugLabels labels, Matrix4fc modelView) {
        if (visible >= limit) return;
        final float[] positions = labels.pos;
        final int[] values = labels.value;
        final int[] colors = labels.color;
        final int count = labels.count;
        final float m00 = modelView.m00();
        final float m01 = modelView.m01();
        final float m02 = modelView.m02();
        final float m10 = modelView.m10();
        final float m11 = modelView.m11();
        final float m12 = modelView.m12();
        final float m20 = modelView.m20();
        final float m21 = modelView.m21();
        final float m22 = modelView.m22();
        final float m30 = modelView.m30();
        final float m31 = modelView.m31();
        final float m32 = modelView.m32();
        final float[] advance = glyphAdvance;
        final float height = lineHeight * SCALE;
        for (int i = 0, p = 0; i < count; i++, p += 3) {
            final float x = positions[p];
            final float y = positions[p + 1];
            final float z = positions[p + 2];
            final float eyeZ = m02 * x + m12 * y + m22 * z + m32;
            if (-eyeZ < NEAR) continue;
            final float eyeX = m00 * x + m10 * y + m20 * z + m30;
            final float eyeY = m01 * x + m11 * y + m21 * z + m31;
            final int value = values[i];
            final float halfWidth = stringWidth(value, advance) * SCALE * 0.5F;
            if (!isVisible(eyeX, eyeY, eyeZ, halfWidth, height, projectionX, projectionY, viewportHeight)) continue;
            if (batcher == null) {
                drawFallback(value, colors[i], eyeX, eyeY, eyeZ);
            } else {
                final int digits = digitCount(value);
                for (int k = 0; k < digits; k++) {
                    glyph(digitAt(value, k), eyeX + digitLeft(value, k, advance) * SCALE, eyeY, eyeZ, colors[i]);
                }
            }
            if (++visible >= limit) return;
        }
    }

    private void glyph(int digit, float left, float anchorY, float z, int argb) {
        final int bucket = glyphBucket[digit];
        final int start = bucketBytes[bucket];
        final int end = start + QUAD_BYTES;
        final NativeScratch scratch = bucketScratch[bucket];
        scratch.ensure(end);
        bucketBytes[bucket] = end;
        final long p = scratch.address() + start;
        final float right = left + glyphWidth[digit] * SCALE;
        final float top = anchorY - glyphTop[digit] * SCALE;
        final float bottom = top - glyphHeight[digit] * SCALE;
        final float u0 = glyphU[digit];
        final float v0 = glyphV[digit];
        final float u1 = u0 + glyphUSize[digit];
        final float v1 = v0 + glyphVSize[digit];
        vertex(p, left, top, z, u0, v0, argb);
        vertex(p + STRIDE, left, bottom, z, u0, v1, argb);
        vertex(p + 2L * STRIDE, right, bottom, z, u1, v1, argb);
        vertex(p + 3L * STRIDE, right, top, z, u1, v0, argb);
    }

    private static void vertex(long p, float x, float y, float z, float u, float v, int argb) {
        DebugGeometry.putPosition(p, x, y, z);
        memPutFloat(p + TEXTURE_OFFSET, u);
        memPutFloat(p + TEXTURE_OFFSET + 4, v);
        DebugGeometry.putColor(p + COLOR_OFFSET, argb);
    }

    private void drawFallback(int value, int argb, float eyeX, float eyeY, float eyeZ) {
        final String text = NUMBERS[value];
        labelMatrix.translation(eyeX, eyeY, eyeZ).scale(SCALE, -SCALE, SCALE);
        GLStateManager.setModelViewMatrix(labelMatrix);
        font.drawString(text, -Math.round(stringWidth(value, glyphAdvance) * 0.5F), 0, argb);
    }

    private void pinFallback() {
        fallbackModelView.set(GLStateManager.getModelViewMatrix());
        final Color4 color = GLStateManager.getColor();
        fallbackColor[0] = color.getRed();
        fallbackColor[1] = color.getGreen();
        fallbackColor[2] = color.getBlue();
        fallbackColor[3] = color.getAlpha();
        GLStateManager.glUseProgram(0);
        GLStateManager.disableLighting();
        GLStateManager.disableFog();
        GLStateManager.disableAlphaTest();
        GLStateManager.glActiveTexture(OpenGlHelper.lightmapTexUnit);
        GLStateManager.disableTexture();
        GLStateManager.glActiveTexture(OpenGlHelper.defaultTexUnit);
        GLStateManager.enableTexture();
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    void end() {
        try {
            if (batcher != null) {
                boolean bound = false;
                for (int i = 0; i < bucketCount; i++) {
                    final int bytes = bucketBytes[i];
                    if (bytes == 0) continue;
                    if (!bound) {
                        final DebugProgram textured = DebugPrograms.textured();
                        textured.use();
                        textured.mvp(projection);
                        bound = true;
                    }
                    batcher.bindGlyphTexture(bucketTexture[i]);
                    TessellatorStreamingDrawer.drawPacked(bucketScratch[i].window(bytes), GL11.GL_QUADS, FLAGS, bytes / STRIDE);
                }
            }
        } finally {
            if (batcher == null && font != null) {
                GLStateManager.setModelViewMatrix(fallbackModelView);
                GLStateManager.glColor4f(fallbackColor[0], fallbackColor[1], fallbackColor[2], fallbackColor[3]);
            }
            for (int i = 0; i < bucketCount; i++) bucketBytes[i] = 0;
            batcher = null;
            font = null;
        }
    }

    void free() {
        for (int i = 0; i < DIGITS; i++) {
            bucketScratch[i].free();
            bucketBytes[i] = 0;
        }
        bucketCount = 0;
        metricsValid = false;
    }
}

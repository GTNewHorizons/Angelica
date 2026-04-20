package com.gtnewhorizons.angelica.client.font;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexBuffer;
import com.gtnewhorizon.gtnhlib.util.font.GlyphReplacements;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.streaming.StreamingUploader;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Setter;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;


import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import java.util.Comparator;
import java.util.Objects;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack.stackPush;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

/**
 * A batching replacement for {@code FontRenderer}
 *
 * @author eigenraven
 */
public class BatchingFontRenderer {

    /** The underlying FontRenderer object that's being accelerated */
    protected FontRenderer underlying;
    /** Array of width of all the characters in default.png */
    protected int[] charWidth;
    /**
     * Array of RGB triplets defining the 16 standard chat colors followed by 16 darker version of the same colors for
     * drop shadows.
     */
    private final int[] colorCode;
    /** Location of the primary font atlas to bind. */
    protected final ResourceLocation locationFontTexture;

    private final int AAMode;
    private final int AAStrength;
    private final int alphaTestRefLocation;
    private final int mvpMatrixLocation;
    private final int fontShaderId;

    final boolean isSGA;
    final boolean isSplash;

    /** For use with modded books. Affects calculations and forces some defaults. */
    @Setter
    boolean bookMode = false;

    private static class FontAAShader {

        private static Program fontShader = null;
        public static Program getProgram() {
            if (fontShader == null) {
                final String vsh = ShaderLoader.getShaderSource("angelica:fontFilter.vsh");
                final String fsh = ShaderLoader.getShaderSource("angelica:fontFilter.fsh");
                fontShader = ProgramBuilder.begin("fontFilter", vsh, null, fsh, ImmutableSet.of(0)).build();
            }
            return fontShader;
        }
    }

    public BatchingFontRenderer(FontRenderer underlying, int[] charWidth, int[] colorCode, ResourceLocation locationFontTexture) {
        this.underlying = underlying;
        this.charWidth = charWidth;
        this.colorCode = colorCode;
        this.locationFontTexture = locationFontTexture;

        for (int i = 0; i < 64; i++) {
            batchCommandPool.add(new FontDrawCmd());
        }

        this.isSGA = Objects.equals(this.locationFontTexture.getResourcePath(), "textures/font/ascii_sga.png");
        this.isSplash = FontStrategist.isSplashFontRendererActive(underlying);

        FontProviderMC.get(this.isSGA).charWidth = this.charWidth;
        FontProviderMC.get(this.isSGA).locationFontTexture = this.locationFontTexture;

        //noinspection deprecation
        fontShaderId = FontAAShader.getProgram().getProgramId();
        AAMode = GLStateManager.glGetUniformLocation(fontShaderId, "aaMode");
        AAStrength = GLStateManager.glGetUniformLocation(fontShaderId, "strength");
        alphaTestRefLocation = GLStateManager.glGetUniformLocation(fontShaderId, "alphaTestRef");
        mvpMatrixLocation = GLStateManager.glGetUniformLocation(fontShaderId, "u_MVPMatrix");
        if (ebo == null) {
            ebo = new IndexBuffer();
            vbo = GLStateManager.glGenBuffers();
            allocateBuffers();
        }
    }

    // === Batched rendering

    private static final int INITIAL_BATCH_SIZE = 2048;
    private static final ResourceLocation DUMMY_RESOURCE_LOCATION = new ResourceLocation("angelica$dummy",
        "this is invalid!");

    // Layout in data:
    // [v, v, t, t, c, c, c, c, tb, tb, tb, tb]
    // v, t and tb are floats, c is bytes; 36 bytes total
    private static final int VERTEX_SIZE = 36;
    private static int rawCapacity = INITIAL_BATCH_SIZE * VERTEX_SIZE;
    private static ByteBuffer vertexData = memAlloc(rawCapacity);
    private static long vertexDataAddress = memAddress0(vertexData);
    private static int vboCapacity;


    // OpenGL objects (static, can be used between multiple BatchingFontRenderer)
    private static int fontVAO = 0;
    private static int vbo;
    private static IndexBuffer ebo;

    private int batchDepth = 0;

    private int vertexDataPos = 0;
    private int idxWriterIndex = 0;

    private final ObjectArrayList<FontDrawCmd> batchCommands = ObjectArrayList.wrap(new FontDrawCmd[64], 0);
    private final ObjectArrayList<FontDrawCmd> batchCommandPool = ObjectArrayList.wrap(new FontDrawCmd[64], 0);

    private int blendSrcRGB = GL11.GL_SRC_ALPHA;
    private int blendDstRGB = GL11.GL_ONE_MINUS_SRC_ALPHA;


    private void allocateBuffers() {
        populateEBO(rawCapacity / VERTEX_SIZE);
    }

    private void populateEBO(int capacity) {
        final int quadCount = capacity * 6;
        final ByteBuffer data = memAlloc(quadCount * 6 * 2);
        long ptr = memAddress0(data);
        for (int i = 0; i < quadCount; i++) {
            int base = (i * 4);

            // triangle 1
            memPutShort(ptr, (short) base);
            memPutShort(ptr + 2, (short) (base + 1));
            memPutShort(ptr + 4, (short) (base + 2));

            // triangle 2
            memPutShort(ptr + 6, (short) (base + 2));
            memPutShort(ptr + 8, (short) (base + 1));
            memPutShort(ptr + 10, (short) (base + 3));
            ptr += 12;
        }

        ebo.upload(data);

        memFree(data);

    }

    private void ensureCapacity() {
        if (vertexDataPos + (4 * VERTEX_SIZE) > rawCapacity) {
            rawCapacity *= 2;
            vertexData = memRealloc(vertexData, rawCapacity);
            vertexDataAddress = memAddress0(vertexData);

            allocateBuffers();
        }
    }

    private void pushVtx(float x, float y, int rgba, float u, float v, float uMin, float uMax, float vMin, float vMax) {
        final long ptr = vertexDataAddress + vertexDataPos;

        // v, v
        memPutFloat(ptr, x);
        memPutFloat(ptr + 4, y);

        // t, t
        memPutFloat(ptr + 8, u);
        memPutFloat(ptr + 12, v);

        // c, c, c, c
        // 0xAARRGGBB
        memPutByte(ptr + 16, (byte) ((rgba >> 16) & 0xFF));
        memPutByte(ptr + 17, (byte) ((rgba >> 8) & 0xFF));
        memPutByte(ptr + 18, (byte) (rgba & 0xFF));
        memPutByte(ptr + 19, (byte) ((rgba >> 24) & 0xFF));

        // tb, tb, tb, tb
        memPutFloat(ptr + 20, uMin);
        memPutFloat(ptr + 24, uMax);
        memPutFloat(ptr + 28, vMin);
        memPutFloat(ptr + 32, vMax);

        vertexDataPos += VERTEX_SIZE;
    }

    private void pushUntexRect(float x, float y, float w, float h, int rgba) {
        ensureCapacity();
        pushVtx(x, y, rgba, 0, 0, 0, 0, 0, 0);
        pushVtx(x, y + h, rgba, 0, 0, 0, 0, 0, 0);
        pushVtx(x + w, y, rgba, 0, 0, 0, 0, 0, 0);
        pushVtx(x + w, y + h, rgba, 0, 0, 0, 0, 0, 0);
        pushQuadIdx();
    }

    private void pushTexRect(float x, float y, float w, float h, float itOff, int rgba, float uStart, float vStart, float uSz, float vSz, boolean flipV) {
        ensureCapacity();
        float vTop = flipV ? vStart + vSz : vStart;
        float vBot = flipV ? vStart : vStart + vSz;
        pushVtx(x + itOff, y, rgba, uStart, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
        pushVtx(x - itOff, y + h, rgba, uStart, vBot, uStart, uStart + uSz, vStart, vStart + vSz);
        pushVtx(x + itOff + w, y, rgba, uStart + uSz, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
        pushVtx(x - itOff + w, y + h, rgba, uStart + uSz, vBot, uStart, uStart + uSz, vStart, vStart + vSz);
        pushQuadIdx();
    }

    private void pushQuadIdx() {
        idxWriterIndex += 6;
    }

    private void pushDrawCmd(int startIdx, int idxCount, ResourceLocation texture, boolean isUnicode) {
        if (!batchCommands.isEmpty()) {
            final FontDrawCmd lastCmd = batchCommands.get(batchCommands.size() - 1);
            final int prevEndVtx = lastCmd.startVtx + lastCmd.idxCount;
            if (prevEndVtx == startIdx && lastCmd.texture == texture) {
                // Coalesce into one
                lastCmd.idxCount += idxCount;
                return;
            }
        }
        if (batchCommandPool.isEmpty()) {
            for (int i = 0; i < 64; i++) {
                batchCommandPool.add(new FontDrawCmd());
            }
        }
        final FontDrawCmd cmd = batchCommandPool.pop();
        cmd.reset(startIdx, idxCount, texture, isUnicode);
        batchCommands.add(cmd);
    }

    private static final class FontDrawCmd {

        public int startVtx;
        public int idxCount;
        public boolean isUnicode;
        public ResourceLocation texture;

        public void reset(int startVtx, int vtxCount, ResourceLocation texture, boolean isUnicode) {
            this.startVtx = startVtx;
            this.idxCount = vtxCount;
            this.texture = texture;
            this.isUnicode = isUnicode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (FontDrawCmd) obj;
            return this.startVtx == that.startVtx && this.idxCount == that.idxCount && Objects.equals(this.texture,
                that.texture);
        }

        @Override
        public int hashCode() {
            return Objects.hash(startVtx, idxCount, texture);
        }

        @Override
        public String toString() {
            return "FontDrawCmd["
                + "startVtx="
                + startVtx
                + ", "
                + "vtxCount="
                + idxCount
                + ", "
                + "texture="
                + texture
                + ']';
        }

        public static final Comparator<FontDrawCmd> DRAW_ORDER_COMPARATOR = Comparator.comparing((FontDrawCmd fdc) -> fdc.texture,
            Comparator.nullsLast(Comparator.comparing(ResourceLocation::getResourceDomain)
                .thenComparing(ResourceLocation::getResourcePath))).thenComparing(fdc -> fdc.startVtx);
    }

    /**
     * Starts a new batch of font rendering operations. Can be called from within another batch with a matching end, to
     * allow for easier optimizing of blocks of font rendering code.
     */
    public void beginBatch() {
        batchDepth++;
    }

    public void endBatch() {
        if (batchDepth <= 0) {
            batchDepth = 0;
            return;
        }
        batchDepth--;
        if (batchDepth == 0) {
            // We finished any nested batches
            flushBatch();
        }
    }

    private static final Matrix4f scratchMvp = new Matrix4f();
    private int fontAAModeLast = -1;
    private int fontAAStrengthLast = -1;

    private void flushBatch() {
        if (vertexDataPos == 0) {
            clearBatch();
            return;
        }

        // Upload first (to reduce stalls)
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        vertexData.limit(vertexDataPos);
        vboCapacity = StreamingUploader.upload(vertexData, vboCapacity);

        final int prevProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        // Sort&Draw
        batchCommands.sort(FontDrawCmd.DRAW_ORDER_COMPARATOR);

        final boolean isTextureEnabledBefore = GLStateManager.glIsEnabled(GL11.GL_TEXTURE_2D);
        final boolean isBlendEnabledBefore = GLStateManager.glIsEnabled(GL11.GL_BLEND);
        final int boundTextureBefore = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean textureChanged = false;

        ResourceLocation lastTexture = DUMMY_RESOURCE_LOCATION;
        GLStateManager.enableTexture();
        GLStateManager.enableAlphaTest();
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(blendSrcRGB, blendDstRGB, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glShadeModel(GL11.GL_FLAT);

        GLStateManager.glUseProgram(fontShaderId);
        if (FontConfig.fontAAMode != fontAAModeLast) {
            fontAAModeLast = FontConfig.fontAAMode;
            GLStateManager.glUniform1i(AAMode, FontConfig.fontAAMode);
        }
        if (FontConfig.fontAAStrength != fontAAStrengthLast) {
            fontAAStrengthLast = FontConfig.fontAAStrength;
            GLStateManager.glUniform1f(AAStrength, FontConfig.fontAAStrength / 120.f);
        }
        GLStateManager.glUniform1f(alphaTestRefLocation, GLStateManager.getAlphaState().getReference());
        try (MemoryStack stack = stackPush()) {
            final FloatBuffer mvpBuf = stack.mallocFloat(16);
            GLStateManager.getProjectionMatrix().mul(GLStateManager.getModelViewMatrix(), scratchMvp);
            scratchMvp.get(mvpBuf);
            GLStateManager.glUniformMatrix4(mvpMatrixLocation, false, mvpBuf);
        }

        if (fontVAO == 0) {
            fontVAO = GLStateManager.glGenVertexArrays();

            GLStateManager.glBindVertexArray(fontVAO);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            ebo.bind();

            // position
            GLStateManager.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, VERTEX_SIZE, 0);
            GLStateManager.glEnableVertexAttribArray(0);

            // texcoords
            GLStateManager.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, VERTEX_SIZE, 8);
            GLStateManager.glEnableVertexAttribArray(1);

            // color
            GLStateManager.glVertexAttribPointer(2, 4, GL11.GL_UNSIGNED_BYTE, true, VERTEX_SIZE, 16);
            GLStateManager.glEnableVertexAttribArray(2);

            // tex bounds
            GLStateManager.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, VERTEX_SIZE, 20);
            GLStateManager.glEnableVertexAttribArray(3);
        }

        GLStateManager.glBindVertexArray(fontVAO);

        // Use plain for loop to avoid allocations
        final FontDrawCmd[] cmdsData = batchCommands.elements();
        final int cmdsSize = batchCommands.size();
        for (int i = 0; i < cmdsSize; i++) {
            final FontDrawCmd cmd = cmdsData[i];
            if (!Objects.equals(lastTexture, cmd.texture)) {
                if (lastTexture == null) {
                    GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
                } else if (cmd.texture == null) {
                    GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
                }
                if (cmd.texture != null) {
                    ((FontRendererAccessor) underlying).angelica$bindTexture(cmd.texture);
                    textureChanged = true;
                }
                lastTexture = cmd.texture;
            }
            GLStateManager.glDrawElements(GL11.GL_TRIANGLES, cmd.idxCount, GL11.GL_UNSIGNED_SHORT, (long) cmd.startVtx * 2L);
        }


        GLStateManager.glUseProgram(prevProgram);

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);

        if (isTextureEnabledBefore) {
        	GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        }
        if (!isBlendEnabledBefore) {
            GLStateManager.disableBlend();
        }
        if (textureChanged) {
        	GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, boundTextureBefore);
        }

        clearBatch();
    }

    private void clearBatch() {
        // Clear for the next batch
        batchCommandPool.addAll(batchCommands);
        batchCommands.clear();
        vertexDataPos = 0;
        idxWriterIndex = 0;
    }

    // === Actual text mesh generation

    public static boolean charInRange(char what, char fromInclusive, char toInclusive) {
        return (what >= fromInclusive) && (what <= toInclusive);
    }

    /**
     * Read {@code count} §-hex pairs from str starting at {@code start}.
     * Each pair must be § followed by [0-9a-fA-F].
     * Returns assembled hex value, or -1 if any pair is malformed.
     */
    private static int parseHexPairs(CharSequence str, int start, int count) {
        int result = 0;
        for (int i = 0; i < count; i++) {
            int pairStart = start + i * 2;
            if (str.charAt(pairStart) != FORMATTING_CHAR) return -1;
            int digit = Character.digit(str.charAt(pairStart + 1), 16);
            if (digit == -1) return -1;
            result = (result << 4) | digit;
        }
        return result;
    }

    /** Parse a full §x§R§R§G§G§B§B sequence (14 chars). Returns RGB or -1. */
    private static int parseFullSectionX(CharSequence str, int start) {
        if (str.charAt(start) != FORMATTING_CHAR) return -1;
        if (Character.toLowerCase(str.charAt(start + 1)) != 'x') return -1;
        return parseHexPairs(str, start + 2, 6);
    }

    /**
     * Count visible characters from {@code start} to {@code end}, skipping format codes.
     * Stops at §r, any color code (§0-f, §x), or any color effect (§q, §g).
     */
    private static int countVisibleChars(CharSequence str, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            char ch = str.charAt(i);
            if (ch == FORMATTING_CHAR && i + 1 < end) {
                char code = Character.toLowerCase(str.charAt(i + 1));
                if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
                    || code == 'x' || code == 'q' || code == 'g') {
                    break;
                }
                i++; // skip style codes (k-o, w, j) — they don't terminate gradient
            } else {
                count++;
            }
        }
        return Math.max(count, 1); // avoid div-by-zero
    }

    private static int hsvToRgb(float hue, float sat, float val) {
        int h = (int)(hue / 60f) % 6;
        float f = hue / 60f - h;
        float p = val * (1 - sat);
        float q = val * (1 - f * sat);
        float t = val * (1 - (1 - f) * sat);
        float r, g, b;
        switch (h) {
            case 0: r=val; g=t; b=p; break;
            case 1: r=q; g=val; b=p; break;
            case 2: r=p; g=val; b=t; break;
            case 3: r=p; g=q; b=val; break;
            case 4: r=t; g=p; b=val; break;
            default: r=val; g=p; b=q; break;
        }
        return ((int)(r*255) << 16) | ((int)(g*255) << 8) | (int)(b*255);
    }

    public boolean forceDefaults() {
        return this.bookMode || this.isSGA || this.isSplash;
    }

    public float getGlyphScaleX() {
        return forceDefaults() ? 1 : (float) (FontConfig.glyphScale * Math.pow(2, FontConfig.glyphAspect));
    }

    public float getGlyphScaleY() {
        return forceDefaults() ? 1 : (float) (FontConfig.glyphScale / Math.pow(2, FontConfig.glyphAspect));
    }

    public float getGlyphSpacing() {
        return forceDefaults() ? 0 : FontConfig.glyphSpacing;
    }

    public float getWhitespaceScale() {
        return forceDefaults() ? 1 : FontConfig.whitespaceScale;
    }

    public float getShadowOffset() {
        return forceDefaults() ? 1 : FontConfig.fontShadowOffset;
    }

    private static final char FORMATTING_CHAR = 167; // §

    public float drawString(final float anchorX, final float anchorY, final int color, final boolean enableShadow,
        final boolean unicodeFlag, final CharSequence string, int stringOffset, int stringLength) {
        // noinspection SizeReplaceableByIsEmpty
        if (string == null || string.length() == 0) {
            return anchorX + (enableShadow ? 1.0f : 0.0f);
        }
        final int shadowColor = (color & 0xfcfcfc) >> 2 | color & 0xff000000;

        FontProviderMC.get(this.isSGA).charWidth = this.charWidth;
        FontProviderMC.get(this.isSGA).locationFontTexture = this.locationFontTexture;

        this.beginBatch();
        float curX = anchorX;
        try {
            final int totalStringLength = string.length();
            stringOffset = MathHelper.clamp_int(stringOffset, 0, totalStringLength);
            stringLength = MathHelper.clamp_int(stringLength, 0, totalStringLength - stringOffset);
            if (stringLength <= 0) {
                return 0;
            }
            final int stringEnd = stringOffset + stringLength;

            int curColor = color;
            int curShadowColor = shadowColor;
            boolean curItalic = false;
            boolean curRandom = false;
            boolean curBold = false;
            boolean curStrikethrough = false;
            boolean curUnderline = false;
            boolean curRainbow = false;
            boolean curWave = false;
            boolean curDinnerbone = false;
            boolean curGradient = false;
            int gradientStartRgb = 0, gradientEndRgb = 0;
            int gradientCharIndex = 0, gradientTotalChars = 0;
            int rainbowCharIndex = 0;

            float glyphScaleY = getGlyphScaleY();
            float glyphScaleX = getGlyphScaleX();
            float heightNorth = anchorY + (underlying.FONT_HEIGHT - 1.0f) * (0.5f - glyphScaleY / 2);

            final float underlineY = heightNorth + (underlying.FONT_HEIGHT - 1.0f) * glyphScaleY;
            float underlineStartX = 0.0f;
            float underlineEndX = 0.0f;

            final float strikethroughY = heightNorth + ((float) (underlying.FONT_HEIGHT / 2) - 1.0f) * glyphScaleY;
            float strikethroughStartX = 0.0f;
            float strikethroughEndX = 0.0f;

            for (int charIdx = stringOffset; charIdx < stringEnd; charIdx++) {
                char chr = string.charAt(charIdx);
                if (chr == FORMATTING_CHAR && (charIdx + 1) < stringEnd) {
                    final char fmtCode = Character.toLowerCase(string.charAt(charIdx + 1));
                    charIdx++;

                    if (curUnderline && underlineStartX != underlineEndX) {
                        final int ulIdx = idxWriterIndex;
                        pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, glyphScaleY, curColor);
                        pushDrawCmd(ulIdx, 6, null, false);
                        underlineStartX = underlineEndX;
                    }
                    if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                        final int ulIdx = idxWriterIndex;
                        pushUntexRect(
                            strikethroughStartX,
                            strikethroughY,
                            strikethroughEndX - strikethroughStartX,
                            glyphScaleY,
                            curColor);
                        pushDrawCmd(ulIdx, 6, null, false);
                        strikethroughStartX = strikethroughEndX;
                    }

                    if (fmtCode == 'x' && AngelicaConfig.enableRGBColors && charIdx + 12 < stringEnd) {
                        int rgb = parseHexPairs(string, charIdx + 1, 6);
                        if (rgb != -1) {
                            curRainbow = false;
                            curGradient = false;
                            curColor = (curColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                            curShadowColor = (curShadowColor & 0xFF000000) | ((rgb & 0xFCFCFC) >> 2);
                            charIdx += 12;
                        }
                    } else {
                    final boolean is09 = charInRange(fmtCode, '0', '9');
                    final boolean isAF = charInRange(fmtCode, 'a', 'f');
                    if (is09 || isAF) {
                        curRandom = false;
                        curBold = false;
                        curStrikethrough = false;
                        curUnderline = false;
                        curItalic = false;
                        curRainbow = false;
                        curGradient = false;
                        // wave/dinnerbone NOT reset — they're positional effects, independent of color

                        final int colorIdx = is09 ? (fmtCode - '0') : (fmtCode - 'a' + 10);
                        final int rgb = this.colorCode[colorIdx];
                        curColor = (curColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                        final int shadowRgb = this.colorCode[colorIdx + 16];
                        curShadowColor = (curShadowColor & 0xFF000000) | (shadowRgb & 0x00FFFFFF);
                    } else if (fmtCode == 'k') {
                        curRandom = true;
                    } else if (fmtCode == 'l') {
                        curBold = true;
                    } else if (fmtCode == 'm') {
                        curStrikethrough = true;
                        strikethroughStartX = curX - 1.0f;
                        strikethroughEndX = strikethroughStartX;
                    } else if (fmtCode == 'n') {
                        curUnderline = true;
                        underlineStartX = curX - 1.0f;
                        underlineEndX = underlineStartX;
                    } else if (fmtCode == 'o') {
                        curItalic = true;
                    } else if (fmtCode == 'q' && AngelicaConfig.enableTextEffects) {
                        curRainbow = true;
                        curGradient = false;
                        rainbowCharIndex = 0;
                    } else if (fmtCode == 'z' && AngelicaConfig.enableTextEffects) {
                        curWave = !curWave;
                    } else if (fmtCode == 'v' && AngelicaConfig.enableTextEffects) {
                        curDinnerbone = !curDinnerbone;
                    } else if (fmtCode == 'g' && AngelicaConfig.enableTextEffects && charIdx + 28 < stringEnd) {
                        int color1 = parseFullSectionX(string, charIdx + 1);
                        int color2 = parseFullSectionX(string, charIdx + 15);
                        if (color1 != -1 && color2 != -1) {
                            curGradient = true;
                            curRainbow = false;
                            gradientStartRgb = color1;
                            gradientEndRgb = color2;
                            gradientCharIndex = 0;
                            gradientTotalChars = countVisibleChars(string, charIdx + 29, stringEnd);
                            charIdx += 28;
                        }
                    } else if (fmtCode == 'r') {
                        curRandom = false;
                        curBold = false;
                        curStrikethrough = false;
                        curUnderline = false;
                        curItalic = false;
                        curRainbow = false;
                        curWave = false;
                        curDinnerbone = false;
                        curGradient = false;
                        curColor = color;
                        curShadowColor = shadowColor;
                    }
                    } // close else block for non-§x codes

                    continue;
                }

                if (FontConfig.enableCustomFont && FontConfig.enableGlyphReplacements) {
                    String chrReplacement = GlyphReplacements.customGlyphs.get(String.valueOf(chr));
                    if (chrReplacement != null) {
                        char replacement = chrReplacement.charAt(0);
                        boolean isReplacementCharAvailable =
                            FontProviderCustom.getPrimary().isGlyphAvailable(replacement)
                                || FontProviderCustom.getFallback().isGlyphAvailable(replacement);
                        if (isReplacementCharAvailable) {
                            chr = replacement;
                        }
                    }
                }

                if (curRandom) {
                    chr = FontProviderMC.get(this.isSGA).getRandomReplacement(chr);
                }

                FontProvider fontProvider = FontStrategist.getFontProvider(this, chr, FontConfig.enableCustomFont, unicodeFlag);

                heightNorth = anchorY + (underlying.FONT_HEIGHT - 1.0f) * (0.5f - glyphScaleY * fontProvider.getYScaleMultiplier() / 2);
                float heightSouth = (underlying.FONT_HEIGHT - 1.0f) * glyphScaleY * fontProvider.getYScaleMultiplier();

                // Check ASCII space, NBSP, NNBSP
                if (chr == ' ' || chr == '\u00A0' || chr == '\u202F') {
                    curX += 4 * this.getWhitespaceScale() + (curBold ? 1 : 0);
                    continue;
                }

                // Per-character color effects (rainbow / gradient)
                if (curRainbow) {
                    float hue = (rainbowCharIndex * 15f) % 360f;
                    int rgbEffect = hsvToRgb(hue, 1f, 1f);
                    curColor = (curColor & 0xFF000000) | rgbEffect;
                    curShadowColor = (curShadowColor & 0xFF000000) | ((rgbEffect & 0xFCFCFC) >> 2);
                    rainbowCharIndex++;
                }
                if (curGradient && gradientTotalChars > 0) {
                    float t = gradientTotalChars > 1 ? (float) gradientCharIndex / (float)(gradientTotalChars - 1) : 0f;
                    t = Math.min(t, 1f);
                    int gr = (int)((gradientStartRgb >> 16 & 0xFF) * (1-t) + (gradientEndRgb >> 16 & 0xFF) * t);
                    int gg = (int)((gradientStartRgb >> 8 & 0xFF) * (1-t) + (gradientEndRgb >> 8 & 0xFF) * t);
                    int gb = (int)((gradientStartRgb & 0xFF) * (1-t) + (gradientEndRgb & 0xFF) * t);
                    int rgbEffect = (gr << 16) | (gg << 8) | gb;
                    curColor = (curColor & 0xFF000000) | rgbEffect;
                    curShadowColor = (curShadowColor & 0xFF000000) | ((rgbEffect & 0xFCFCFC) >> 2);
                    gradientCharIndex++;
                }

                final float uStart = fontProvider.getUStart(chr);
                final float vStart = fontProvider.getVStart(chr);
                final float xAdvance = fontProvider.getXAdvance(chr) * glyphScaleX;
                final float glyphW = fontProvider.getGlyphW(chr) * glyphScaleX;
                final float uSz = fontProvider.getUSize(chr);
                final float vSz = fontProvider.getVSize(chr);
                final float itOff = curItalic ? 1.0F : 0.0F; // italic offset
                final float shadowOffset = fontProvider.getShadowOffset();
                final int shadowCopies = FontConfig.shadowCopies;
                final int boldCopies = FontConfig.boldCopies;
                final ResourceLocation texture = fontProvider.getTexture(chr);
                final int idxId = idxWriterIndex;

                // Wave: Y offset via sine wave
                float renderY = heightNorth;
                if (curWave) {
                    float time = HUDCaching.renderingCacheOverride ? 0f : System.nanoTime() * 0.000000005f;
                    renderY += (float) Math.sin(charIdx * 0.5 + time) * 2.0f;
                }

                if (enableShadow) {
                    for (int n = 1; n <= shadowCopies; n++) {
                        final float shadowOffsetPart = shadowOffset * ((float) n / shadowCopies);
                        pushTexRect(curX + shadowOffsetPart, renderY + shadowOffsetPart, glyphW - 1.0f, heightSouth, itOff, curShadowColor, uStart, vStart, uSz, vSz, curDinnerbone);

                        if (curBold) {
                            pushTexRect(curX + 2.0f * shadowOffsetPart, renderY + shadowOffsetPart, glyphW - 1.0f, heightSouth, itOff, curShadowColor, uStart, vStart, uSz, vSz, curDinnerbone);
                        }
                    }
                }

                pushTexRect(curX, renderY, glyphW - 1.0f, heightSouth, itOff, curColor, uStart, vStart, uSz, vSz, curDinnerbone);

                if (curBold) {
                    for (int n = 1; n <= boldCopies; n++) {
                        final float shadowOffsetPart = shadowOffset * ((float) n / boldCopies);
                        pushTexRect(curX + shadowOffsetPart, renderY, glyphW - 1.0f, heightSouth, itOff, curColor, uStart, vStart, uSz, vSz, curDinnerbone);
                    }
                }

                /*
                Vertex-per-char counts for different configurations
                    default:        4
                    shadow only:    4(1 + shadowCopies)
                    bold only:      4(1 + boldCopies)
                    both:           4(1 + 2 * shadowCopies + boldCopies)
                 */
                int charCount = 1;
                if (enableShadow) { charCount += shadowCopies * (curBold ? 2 : 1); }
                if (curBold) { charCount += boldCopies; }
                final int vtxCount = 4 * charCount;
                pushDrawCmd(idxId, vtxCount / 2 * 3, texture, chr > 255);

                curX += (xAdvance + (curBold ? 1.0f : 0.0f)) + getGlyphSpacing();
                if (bookMode) { curX = (int) curX; }
                underlineEndX = curX;
                strikethroughEndX = curX;
            }

            if (curUnderline && underlineStartX != underlineEndX) {
                final int ulIdx = idxWriterIndex;
                pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, glyphScaleY, curColor);
                pushDrawCmd(ulIdx, 6, null, false);
            }
            if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                final int ulIdx = idxWriterIndex;
                pushUntexRect(
                    strikethroughStartX,
                    strikethroughY,
                    strikethroughEndX - strikethroughStartX,
                    glyphScaleY,
                    curColor);
                pushDrawCmd(ulIdx, 6, null, false);
            }

        } finally {
            this.endBatch();
        }
        return curX + (enableShadow ? 1.0f : 0.0f);
    }

    public float getCharWidthFine(char chr) {
        if (chr == FORMATTING_CHAR) { return -1; }

        if (chr == ' ' || chr == '\u00A0' || chr == '\u202F') {
            return 4 * this.getWhitespaceScale();
        }

        FontProvider fp = FontStrategist.getFontProvider(this, chr, FontConfig.enableCustomFont, underlying.getUnicodeFlag());

        return fp.getXAdvance(chr) * this.getGlyphScaleX();
    }

    public void overrideBlendFunc(int srcRgb, int dstRgb) {
        blendSrcRGB = srcRgb;
        blendDstRGB = dstRgb;
    }

    public void resetBlendFunc() {
        blendSrcRGB = GL11.GL_SRC_ALPHA;
        blendDstRGB = GL11.GL_ONE_MINUS_SRC_ALPHA;
    }
}

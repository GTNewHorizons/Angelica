package com.gtnewhorizons.angelica.client.font;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack;
import com.gtnewhorizon.gtnhlib.util.font.GlyphReplacements;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack.stackPush;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memByteBuffer;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memRealloc;

/**
 * A batching replacement for {@code FontRenderer}
 *
 * @author eigenraven
 */
public class BatchingFontRenderer {

    /** The underlying FontRenderer object that's being accelerated */
    protected FontRenderer underlying;
    /** Array of width of all the characters in default.png */
    protected int[] charWidth = new int[256];
    /** Array of the start/end column (in upper/lower nibble) for every glyph in the /font directory. */
    protected byte[] glyphWidth;
    /**
     * Array of RGB triplets defining the 16 standard chat colors followed by 16 darker version of the same colors for
     * drop shadows.
     */
    private int[] colorCode;
    /** Location of the primary font atlas to bind. */
    protected final ResourceLocation locationFontTexture;

    private final int AAMode;
    private final int AAStrength;
    private final int mvpMatrixLocation;
    private final int fontShaderId;
    private static int fontVAO = 0;
    private static int vboPositions, vboColors, vboTexCoords, vboTexBounds, vboIndices;
    private static int capPositions, capColors, capTexCoords, capTexBounds, capIndices;

    final boolean isSGA;
    final boolean isSplash;

    private static class FontAAShader {

        private static Program fontShader = null;
        public static Program getProgram() {
            if (fontShader == null) {
                String vsh, fsh;
                try {
                    fsh = new String(IOUtils.toByteArray(Objects.requireNonNull(FontAAShader.class.getResourceAsStream("/assets/angelica/shaders/fontFilter.fsh"))), StandardCharsets.UTF_8);
                    vsh = new String(IOUtils.toByteArray(Objects.requireNonNull(FontAAShader.class.getResourceAsStream("/assets/angelica/shaders/fontFilter.vsh"))), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                ProgramBuilder builder = ProgramBuilder.begin("fontFilter", vsh, null, fsh, ImmutableSet.of(0));
                fontShader = builder.build();
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
        AAMode = GL20.glGetUniformLocation(fontShaderId, "aaMode");
        AAStrength = GL20.glGetUniformLocation(fontShaderId, "strength");
        mvpMatrixLocation = GL20.glGetUniformLocation(fontShaderId, "u_MVPMatrix");
    }

    // === Batched rendering

    private int batchDepth = 0;

    private int vtxWriterIndex = 0;
    private int idxWriterIndex = 0;
    private static final int INITIAL_BATCH_SIZE = 256;
    private static final ResourceLocation DUMMY_RESOURCE_LOCATION = new ResourceLocation("angelica$dummy",
        "this is invalid!");
    private FloatBuffer batchVtxPositions = memAllocFloat(INITIAL_BATCH_SIZE * 2);
    private ByteBuffer batchVtxColors = memAlloc(INITIAL_BATCH_SIZE * 4);
    private FloatBuffer batchVtxTexCoords = memAllocFloat(INITIAL_BATCH_SIZE * 2);
    private IntBuffer batchIndices = memAllocInt(INITIAL_BATCH_SIZE / 2 * 3);
    private FloatBuffer batchVtxTexBounds = memAllocFloat(INITIAL_BATCH_SIZE * 4);
    private final ObjectArrayList<FontDrawCmd> batchCommands = ObjectArrayList.wrap(new FontDrawCmd[64], 0);
    private final ObjectArrayList<FontDrawCmd> batchCommandPool = ObjectArrayList.wrap(new FontDrawCmd[64], 0);

    private int blendSrcRGB = GL11.GL_SRC_ALPHA;
    private int blendDstRGB = GL11.GL_ONE_MINUS_SRC_ALPHA;

    /**  */
    private void pushVtx(float x, float y, int rgba, float u, float v, float uMin, float uMax, float vMin, float vMax) {
        final int oldCap = batchVtxPositions.capacity() / 2;
        if (vtxWriterIndex >= oldCap) {
            final int newCap = oldCap * 2;
            batchVtxPositions = memRealloc(batchVtxPositions, newCap * 2);
            batchVtxColors = memRealloc(batchVtxColors, newCap * 4);
            batchVtxTexCoords = memRealloc(batchVtxTexCoords, newCap * 2);
            batchVtxTexBounds = memRealloc(batchVtxTexBounds, newCap * 4);
            final int oldIdxCap = batchIndices.capacity();
            final int newIdxCap = oldIdxCap * 2;
            batchIndices = memRealloc(batchIndices, newIdxCap);
        }
        final int idx = vtxWriterIndex;
        final int idx2 = idx * 2;
        final int idx4 = idx * 4;
        batchVtxPositions.put(idx2, x);
        batchVtxPositions.put(idx2 + 1, y);
        // 0xAARRGGBB
        batchVtxColors.put(idx4, (byte) ((rgba >> 16) & 0xFF));
        batchVtxColors.put(idx4 + 1, (byte) ((rgba >> 8) & 0xFF));
        batchVtxColors.put(idx4 + 2, (byte) (rgba & 0xFF));
        batchVtxColors.put(idx4 + 3, (byte) ((rgba >> 24) & 0xFF));
        batchVtxTexCoords.put(idx2, u);
        batchVtxTexCoords.put(idx2 + 1, v);
        batchVtxTexBounds.put(idx4, uMin);
        batchVtxTexBounds.put(idx4 + 1, uMax);
        batchVtxTexBounds.put(idx4 + 2, vMin);
        batchVtxTexBounds.put(idx4 + 3, vMax);
        vtxWriterIndex++;
    }

    private void pushUntexRect(float x, float y, float w, float h, int rgba) {
        final int vtxId = vtxWriterIndex;
        pushVtx(x, y, rgba, 0, 0, 0, 0, 0, 0);
        pushVtx(x, y + h, rgba, 0, 0, 0, 0, 0, 0);
        pushVtx(x + w, y, rgba, 0, 0, 0, 0, 0, 0);
        pushVtx(x + w, y + h, rgba, 0, 0, 0, 0, 0, 0);
        pushQuadIdx(vtxId);
    }

    private void pushTexRect(float x, float y, float w, float h, float itOff, int rgba, float uStart, float vStart, float uSz, float vSz) {
        final int vtxId = vtxWriterIndex;
        pushVtx(x + itOff, y, rgba, uStart, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
        pushVtx(x - itOff, y + h, rgba, uStart, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
        pushVtx(x + itOff + w, y, rgba, uStart + uSz, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
        pushVtx(x - itOff + w, y + h, rgba, uStart + uSz, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
        pushQuadIdx(vtxId);
    }

    private int pushQuadIdx(int startV) {
        final int idx = idxWriterIndex;
        batchIndices.put(idx, startV);
        batchIndices.put(idx + 1, startV + 1);
        batchIndices.put(idx + 2, startV + 2);
        //
        batchIndices.put(idx + 3, startV + 2);
        batchIndices.put(idx + 4, startV + 1);
        batchIndices.put(idx + 5, startV + 3);
        idxWriterIndex += 6;
        return idx;
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
        if (batchDepth == Integer.MAX_VALUE) {
            throw new StackOverflowError("More than Integer.MAX_VALUE nested font rendering batch operations");
        }
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

    private static int streamUpload(int target, int vbo, long address, int bytes, int cap) {
        GL15.glBindBuffer(target, vbo);
        final ByteBuffer data = memByteBuffer(address, bytes);
        if (bytes > cap) {
            GL15.glBufferData(target, data, GL15.GL_STREAM_DRAW);
            return bytes;
        }
        GL15.glBufferData(target, cap, GL15.GL_STREAM_DRAW);
        GL15.glBufferSubData(target, 0, data);
        return cap;
    }

    private static final Matrix4f scratchMvp = new Matrix4f();
    private int fontAAModeLast = -1;
    private int fontAAStrengthLast = -1;
    private void flushBatch() {
        final int prevProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        // Sort&Draw
        batchCommands.sort(FontDrawCmd.DRAW_ORDER_COMPARATOR);

        final boolean isTextureEnabledBefore = GLStateManager.glIsEnabled(GL11.GL_TEXTURE_2D);
        final int boundTextureBefore = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean textureChanged = false;

        ResourceLocation lastTexture = DUMMY_RESOURCE_LOCATION;
        GLStateManager.enableTexture();
        GLStateManager.enableAlphaTest();
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(blendSrcRGB, blendDstRGB, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glShadeModel(GL11.GL_FLAT);

        final boolean useFontShader = prevProgram == 0;
        if (useFontShader) {
            GLStateManager.glUseProgram(fontShaderId);
            if (FontConfig.fontAAMode != fontAAModeLast) {
                fontAAModeLast = FontConfig.fontAAMode;
                GL20.glUniform1i(AAMode, FontConfig.fontAAMode);
            }
            if (FontConfig.fontAAStrength != fontAAStrengthLast) {
                fontAAStrengthLast = FontConfig.fontAAStrength;
                GL20.glUniform1f(AAStrength, FontConfig.fontAAStrength / 120.f);
            }
            try (MemoryStack stack = stackPush()) {
                final FloatBuffer mvpBuf = stack.mallocFloat(16);
                GLStateManager.getProjectionMatrix().mul(GLStateManager.getModelViewMatrix(), scratchMvp);
                scratchMvp.get(mvpBuf);
                GL20.glUniformMatrix4(mvpMatrixLocation, false, mvpBuf);
            }
        }

        if (fontVAO == 0) {
            fontVAO = org.lwjgl.opengl.GL30.glGenVertexArrays();
            vboPositions = GL15.glGenBuffers();
            vboColors = GL15.glGenBuffers();
            vboTexCoords = GL15.glGenBuffers();
            vboTexBounds = GL15.glGenBuffers();
            vboIndices = GL15.glGenBuffers();
        }
        GLStateManager.glBindVertexArray(fontVAO);

        final int vtxCount = vtxWriterIndex;

        // Location 0: position (vec2, float)
        batchVtxPositions.position(0).limit(vtxCount * 2);
        capPositions = streamUpload(GL15.GL_ARRAY_BUFFER, vboPositions, memAddress(batchVtxPositions), batchVtxPositions.remaining() * 4, capPositions);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(0);

        // Location 1: color (vec4, unsigned byte normalized)
        batchVtxColors.position(0).limit(vtxCount * 4);
        capColors = streamUpload(GL15.GL_ARRAY_BUFFER, vboColors, memAddress(batchVtxColors), batchVtxColors.remaining(), capColors);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 0, 0L);
        GL20.glEnableVertexAttribArray(1);

        // Location 2: texcoord0 (vec2, float)
        batchVtxTexCoords.position(0).limit(vtxCount * 2);
        capTexCoords = streamUpload(GL15.GL_ARRAY_BUFFER, vboTexCoords, memAddress(batchVtxTexCoords), batchVtxTexCoords.remaining() * 4, capTexCoords);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(2);

        // Location 3: texbounds (vec4, float)
        batchVtxTexBounds.position(0).limit(vtxCount * 4);
        capTexBounds = streamUpload(GL15.GL_ARRAY_BUFFER, vboTexBounds, memAddress(batchVtxTexBounds), batchVtxTexBounds.remaining() * 4, capTexBounds);
        GL20.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(3);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // Stream upload index buffer
        batchIndices.position(0).limit(idxWriterIndex);
        capIndices = streamUpload(GL15.GL_ELEMENT_ARRAY_BUFFER, vboIndices, memAddress(batchIndices), batchIndices.remaining() * 4, capIndices);

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

            GL11.glDrawElements(GL11.GL_TRIANGLES, cmd.idxCount, GL11.GL_UNSIGNED_INT, (long) cmd.startVtx * 4L);
        }
        if (useFontShader) {
            GLStateManager.glUseProgram(prevProgram);
        }

        GLStateManager.glBindVertexArray(0);

        if (isTextureEnabledBefore) {
        	GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        }
        if (textureChanged) {
        	GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, boundTextureBefore);
        }

        // Clear for the next batch
        batchCommandPool.addAll(batchCommands);
        batchCommands.clear();
        vtxWriterIndex = 0;
        idxWriterIndex = 0;
        batchVtxPositions.limit(batchVtxPositions.capacity());
        batchVtxColors.limit(batchVtxColors.capacity());
        batchVtxTexCoords.limit(batchVtxTexCoords.capacity());
        batchVtxTexBounds.limit(batchVtxTexBounds.capacity());
        batchIndices.limit(batchIndices.capacity());
    }

    // === Actual text mesh generation

    public static boolean charInRange(char what, char fromInclusive, char toInclusive) {
        return (what >= fromInclusive) && (what <= toInclusive);
    }

    public boolean forceDefaults() {
        return this.isSGA || this.isSplash;
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

            float glyphScaleY = getGlyphScaleY();
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

                    final boolean is09 = charInRange(fmtCode, '0', '9');
                    final boolean isAF = charInRange(fmtCode, 'a', 'f');
                    if (is09 || isAF) {
                        curRandom = false;
                        curBold = false;
                        curStrikethrough = false;
                        curUnderline = false;
                        curItalic = false;

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
                    } else if (fmtCode == 'r') {
                        curRandom = false;
                        curBold = false;
                        curStrikethrough = false;
                        curUnderline = false;
                        curItalic = false;
                        curColor = color;
                        curShadowColor = shadowColor;
                    }

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
                    curX += 4 * this.getWhitespaceScale();
                    continue;
                }

                final float uStart = fontProvider.getUStart(chr);
                final float vStart = fontProvider.getVStart(chr);
                final float xAdvance = fontProvider.getXAdvance(chr) * getGlyphScaleX();
                final float glyphW = fontProvider.getGlyphW(chr) * getGlyphScaleX();
                final float uSz = fontProvider.getUSize(chr);
                final float vSz = fontProvider.getVSize(chr);
                final float itOff = curItalic ? 1.0F : 0.0F; // italic offset
                final float shadowOffset = fontProvider.getShadowOffset();
                final int shadowCopies = FontConfig.shadowCopies;
                final int boldCopies = FontConfig.boldCopies;
                final ResourceLocation texture = fontProvider.getTexture(chr);
                final int idxId = idxWriterIndex;

                if (enableShadow) {
                    for (int n = 1; n <= shadowCopies; n++) {
                        final float shadowOffsetPart = shadowOffset * ((float) n / shadowCopies);
                        pushTexRect(curX + shadowOffsetPart, heightNorth + shadowOffsetPart, glyphW - 1.0f, heightSouth, itOff, curShadowColor, uStart, vStart, uSz, vSz);

                        if (curBold) {
                            pushTexRect(curX + 2.0f * shadowOffsetPart, heightNorth + shadowOffsetPart, glyphW - 1.0f, heightSouth, itOff, curShadowColor, uStart, vStart, uSz, vSz);
                        }
                    }
                }

                pushTexRect(curX, heightNorth, glyphW - 1.0f, heightSouth, itOff, curColor, uStart, vStart, uSz, vSz);

                if (curBold) {
                    for (int n = 1; n <= boldCopies; n++) {
                        final float shadowOffsetPart = shadowOffset * ((float) n / boldCopies);
                        pushTexRect(curX + shadowOffsetPart, heightNorth, glyphW - 1.0f, heightSouth, itOff, curColor, uStart, vStart, uSz, vSz);
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

                curX += (xAdvance + (curBold ? shadowOffset : 0.0f)) + getGlyphSpacing();
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

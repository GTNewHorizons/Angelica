package com.gtnewhorizons.angelica.client.font;

import com.google.common.collect.ImmutableSet;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

/**
 * A batching replacement for {@code FontRenderer}
 *
 * @author eigenraven
 */
public class BatchingFontRenderer {

    /** The underlying FontRenderer object that's being accelerated */
    protected FontRenderer underlying;
    /** Cached locations for each unicode page atlas */
    private final ResourceLocation[] unicodePageLocations;
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
    /** The font provider instance currently in use. */
    private FontProvider fontProvider;

    private int fontShaderId;
    private int AAMode;
    private int AAStrength;
    private int texBoundAttrLocation;

    private final boolean isSGA;

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

    public BatchingFontRenderer(FontRenderer underlying, ResourceLocation[] unicodePageLocations, int[] charWidth,
        byte[] glyphWidth, int[] colorCode, ResourceLocation locationFontTexture) {
        this.underlying = underlying;
        this.unicodePageLocations = unicodePageLocations;
        this.charWidth = charWidth;
        this.glyphWidth = glyphWidth;
        this.colorCode = colorCode;
        this.locationFontTexture = locationFontTexture;

        for (int i = 0; i < 64; i++) {
            batchCommandPool.add(new FontDrawCmd());
        }

        this.isSGA = Objects.equals(this.locationFontTexture.getResourcePath(), "textures/font/ascii_sga.png");

        FontProviderMC.get(this.isSGA).charWidth = this.charWidth;
        FontProviderMC.get(this.isSGA).locationFontTexture = this.locationFontTexture;
        FontProviderUnicode.get().glyphWidth = this.glyphWidth;

        //noinspection deprecation
        fontShaderId = FontAAShader.getProgram().getProgramId();
        AAMode = GL20.glGetUniformLocation(fontShaderId, "aaMode");
        AAStrength = GL20.glGetUniformLocation(fontShaderId, "strength");
        texBoundAttrLocation = GL20.glGetAttribLocation(fontShaderId, "texBounds");
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

    private void flushBatch() {
        // Sort&Draw
        batchCommands.sort(FontDrawCmd.DRAW_ORDER_COMPARATOR);

        final boolean isTextureEnabledBefore = GLStateManager.glIsEnabled(GL11.GL_TEXTURE_2D);
        final int boundTextureBefore = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean textureChanged = false;

        ResourceLocation lastTexture = DUMMY_RESOURCE_LOCATION;
        GLStateManager.enableTexture();
        GLStateManager.enableAlphaTest();
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glShadeModel(GL11.GL_FLAT);

        GL20.glVertexAttribPointer(texBoundAttrLocation, 4, false, 0, batchVtxTexBounds);
        GL20.glEnableVertexAttribArray(texBoundAttrLocation);
        GL11.glTexCoordPointer(2, 0, batchVtxTexCoords);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 0, batchVtxColors);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glVertexPointer(2, 0, batchVtxPositions);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

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
            batchIndices.limit(cmd.startVtx + cmd.idxCount);
            batchIndices.position(cmd.startVtx);

            if (FontConfig.fontAAMode != 0) {
                FontAAShader.getProgram().use();
                GL20.glUniform1i(AAMode, FontConfig.fontAAMode);
                GL20.glUniform1f(AAStrength, FontConfig.fontAAStrength / 120.f);
            }
            GL11.glDrawElements(GL11.GL_TRIANGLES, batchIndices);
            if (FontConfig.fontAAMode != 0) {
                Program.unbind();
            }
        }

        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL20.glDisableVertexAttribArray(texBoundAttrLocation);

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
        batchIndices.limit(batchIndices.capacity());
        batchIndices.position(0);
    }

    // === Actual text mesh generation

    public static boolean charInRange(char what, char fromInclusive, char toInclusive) {
        return (what >= fromInclusive) && (what <= toInclusive);
    }

    public float getGlyphScaleX() {
        return this.isSGA ? 1 : (float) (FontConfig.glyphScale * Math.pow(2, FontConfig.glyphAspect));
    }

    public float getGlyphScaleY() {
        return this.isSGA ? 1 : (float) (FontConfig.glyphScale / Math.pow(2, FontConfig.glyphAspect));
    }

    public float getGlyphSpacing() {
        return (this.isSGA ? 1 : FontConfig.glyphSpacing);
    }

    public float getWhitespaceScale() {
        return (this.isSGA ? 1 : FontConfig.whitespaceScale);
    }

    public float getShadowOffset() {
        return (this.isSGA ? 1 : FontConfig.fontShadowOffset);
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

            final float underlineY = anchorY + underlying.FONT_HEIGHT - 1.0f;
            float underlineStartX = 0.0f;
            float underlineEndX = 0.0f;
            final float strikethroughY = anchorY + (float) (underlying.FONT_HEIGHT / 2) - 1.0F;
            float strikethroughStartX = 0.0f;
            float strikethroughEndX = 0.0f;

            for (int charIdx = stringOffset; charIdx < stringEnd; charIdx++) {
                char chr = string.charAt(charIdx);
                if (chr == FORMATTING_CHAR && (charIdx + 1) < stringEnd) {
                    final char fmtCode = Character.toLowerCase(string.charAt(charIdx + 1));
                    charIdx++;

                    if (curUnderline && underlineStartX != underlineEndX) {
                        final int ulIdx = idxWriterIndex;
                        pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, 1.0f, curColor);
                        pushDrawCmd(ulIdx, 6, null, false);
                        underlineStartX = underlineEndX;
                    }
                    if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                        final int ulIdx = idxWriterIndex;
                        pushUntexRect(
                            strikethroughStartX,
                            strikethroughY,
                            strikethroughEndX - strikethroughStartX,
                            1.0f,
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

                if (curRandom) {
                    chr = FontProviderMC.get(this.isSGA).getRandomReplacement(chr);
                }

                fontProvider = FontStrategist.getFontProvider(chr, this.isSGA, FontConfig.enableCustomFont, unicodeFlag);

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
                final ResourceLocation texture = fontProvider.getTexture(chr);

                final int vtxId = vtxWriterIndex;
                final int idxId = idxWriterIndex;

                int vtxCount = 0;

                float heightNorth = anchorY + 7.99F * (0.5f - getGlyphScaleY() / 2);
                float heightSouth = 7.99F * getGlyphScaleY();

                if (enableShadow) {
                    pushVtx(curX + itOff + shadowOffset, heightNorth + shadowOffset, curShadowColor, uStart, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(curX - itOff + shadowOffset, heightNorth + heightSouth + shadowOffset, curShadowColor, uStart, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(curX + glyphW - 1.0F + itOff + shadowOffset, heightNorth + shadowOffset, curShadowColor, uStart + uSz, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(curX + glyphW - 1.0F - itOff + shadowOffset, heightNorth + heightSouth + shadowOffset, curShadowColor, uStart + uSz, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushQuadIdx(vtxId + vtxCount);
                    vtxCount += 4;

                    if (curBold) {
                        final float shadowOffset2 = 2.0f * shadowOffset;
                        pushVtx(curX + itOff + shadowOffset2, heightNorth + shadowOffset, curShadowColor, uStart, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
                        pushVtx(curX - itOff + shadowOffset2, heightNorth + heightSouth + shadowOffset, curShadowColor, uStart, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
                        pushVtx(curX + glyphW - 1.0F + itOff + shadowOffset2, heightNorth + shadowOffset, curShadowColor, uStart + uSz, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
                        pushVtx(curX + glyphW - 1.0F - itOff + shadowOffset2, heightNorth + heightSouth + shadowOffset, curShadowColor, uStart + uSz, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
                        pushQuadIdx(vtxId + vtxCount);
                        vtxCount += 4;
                    }
                }

                pushVtx(curX + itOff, heightNorth, curColor, uStart, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
                pushVtx(curX - itOff, heightNorth + heightSouth, curColor, uStart, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
                pushVtx(curX + glyphW - 1.0F + itOff, heightNorth, curColor, uStart + uSz, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
                pushVtx(curX + glyphW - 1.0F - itOff, heightNorth + heightSouth, curColor, uStart + uSz, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
                pushQuadIdx(vtxId + vtxCount);
                vtxCount += 4;

                if (curBold) {
                    pushVtx(shadowOffset + curX + itOff, heightNorth, curColor, uStart, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(shadowOffset + curX - itOff, heightNorth + heightSouth, curColor, uStart, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(shadowOffset + curX + glyphW - 1.0F + itOff, heightNorth, curColor, uStart + uSz, vStart, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(shadowOffset + curX + glyphW - 1.0F - itOff, heightNorth + heightSouth, curColor, uStart + uSz, vStart + vSz, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushQuadIdx(vtxId + vtxCount);
                    vtxCount += 4;
                }

                pushDrawCmd(idxId, vtxCount / 2 * 3, texture, chr > 255);
                curX += (xAdvance + (curBold ? shadowOffset : 0.0f)) + getGlyphSpacing();
                underlineEndX = curX;
                strikethroughEndX = curX;
            }

            if (curUnderline && underlineStartX != underlineEndX) {
                final int ulIdx = idxWriterIndex;
                pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, 1.0f, curColor);
                pushDrawCmd(ulIdx, 6, null, false);
            }
            if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                final int ulIdx = idxWriterIndex;
                pushUntexRect(
                    strikethroughStartX,
                    strikethroughY,
                    strikethroughEndX - strikethroughStartX,
                    1.0f,
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

        FontProvider fp = FontStrategist.getFontProvider(chr, isSGA, FontConfig.enableCustomFont, underlying.getUnicodeFlag());

        return fp.getXAdvance(chr) * this.getGlyphScaleX();
    }
}

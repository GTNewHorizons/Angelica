package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import it.unimi.dsi.fastutil.chars.Char2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import jss.util.RandomXoshiro256StarStar;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;

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
    /** The RenderEngine used to load and setup glyph textures. */
    private final TextureManager renderEngine;
    private final RandomXoshiro256StarStar fontRandom = new RandomXoshiro256StarStar();

    /** The full list of characters present in the default Minecraft font, excluding the Unicode font */
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final String MCFONT_CHARS = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000";

    private static final short[] MCFONT_ASCII_LUT = new short[512];
    private static final Char2ShortOpenHashMap MCFONT_UNI_LUT = new Char2ShortOpenHashMap();

    static {
        Arrays.fill(MCFONT_ASCII_LUT, (short) -1);
        for (short i = 0; i < MCFONT_CHARS.length(); i++) {
            char ch = MCFONT_CHARS.charAt(i);
            if (ch < MCFONT_ASCII_LUT.length) {
                MCFONT_ASCII_LUT[ch] = i;
            } else {
                MCFONT_UNI_LUT.put(ch, i);
            }
        }
    }

    public static int lookupMcFontPosition(char ch) {
        if (ch < MCFONT_ASCII_LUT.length) {
            return MCFONT_ASCII_LUT[ch];
        } else {
            return MCFONT_UNI_LUT.getOrDefault(ch, (short) -1);
        }
    }

    public BatchingFontRenderer(FontRenderer underlying, ResourceLocation[] unicodePageLocations, int[] charWidth,
        byte[] glyphWidth, int[] colorCode, ResourceLocation locationFontTexture, TextureManager renderEngine) {
        this.underlying = underlying;
        this.unicodePageLocations = unicodePageLocations;
        this.charWidth = charWidth;
        this.glyphWidth = glyphWidth;
        this.colorCode = colorCode;
        this.locationFontTexture = locationFontTexture;
        this.renderEngine = renderEngine;

        for (int i = 0; i < 64; i++) {
            batchCommandPool.add(new FontDrawCmd());
        }
    }

    // === Batched rendering

    private int batchDepth = 0;

    private int vtxWriterIndex = 0;
    private int idxWriterIndex = 0;
    private static final int INITIAL_BATCH_SIZE = 256;
    private static final ResourceLocation DUMMY_RESOURCE_LOCATION = new ResourceLocation("angelica$dummy",
        "this is invalid!");
    private FloatBuffer batchVtxPositions = BufferUtils.createFloatBuffer(INITIAL_BATCH_SIZE * 2);
    private ByteBuffer batchVtxColors = BufferUtils.createByteBuffer(INITIAL_BATCH_SIZE * 4);
    private FloatBuffer batchVtxTexCoords = BufferUtils.createFloatBuffer(INITIAL_BATCH_SIZE * 2);
    private IntBuffer batchIndices = BufferUtils.createIntBuffer(INITIAL_BATCH_SIZE / 2 * 3);
    private final ObjectArrayList<FontDrawCmd> batchCommands = ObjectArrayList.wrap(new FontDrawCmd[64], 0);
    private final ObjectArrayList<FontDrawCmd> batchCommandPool = ObjectArrayList.wrap(new FontDrawCmd[64], 0);

    /**  */
    private void pushVtx(float x, float y, int rgba, float u, float v) {
        final int oldCap = batchVtxPositions.capacity() / 2;
        if (vtxWriterIndex >= oldCap) {
            final int newCap = oldCap * 2;
            batchVtxPositions = CompatMemoryUtil.memReallocDirect(batchVtxPositions, newCap * 2);
            batchVtxColors = CompatMemoryUtil.memReallocDirect(batchVtxColors, newCap * 4);
            batchVtxTexCoords = CompatMemoryUtil.memReallocDirect(batchVtxTexCoords, newCap * 2);
            final int oldIdxCap = batchIndices.capacity();
            final int newIdxCap = oldIdxCap * 2;
            batchIndices = CompatMemoryUtil.memReallocDirect(batchIndices, newIdxCap);
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
        vtxWriterIndex++;
    }

    private void pushUntexRect(float x, float y, float w, float h, int rgba) {
        final int vtxId = vtxWriterIndex;
        pushVtx(x, y, rgba, 0, 0);
        pushVtx(x, y + h, rgba, 0, 0);
        pushVtx(x + w, y, rgba, 0, 0);
        pushVtx(x + w, y + h, rgba, 0, 0);
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

    private void pushDrawCmd(int startIdx, int idxCount, ResourceLocation texture) {
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
        cmd.reset(startIdx, idxCount, texture);
        batchCommands.add(cmd);
    }

    private static final class FontDrawCmd {

        public int startVtx;
        public int idxCount;
        public ResourceLocation texture;

        public void reset(int startVtx, int vtxCount, ResourceLocation texture) {
            this.startVtx = startVtx;
            this.idxCount = vtxCount;
            this.texture = texture;
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
        GLStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        GLStateManager.glShadeModel(GL_FLAT);

        glTexCoordPointer(2, 0, batchVtxTexCoords);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glColorPointer(4, GL_UNSIGNED_BYTE, 0, batchVtxColors);
        glEnableClientState(GL_COLOR_ARRAY);
        glVertexPointer(2, 0, batchVtxPositions);
        glEnableClientState(GL_VERTEX_ARRAY);
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
            glDrawElements(GL_TRIANGLES, batchIndices);
        }

        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);

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

    private ResourceLocation getUnicodePageLocation(int page) {
        final ResourceLocation lookup = unicodePageLocations[page];
        if (lookup == null) {
            final ResourceLocation rl = new ResourceLocation(String.format(
                "textures/font/unicode_page_%02x.png",
                page));
            unicodePageLocations[page] = rl;
            return rl;
        } else {
            return lookup;
        }
    }

    private static final char FORMATTING_CHAR = 167; // §

    public float drawString(final float anchorX, final float anchorY, final int color, final boolean enableShadow,
        final boolean unicodeFlag, final CharSequence string, int stringOffset, int stringLength) {
        // noinspection SizeReplaceableByIsEmpty
        if (string == null || string.length() == 0) {
            return anchorX + (enableShadow ? 1.0f : 0.0f);
        }
        final int shadowColor = (color & 0xfcfcfc) >> 2 | color & 0xff000000;

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
            final float strikethroughY = anchorY + (float) (underlying.FONT_HEIGHT / 2);
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
                        pushDrawCmd(ulIdx, 6, null);
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
                        pushDrawCmd(ulIdx, 6, null);
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

                int lutIndex = lookupMcFontPosition(chr);
                if (curRandom && lutIndex != -1) {
                    int randomReplacementIndex;
                    do {
                        randomReplacementIndex = fontRandom.nextInt(this.charWidth.length);
                    } while (this.charWidth[lutIndex] != this.charWidth[randomReplacementIndex]);

                    lutIndex = randomReplacementIndex;
                    chr = MCFONT_CHARS.charAt(lutIndex);
                }

                final float shadowOffset = unicodeFlag ? 0.5F : 1.0F;

                // Check ASCII space, NBSP, NNBSP
                if (chr == ' ' || chr == '\u00A0' || chr == '\u202F') {
                    curX += 4;
                    continue;
                }

                final float uStart;
                final float vStart;
                final float xAdvance;
                final float glyphW;
                final float uSz;
                final float vSz;
                final float itOff = curItalic ? 1.0F : 0.0F; // italic offset
                final ResourceLocation texture;

                if (lutIndex == -1 || unicodeFlag) {
                    if (glyphWidth[chr] == 0) {
                        continue;
                    }
                    // Draw unicode char
                    final int uniPage = chr / 256;
                    texture = getUnicodePageLocation(uniPage);
                    final int startColumn = this.glyphWidth[chr] >>> 4;
                    final int endColumn = this.glyphWidth[chr] & 15;
                    final float startColumnF = (float) startColumn;
                    final float endColumnF = (float) (endColumn + 1);
                    uStart = ((float) (chr % 16 * 16) + startColumnF) / 256.0f;
                    vStart = ((float) ((chr & 255) / 16 * 16)) / 256.0f;
                    final float chrWidth = endColumnF - startColumnF - 0.02F;
                    glyphW = chrWidth / 2.0f + 1.0f;
                    xAdvance = (endColumnF - startColumnF) / 2.0F + 1.0F;
                    uSz = chrWidth / 256.0f;
                    vSz = 15.98f / 256.0f;

                } else {
                    // Draw "ASCII" char
                    uStart = ((lutIndex % 16) * 8) / 128.0F;
                    vStart = (float) ((lutIndex / 16) * 8) / 128.0F;
                    xAdvance = this.charWidth[lutIndex];
                    if (xAdvance == 0) {
                        continue;
                    }
                    glyphW = xAdvance - 0.01F;
                    uSz = (glyphW - 1.0F) / 128.0F;
                    vSz = 7.99F / 128.0F;
                    texture = locationFontTexture;
                }

                final int vtxId = vtxWriterIndex;
                final int idxId = idxWriterIndex;

                int vtxCount = 0;

                if (enableShadow) {
                    pushVtx(curX + itOff + shadowOffset, anchorY + shadowOffset, curShadowColor, uStart, vStart);
                    pushVtx(
                        curX - itOff + shadowOffset,
                        anchorY + 7.99F + shadowOffset,
                        curShadowColor,
                        uStart,
                        vStart + vSz);
                    pushVtx(curX + glyphW - 1.0F + itOff + shadowOffset,
                        anchorY + shadowOffset,
                        curShadowColor,
                        uStart + uSz,
                        vStart);
                    pushVtx(curX + glyphW - 1.0F - itOff + shadowOffset,
                        anchorY + 7.99F + shadowOffset,
                        curShadowColor,
                        uStart + uSz,
                        vStart + vSz);
                    pushQuadIdx(vtxId + vtxCount);
                    vtxCount += 4;

                    if (curBold) {
                        final float shadowOffset2 = 2.0f * shadowOffset;
                        pushVtx(curX + itOff + shadowOffset2, anchorY + shadowOffset, curShadowColor, uStart, vStart);
                        pushVtx(
                            curX - itOff + shadowOffset2,
                            anchorY + 7.99F + shadowOffset,
                            curShadowColor,
                            uStart,
                            vStart + vSz);
                        pushVtx(
                            curX + glyphW - 1.0F + itOff + shadowOffset2,
                            anchorY + shadowOffset,
                            curShadowColor,
                            uStart + uSz,
                            vStart);
                        pushVtx(curX + glyphW - 1.0F - itOff + shadowOffset2,
                            anchorY + 7.99F + shadowOffset,
                            curShadowColor,
                            uStart + uSz,
                            vStart + vSz);
                        pushQuadIdx(vtxId + vtxCount);
                        vtxCount += 4;
                    }
                }

                pushVtx(curX + itOff, anchorY, curColor, uStart, vStart);
                pushVtx(curX - itOff, anchorY + 7.99F, curColor, uStart, vStart + vSz);
                pushVtx(curX + glyphW - 1.0F + itOff, anchorY, curColor, uStart + uSz, vStart);
                pushVtx(curX + glyphW - 1.0F - itOff, anchorY + 7.99F, curColor, uStart + uSz, vStart + vSz);
                pushQuadIdx(vtxId + vtxCount);
                vtxCount += 4;

                if (curBold) {
                    pushVtx(shadowOffset + curX + itOff, anchorY, curColor, uStart, vStart);
                    pushVtx(shadowOffset + curX - itOff, anchorY + 7.99F, curColor, uStart, vStart + vSz);
                    pushVtx(shadowOffset + curX + glyphW - 1.0F + itOff, anchorY, curColor, uStart + uSz, vStart);
                    pushVtx(shadowOffset + curX + glyphW - 1.0F - itOff,
                        anchorY + 7.99F,
                        curColor,
                        uStart + uSz,
                        vStart + vSz);
                    pushQuadIdx(vtxId + vtxCount);
                    vtxCount += 4;
                }

                pushDrawCmd(idxId, vtxCount / 2 * 3, texture);
                curX += xAdvance + (curBold ? shadowOffset : 0.0f);
                underlineEndX = curX;
                strikethroughEndX = curX;
            }

            if (curUnderline && underlineStartX != underlineEndX) {
                final int ulIdx = idxWriterIndex;
                pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, 1.0f, curColor);
                pushDrawCmd(ulIdx, 6, null);
            }
            if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                final int ulIdx = idxWriterIndex;
                pushUntexRect(
                    strikethroughStartX,
                    strikethroughY,
                    strikethroughEndX - strikethroughStartX,
                    1.0f,
                    curColor);
                pushDrawCmd(ulIdx, 6, null);
            }

        } finally {
            this.endBatch();
        }
        return curX + (enableShadow ? 1.0f : 0.0f);
    }

}

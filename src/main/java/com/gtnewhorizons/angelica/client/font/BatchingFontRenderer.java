package com.gtnewhorizons.angelica.client.font;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import cpw.mods.fml.client.SplashProgress;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
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
    private final int texBoundAttrLocation;
    private final int fontShaderId;

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

    public BatchingFontRenderer(FontRenderer underlying, int[] charWidth, byte[] glyphWidth,
                                int[] colorCode, ResourceLocation locationFontTexture) {
        this.underlying = underlying;
        this.charWidth = charWidth;
        this.glyphWidth = glyphWidth;
        this.colorCode = colorCode;
        this.locationFontTexture = locationFontTexture;

        for (int i = 0; i < 64; i++) {
            batchCommandPool.add(new FontDrawCmd());
        }

        this.isSGA = Objects.equals(this.locationFontTexture.getResourcePath(), "textures/font/ascii_sga.png");
        this.isSplash = FontStrategist.isSplashFontRendererActive(underlying);

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

    int lastActiveProgram;
    int fontAAModeLast = -1;
    int fontAAStrengthLast = -1;
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

        final boolean canUseAA = FontConfig.fontAAMode != 0 && prevProgram == 0;
        if (canUseAA) {
            GL20.glVertexAttribPointer(texBoundAttrLocation, 4, false, 0, batchVtxTexBounds);
            GL20.glEnableVertexAttribArray(texBoundAttrLocation);
            lastActiveProgram = prevProgram;
            GLStateManager.glUseProgram(fontShaderId);
            if (FontConfig.fontAAMode != fontAAModeLast) {
                fontAAModeLast = FontConfig.fontAAMode;
                GL20.glUniform1i(AAMode, FontConfig.fontAAMode);
            }
            if (FontConfig.fontAAStrength != fontAAStrengthLast) {
                fontAAStrengthLast = FontConfig.fontAAStrength;
                GL20.glUniform1f(AAStrength, FontConfig.fontAAStrength / 120.f);
            }
        }

        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL11.glTexCoordPointer(2, 0, batchVtxTexCoords);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 0, batchVtxColors);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glVertexPointer(2, 0, batchVtxPositions);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormal3f(0.0f, 0.0f, 1.0f);

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

            GL11.glDrawElements(GL11.GL_TRIANGLES, batchIndices);
        }
        if (canUseAA) {
            GLStateManager.glUseProgram(lastActiveProgram);
            GL20.glDisableVertexAttribArray(texBoundAttrLocation);
        }

        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

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

    public boolean forceDefaults() {
        return this.isSGA || this.isSplash;
    }

    public float getGlyphScaleX() {
        return forceDefaults() ? 1 : (float) (FontConfig.glyphScale * Math.pow(2, FontConfig.glyphAspect)) * (FontStrategist.customFontInUse ? 1.5f : 1);
    }

    public float getGlyphScaleY() {
        return forceDefaults() ? 1 : (float) (FontConfig.glyphScale / Math.pow(2, FontConfig.glyphAspect)) * (FontStrategist.customFontInUse ? 1.5f : 1);
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

            final boolean rawMode = AngelicaFontRenderContext.isRawTextRendering();
            int curColor = color;
            int curShadowColor = shadowColor;
            boolean curItalic = false;
            boolean curRandom = false;
            boolean curBold = false;
            boolean curStrikethrough = false;
            boolean curUnderline = false;
            boolean curRainbow = false;
            boolean curDinnerbone = false;
            int rainbowIndex = 0;
            final IntArrayList colorStack = new IntArrayList();
            final IntArrayList shadowStack = new IntArrayList();

            final float glyphScaleY = getGlyphScaleY();
            final float heightNorth = anchorY + (underlying.FONT_HEIGHT - 1.0f) * (0.5f - glyphScaleY / 2);
            final float heightSouth = (underlying.FONT_HEIGHT - 1.0f) * glyphScaleY;

            final float underlineY = heightNorth + (underlying.FONT_HEIGHT - 1.0f) * glyphScaleY;
            float underlineStartX = 0.0f;
            float underlineEndX = 0.0f;
            final float strikethroughY = heightNorth + ((float) (underlying.FONT_HEIGHT / 2) - 1.0f) * glyphScaleY;
            float strikethroughStartX = 0.0f;
            float strikethroughEndX = 0.0f;
            int rawTokenSkip = 0;

            for (int charIdx = stringOffset; charIdx < stringEnd; charIdx++) {
                char chr = string.charAt(charIdx);
                boolean processedRgbOrTag = false;

                if (rawMode) {
                    if (rawTokenSkip > 0) {
                        rawTokenSkip--;
                    } else {
                        int tokenLen = ColorCodeUtils.detectColorCodeLengthIgnoringRaw(string, charIdx);
                        if (tokenLen > 0) {
                            float highlightWidth = angelica$measureLiteralWidth(string, charIdx, tokenLen, stringEnd, unicodeFlag, curBold);
                            if (highlightWidth > 0.0f) {
                                final int hlIdx = idxWriterIndex;
                                pushUntexRect(curX, heightNorth - 1.0f, highlightWidth, heightSouth + 2.0f, angelica$getTokenHighlightColor(string, charIdx));
                                pushDrawCmd(hlIdx, 6, null, false);
                            }
                            rawTokenSkip = Math.max(tokenLen - 1, 0);
                        }
                    }
                }

                // Check for RGB color codes FIRST (before traditional § codes)
                // Format: &RRGGBB (ampersand followed by 6 hex digits)
                if (chr == '&' && (charIdx + 6) < stringEnd) {
                    final int rgb = ColorCodeUtils.parseHexColor(string, charIdx + 1);
                    if (rgb != -1) {
                        // Valid RGB color code found
                        if (curUnderline && underlineStartX != underlineEndX) {
                            final int ulIdx = idxWriterIndex;
                            pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, glyphScaleY, curColor);
                            pushDrawCmd(ulIdx, 6, null, false);
                            underlineStartX = underlineEndX;
                        }
                        if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                            final int ulIdx = idxWriterIndex;
                            pushUntexRect(strikethroughStartX, strikethroughY, strikethroughEndX - strikethroughStartX, glyphScaleY, curColor);
                            pushDrawCmd(ulIdx, 6, null, false);
                            strikethroughStartX = strikethroughEndX;
                        }

                        // Apply RGB color (preserve formatting state to allow &l&FFxxxx patterns)
                        colorStack.clear();
                        shadowStack.clear();
                        curColor = (curColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                        curShadowColor = (curShadowColor & 0xFF000000) | ColorCodeUtils.calculateShadowColor(rgb);

                        // reset styles on color change (vanilla behavior)
                        curRandom = false;
                        curBold = false;
                        curStrikethrough = false;
                        curUnderline = false;
                        curItalic = false;
                        curRainbow = false;
                        curDinnerbone = false;

                        processedRgbOrTag = true; // Prevent traditional &X from overwriting

                        if (!rawMode) {
                            charIdx += 6; // Skip the 6 hex digits
                            continue;
                        }
                    }
                }

                // Format: <RRGGBB> (opening tag) or </RRGGBB> (closing tag)
                if (chr == '<') {
                    // Check for closing tag </RRGGBB>
                    if ((charIdx + 9) <= stringEnd && string.charAt(charIdx + 1) == '/' && string.charAt(charIdx + 8) == '>') {
                        if (ColorCodeUtils.isValidHexString(string, charIdx + 2)) {
                            // Valid closing tag - reset to original color
                            if (curUnderline && underlineStartX != underlineEndX) {
                                final int ulIdx = idxWriterIndex;
                                pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, glyphScaleY, curColor);
                                pushDrawCmd(ulIdx, 6, null, false);
                                underlineStartX = underlineEndX;
                            }
                            if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                                final int ulIdx = idxWriterIndex;
                                pushUntexRect(strikethroughStartX, strikethroughY, strikethroughEndX - strikethroughStartX, glyphScaleY, curColor);
                                pushDrawCmd(ulIdx, 6, null, false);
                                strikethroughStartX = strikethroughEndX;
                            }

                            if (!colorStack.isEmpty()) {
                                curColor = colorStack.removeInt(colorStack.size() - 1);
                                curShadowColor = shadowStack.removeInt(shadowStack.size() - 1);
                            } else {
                                curColor = color;
                                curShadowColor = shadowColor;
                            }
                            curRandom = false;
                            curRainbow = false;
                            processedRgbOrTag = true;

                            if (!rawMode) {
                                charIdx += 8; // Skip </RRGGBB> (9 chars total, but loop will increment)
                                continue;
                            }
                        }
                    }
                    // Check for opening tag <RRGGBB>
                    else if ((charIdx + 8) <= stringEnd && string.charAt(charIdx + 7) == '>') {
                        final int rgb = ColorCodeUtils.parseHexColor(string, charIdx + 1);
                        if (rgb != -1) {
                            // Valid opening tag
                            if (curUnderline && underlineStartX != underlineEndX) {
                                final int ulIdx = idxWriterIndex;
                                pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, glyphScaleY, curColor);
                                pushDrawCmd(ulIdx, 6, null, false);
                                underlineStartX = underlineEndX;
                            }
                            if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                                final int ulIdx = idxWriterIndex;
                                pushUntexRect(strikethroughStartX, strikethroughY, strikethroughEndX - strikethroughStartX, glyphScaleY, curColor);
                                pushDrawCmd(ulIdx, 6, null, false);
                                strikethroughStartX = strikethroughEndX;
                            }

                            colorStack.add(curColor);
                            shadowStack.add(curShadowColor);
                            curColor = (curColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                            curShadowColor = (curShadowColor & 0xFF000000) | ColorCodeUtils.calculateShadowColor(rgb);

                            // reset styles on color change
                            curRandom = false;
                            curBold = false;
                            curStrikethrough = false;
                            curUnderline = false;
                            curItalic = false;
                            curRainbow = false;
                            curDinnerbone = false;

                            processedRgbOrTag = true;

                            if (!rawMode) {
                                charIdx += 7; // Skip <RRGGBB> (8 chars total, but loop will increment)
                                continue;
                            }
                        }
                    }
                }

                // Traditional & formatting codes (only if we didn't process RGB/tag code)
                if (!processedRgbOrTag && (chr == FORMATTING_CHAR || chr == '&') && (charIdx + 1) < stringEnd) {
                    final char nextChar = string.charAt(charIdx + 1);
                    final char fmtCode = Character.toLowerCase(nextChar);
                    if (chr == '&' && !ColorCodeUtils.isFormattingCode(nextChar)) {
                        // Not a formatting alias, treat as literal '&'
                    } else {
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
                            final int colorIdx = is09 ? (fmtCode - '0') : (fmtCode - 'a' + 10);
                            final int rgb = this.colorCode[colorIdx];
                            curColor = (curColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                            final int shadowRgb = this.colorCode[colorIdx + 16];
                            curShadowColor = (curShadowColor & 0xFF000000) | (shadowRgb & 0x00FFFFFF);

                            // vanilla resets styles on color
                            curRandom = false;
                            curBold = false;
                            curStrikethrough = false;
                            curUnderline = false;
                            curItalic = false;
                            curRainbow = false;
                            curDinnerbone = false;
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
                        } else if (fmtCode == 'g') {
                            // Rainbow effect - cycles through all hues
                            curRainbow = true;
                            rainbowIndex = 0;
                        } else if (fmtCode == 'h') {
                            // Dinnerbone effect - renders text upside-down
                            curDinnerbone = true;
                        } else if (fmtCode == 'r') {
                            curRandom = false;
                            curBold = false;
                            curStrikethrough = false;
                            curUnderline = false;
                            curItalic = false;
                            curRainbow = false;
                            curDinnerbone = false;
                            rainbowIndex = 0;
                            curColor = color;
                            curShadowColor = shadowColor;
                        }

                        if (!rawMode) {
                            continue;
                        } else {
                            // In raw mode, we still applied the formatting but need to back up charIdx
                            // so we render the formatting character
                            charIdx--;
                        }
                    }
                }

                if (!rawMode && curRandom) {
                    chr = FontProviderMC.get(this.isSGA).getRandomReplacement(chr);
                }

                FontProvider fontProvider = FontStrategist.getFontProvider(this, chr, FontConfig.enableCustomFont, unicodeFlag);

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

                // Apply rainbow color if enabled
                if (curRainbow) {
                    float hue = (rainbowIndex * 15.0f) % 360.0f;
                    int rainbowRgb = ColorCodeUtils.hsvToRgb(hue, 1.0f, 1.0f);
                    curColor = (curColor & 0xFF000000) | (rainbowRgb & 0x00FFFFFF);
                    curShadowColor = (curShadowColor & 0xFF000000) | ColorCodeUtils.calculateShadowColor(rainbowRgb);
                    rainbowIndex++;
                }

                // Calculate V coordinates with dinnerbone flipping (flip texture only, keep Y position)
                final float yTop = heightNorth;
                final float yBottom = heightNorth + heightSouth;
                final float vTop = curDinnerbone ? vStart + vSz : vStart;
                final float vBottom = curDinnerbone ? vStart : vStart + vSz;
                final float itOffTop = itOff;
                final float itOffBottom = -itOff;

                final int vtxId = vtxWriterIndex;
                final int idxId = idxWriterIndex;

                int vtxCount = 0;

                if (enableShadow) {
                    pushVtx(curX + itOffTop + shadowOffset, yTop + shadowOffset, curShadowColor, uStart, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(curX + itOffBottom + shadowOffset, yBottom + shadowOffset, curShadowColor, uStart, vBottom, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(curX + glyphW - 1.0F + itOffTop + shadowOffset, yTop + shadowOffset, curShadowColor, uStart + uSz, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(curX + glyphW - 1.0F + itOffBottom + shadowOffset, yBottom + shadowOffset, curShadowColor, uStart + uSz, vBottom, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushQuadIdx(vtxId + vtxCount);
                    vtxCount += 4;

                    if (curBold) {
                        final float shadowOffset2 = 2.0f * shadowOffset;
                        pushVtx(curX + itOffTop + shadowOffset2, yTop + shadowOffset, curShadowColor, uStart, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
                        pushVtx(curX + itOffBottom + shadowOffset2, yBottom + shadowOffset, curShadowColor, uStart, vBottom, uStart, uStart + uSz, vStart, vStart + vSz);
                        pushVtx(curX + glyphW - 1.0F + itOffTop + shadowOffset2, yTop + shadowOffset, curShadowColor, uStart + uSz, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
                        pushVtx(curX + glyphW - 1.0F + itOffBottom + shadowOffset2, yBottom + shadowOffset, curShadowColor, uStart + uSz, vBottom, uStart, uStart + uSz, vStart, vStart + vSz);
                        pushQuadIdx(vtxId + vtxCount);
                        vtxCount += 4;
                    }
                }

                pushVtx(curX + itOffTop, yTop, curColor, uStart, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
                pushVtx(curX + itOffBottom, yBottom, curColor, uStart, vBottom, uStart, uStart + uSz, vStart, vStart + vSz);
                pushVtx(curX + glyphW - 1.0F + itOffTop, yTop, curColor, uStart + uSz, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
                pushVtx(curX + glyphW - 1.0F + itOffBottom, yBottom, curColor, uStart + uSz, vBottom, uStart, uStart + uSz, vStart, vStart + vSz);
                pushQuadIdx(vtxId + vtxCount);
                vtxCount += 4;

                if (curBold) {
                    pushVtx(shadowOffset + curX + itOffTop, yTop, curColor, uStart, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(shadowOffset + curX + itOffBottom, yBottom, curColor, uStart, vBottom, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(shadowOffset + curX + glyphW - 1.0F + itOffTop, yTop, curColor, uStart + uSz, vTop, uStart, uStart + uSz, vStart, vStart + vSz);
                    pushVtx(shadowOffset + curX + glyphW - 1.0F + itOffBottom, yBottom, curColor, uStart + uSz, vBottom, uStart, uStart + uSz, vStart, vStart + vSz);
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

    private float angelica$measureLiteralWidth(CharSequence string, int start, int tokenLength, int stringEnd, boolean unicodeFlag, boolean initialBoldState) {
        float width = 0.0f;
        boolean isBold = initialBoldState;
        final int limit = Math.min(start + tokenLength, stringEnd);

        for (int i = start; i < limit; i++) {
            char ch = string.charAt(i);

            // Check if this character is the start of a formatting code that affects bold
            if ((ch == '&' || ch == FORMATTING_CHAR) && i + 1 < limit) {
                char nextChar = string.charAt(i + 1);
                char fmtCode = Character.toLowerCase(nextChar);

                // Check if it's a valid formatting code
                if (ch == '&' && !ColorCodeUtils.isFormattingCode(nextChar)) {
                    // Not a valid formatting code, continue
                } else if (fmtCode == 'l') {
                    isBold = true;
                } else if (fmtCode == 'r') {
                    isBold = false;
                } else if ((fmtCode >= '0' && fmtCode <= '9') || (fmtCode >= 'a' && fmtCode <= 'f')) {
                    // In Angelica, color codes don't reset bold (preserves formatting)
                    // So we keep isBold unchanged
                }
            }

            FontProvider provider = FontStrategist.getFontProvider(ch, this.isSGA, FontConfig.enableCustomFont, unicodeFlag);
            float xAdvance = provider.getXAdvance(ch) * getGlyphScaleX();
            width += xAdvance;
            if (isBold) {
                width += this.getShadowOffset();
            }
            width += getGlyphSpacing();
        }
        return width;
    }

    private int angelica$getTokenHighlightColor(CharSequence string, int index) {
        char c = string.charAt(index);
        if (c == FORMATTING_CHAR || (c == '&' && index + 1 < string.length() && ColorCodeUtils.isFormattingCode(string.charAt(index + 1)))) {
            return 0x304080FF;
        }
        if (c == '&') {
            return 0x3039C86F;
        }
        if (c == '<') {
            if (index + 1 < string.length() && string.charAt(index + 1) == '/') {
                return 0x30FF8C5A;
            }
            return 0x305A8CFF;
        }
        return 0x30222222;
    }

    public float getCharWidthFine(char chr) {
        if (chr == FORMATTING_CHAR && !AngelicaFontRenderContext.isRawTextRendering()) { return -1; }

        // Note: We DO NOT return -1 for & or < here anymore
        // Width calculation is handled properly in getStringWidthWithRgb()
        // This allows & and < to render normally when they're not part of valid color codes

        if (chr == ' ' || chr == '\u00A0' || chr == '\u202F') {
            return 4 * this.getWhitespaceScale();
        }

        FontProvider fp = FontStrategist.getFontProvider(this, chr, FontConfig.enableCustomFont, underlying.getUnicodeFlag());

        return fp.getXAdvance(chr) * this.getGlyphScaleX();
    }

    /**
     * Calculate the width of a string, properly handling RGB color codes.
     * This method correctly skips over:
     * - Traditional § codes (2 chars)
     * - &RRGGBB format (7 chars)
     * - <RRGGBB> format (8 chars)
     * - </RRGGBB> format (9 chars)
     *
     * @param str The string to measure
     * @return The width in pixels
     */
    public float getStringWidthWithRgb(CharSequence str) {
        if (str == null || str.length() == 0) {
            return 0.0f;
        }

        final boolean rawMode = AngelicaFontRenderContext.isRawTextRendering();
        return FormattedTextMetrics.calculateMaxLineWidth(str, rawMode, this::getCharWidthFine,
            getGlyphSpacing(), this.getShadowOffset());
    public void overrideBlendFunc(int srcRgb, int dstRgb) {
        blendSrcRGB = srcRgb;
        blendDstRGB = dstRgb;
    }

    public void resetBlendFunc() {
        blendSrcRGB = GL11.GL_SRC_ALPHA;
        blendDstRGB = GL11.GL_ONE_MINUS_SRC_ALPHA;
    }
}

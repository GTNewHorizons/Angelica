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

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memRealloc;

/**
 * A batching replacement for {@code FontRenderer}
 *
 * @author eigenraven
 */
public class BatchingFontRenderer {

    /**
     * The underlying FontRenderer object that's being accelerated
     */
    protected FontRenderer underlying;
    /**
     * Array of width of all the characters in default.png
     */
    protected int[] charWidth = new int[256];
    /**
     * Array of the start/end column (in upper/lower nibble) for every glyph in the /font directory.
     */
    protected byte[] glyphWidth;
    /**
     * Array of RGB triplets defining the 16 standard chat colors followed by 16 darker version of the same colors for
     * drop shadows.
     */
    private int[] colorCode;
    /**
     * Location of the primary font atlas to bind.
     */
    protected final ResourceLocation locationFontTexture;

    private final int AAMode;
    private final int AAStrength;
    private final int texBoundAttrLocation;
    private final int fontShaderId;

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

    /**
     *
     */
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

        if (FontConfig.fontAAMode != 0) {
            GL20.glVertexAttribPointer(texBoundAttrLocation, 4, false, 0, batchVtxTexBounds);
            GL20.glEnableVertexAttribArray(texBoundAttrLocation);
            lastActiveProgram = GLStateManager.getActiveProgram();
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

            GL11.glDrawElements(GL11.GL_TRIANGLES, batchIndices);
        }
        if (FontConfig.fontAAMode != 0) {
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

    public float getGlyphScaleX() {
        return this.isSGA ? 1 : (float) (FontConfig.glyphScale * Math.pow(2, FontConfig.glyphAspect)) * (FontStrategist.customFontInUse ? 1.5f : 1);
    }

    public float getGlyphScaleY() {
        return this.isSGA ? 1 : (float) (FontConfig.glyphScale / Math.pow(2, FontConfig.glyphAspect)) * (FontStrategist.customFontInUse ? 1.5f : 1);
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

    public float drawString(
        final float startX,
        final float startY,
        final int baseColorARGB,
        final boolean drawShadow,
        final boolean unicodeFlag,
        final CharSequence text,
        int textOffset,
        int textLen
    ) {
        // Fast exits
        if (text == null || text.length() == 0) return startX + (drawShadow ? 1.0f : 0.0f);

        // Shadow color computed like vanilla
        final int baseShadowARGB = (baseColorARGB & 0xFCFCFC) >> 2 | (baseColorARGB & 0xFF000000);

        // Inform providers of current font assets (vanilla atlas vs unicode pages)
        FontProviderMC.get(this.isSGA).charWidth = this.charWidth;
        FontProviderMC.get(this.isSGA).locationFontTexture = this.locationFontTexture;

        // Clamp the slice we’ll draw
        final int totalLen = text.length();
        textOffset = MathHelper.clamp_int(textOffset, 0, totalLen);
        textLen = MathHelper.clamp_int(textLen, 0, totalLen - textOffset);
        if (textLen <= 0) return 0;

        // Per-line vertical metrics (derived from vanilla FONT_HEIGHT + Angelica scaling)
        final float scaleY = getGlyphScaleY();
        final float lineHeight = (underlying.FONT_HEIGHT - 1.0f) * scaleY;
        final float ascentY = startY + (underlying.FONT_HEIGHT - 1.0f) * (0.5f - scaleY / 2.0f); // top of glyphs
        final float underlineYOffset = (underlying.FONT_HEIGHT - 1.0f) * scaleY;
        final float strikethroughYOffset = ((underlying.FONT_HEIGHT / 2.0f) - 1.0f) * scaleY;
        final float lineAdvance = underlying.FONT_HEIGHT; // vertical step between lines (vanilla baseline distance)

        // Dynamic drawing state
        float penX = startX;
        float lineYOffset = 0.0f;       // how far we’ve moved down from the first line

        int currentColor = baseColorARGB;
        int currentShadow = baseShadowARGB;
        boolean styleItalic = false;
        boolean styleRandom = false;
        boolean styleBold = false;
        boolean styleStrike = false;
        boolean styleUnder = false;
        boolean styleRainbow = false;
        boolean styleFlip = false;   // dinnerbone

        final float boldDx = getShadowOffset();

        int rainbowStep = 0;

        // For nested RGB tag colors (<RRGGBB> ... </RRGGBB>)
        final it.unimi.dsi.fastutil.ints.IntArrayList colorStack = new it.unimi.dsi.fastutil.ints.IntArrayList();
        final it.unimi.dsi.fastutil.ints.IntArrayList shadowStack = new it.unimi.dsi.fastutil.ints.IntArrayList();

        // Underline / strikethrough segments on the current line
        float underlineStartX = 0.0f, underlineEndX = 0.0f;
        float strikeStartX = 0.0f, strikeEndX = 0.0f;

        // Editor “raw mode” highlighting support (unchanged behavior)
        final boolean rawMode = AngelicaFontRenderContext.isRawTextRendering();
        int rawTokenSkip = 0;

        beginBatch();
        try {
            final int end = textOffset + textLen;

            for (int i = textOffset; i < end; i++) {
                char ch = text.charAt(i);

                // 1) Hard line break
                if (ch == '\n') {
                    // Flush underline/strike for this line
                    if (styleUnder && underlineStartX != underlineEndX) {
                        final int idx = idxWriterIndex;
                        pushUntexRect(underlineStartX, ascentY + lineYOffset + underlineYOffset,
                            underlineEndX - underlineStartX, scaleY, currentColor);
                        pushDrawCmd(idx, 6, null, false);
                        underlineStartX = underlineEndX = penX;
                    }
                    if (styleStrike && strikeStartX != strikeEndX) {
                        final int idx = idxWriterIndex;
                        pushUntexRect(strikeStartX, ascentY + lineYOffset + strikethroughYOffset,
                            strikeEndX - strikeStartX, scaleY, currentColor);
                        pushDrawCmd(idx, 6, null, false);
                        strikeStartX = strikeEndX = penX;
                    }

                    // Move pen to next line
                    lineYOffset += lineAdvance;
                    penX = startX;
                    continue;
                }

                // 2) Raw-mode token highlight (unchanged, just renamed vars)
                if (rawMode) {
                    if (rawTokenSkip > 0) {
                        rawTokenSkip--;
                    } else {
                        int tokenLen = ColorCodeUtils.detectColorCodeLengthIgnoringRaw(text, i);
                        if (tokenLen > 0) {
                            float tokenW = angelica$measureLiteralWidth(text, i, tokenLen, end, unicodeFlag, styleBold);
                            if (tokenW > 0) {
                                final int idx = idxWriterIndex;
                                pushUntexRect(penX, ascentY + lineYOffset - 1.0f,
                                    tokenW, lineHeight + 2.0f,
                                    angelica$getTokenHighlightColor(text, i));
                                pushDrawCmd(idx, 6, null, false);
                            }
                            rawTokenSkip = Math.max(tokenLen - 1, 0);
                        }
                    }
                }

                // 3) RGB and formatting codes
                boolean consumedFormatting = false;

                // 3a) &RRGGBB
                if (ch == '&' && (i + 6) < end) {
                    final int rgb = ColorCodeUtils.parseHexColor(text, i + 1);
                    if (rgb != -1) {
                        // Close any active underline/strike segments before changing color
                        if (styleUnder && underlineStartX != underlineEndX) {
                            final int idx = idxWriterIndex;
                            pushUntexRect(underlineStartX, ascentY + lineYOffset + underlineYOffset,
                                underlineEndX - underlineStartX, scaleY, currentColor);
                            pushDrawCmd(idx, 6, null, false);
                            underlineStartX = underlineEndX;
                        }
                        if (styleStrike && strikeStartX != strikeEndX) {
                            final int idx = idxWriterIndex;
                            pushUntexRect(strikeStartX, ascentY + lineYOffset + strikethroughYOffset,
                                strikeEndX - strikeStartX, scaleY, currentColor);
                            pushDrawCmd(idx, 6, null, false);
                            strikeStartX = strikeEndX;
                        }

                        // Apply new color and reset styles (vanilla behavior on color change)
                        colorStack.clear();
                        shadowStack.clear();
                        currentColor = (currentColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                        currentShadow = (currentShadow & 0xFF000000) | ColorCodeUtils.calculateShadowColor(rgb);

                        styleRandom = false;
                        styleBold = false;
                        styleStrike = false;
                        styleUnder = false;
                        styleItalic = false;
                        styleRainbow = false;
                        styleFlip = false;

                        consumedFormatting = true;
                        if (!rawMode) {
                            i += 6;
                            continue;
                        }
                    }
                }

                // 3b) <RRGGBB> or </RRGGBB>
                if (!consumedFormatting && ch == '<') {
                    // Close tag: </RRGGBB>
                    if ((i + 9) <= end && text.charAt(i + 1) == '/' && text.charAt(i + 8) == '>') {
                        if (ColorCodeUtils.isValidHexString(text, i + 2)) {
                            if (styleUnder && underlineStartX != underlineEndX) {
                                final int idx = idxWriterIndex;
                                pushUntexRect(underlineStartX, ascentY + lineYOffset + underlineYOffset,
                                    underlineEndX - underlineStartX, scaleY, currentColor);
                                pushDrawCmd(idx, 6, null, false);
                                underlineStartX = underlineEndX;
                            }
                            if (styleStrike && strikeStartX != strikeEndX) {
                                final int idx = idxWriterIndex;
                                pushUntexRect(strikeStartX, ascentY + lineYOffset + strikethroughYOffset,
                                    strikeEndX - strikeStartX, scaleY, currentColor);
                                pushDrawCmd(idx, 6, null, false);
                                strikeStartX = strikeEndX;
                            }

                            if (!colorStack.isEmpty()) {
                                currentColor = colorStack.removeInt(colorStack.size() - 1);
                                currentShadow = shadowStack.removeInt(shadowStack.size() - 1);
                            } else {
                                currentColor = baseColorARGB;
                                currentShadow = baseShadowARGB;
                            }
                            styleRandom = false;
                            styleRainbow = false;
                            consumedFormatting = true;

                            if (!rawMode) {
                                i += 8;
                                continue;
                            }
                        }
                    }
                    // Open tag: <RRGGBB>
                    else if ((i + 8) <= end && text.charAt(i + 7) == '>') {
                        final int rgb = ColorCodeUtils.parseHexColor(text, i + 1);
                        if (rgb != -1) {
                            if (styleUnder && underlineStartX != underlineEndX) {
                                final int idx = idxWriterIndex;
                                pushUntexRect(underlineStartX, ascentY + lineYOffset + underlineYOffset,
                                    underlineEndX - underlineStartX, scaleY, currentColor);
                                pushDrawCmd(idx, 6, null, false);
                                underlineStartX = underlineEndX;
                            }
                            if (styleStrike && strikeStartX != strikeEndX) {
                                final int idx = idxWriterIndex;
                                pushUntexRect(strikeStartX, ascentY + lineYOffset + strikethroughYOffset,
                                    strikeEndX - strikeStartX, scaleY, currentColor);
                                pushDrawCmd(idx, 6, null, false);
                                strikeStartX = strikeEndX;
                            }

                            colorStack.add(currentColor);
                            shadowStack.add(currentShadow);
                            currentColor = (currentColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                            currentShadow = (currentShadow & 0xFF000000) | ColorCodeUtils.calculateShadowColor(rgb);

                            // Vanilla resets styles on color change
                            styleRandom = false;
                            styleBold = false;
                            styleStrike = false;
                            styleUnder = false;
                            styleItalic = false;
                            styleRainbow = false;
                            styleFlip = false;

                            consumedFormatting = true;
                            if (!rawMode) {
                                i += 7;
                                continue;
                            }
                        }
                    }
                }

                // 3c) Traditional (§) or alias (&) formatting codes
                if (!consumedFormatting && (ch == FORMATTING_CHAR || ch == '&') && (i + 1) < end) {
                    final char next = text.charAt(i + 1);
                    final char fmt = Character.toLowerCase(next);

                    // treat '&' as literal unless it's a valid formatting code
                    if (ch == '&' && !ColorCodeUtils.isFormattingCode(next)) {
                        // fall-through to render literal '&'
                    } else {
                        i++; // consume code

                        // before changing styles, flush current underline/strike segments
                        if (styleUnder && underlineStartX != underlineEndX) {
                            final int idx = idxWriterIndex;
                            pushUntexRect(underlineStartX, ascentY + lineYOffset + underlineYOffset,
                                underlineEndX - underlineStartX, scaleY, currentColor);
                            pushDrawCmd(idx, 6, null, false);
                            underlineStartX = underlineEndX;
                        }
                        if (styleStrike && strikeStartX != strikeEndX) {
                            final int idx = idxWriterIndex;
                            pushUntexRect(strikeStartX, ascentY + lineYOffset + strikethroughYOffset,
                                strikeEndX - strikeStartX, scaleY, currentColor);
                            pushDrawCmd(idx, 6, null, false);
                            strikeStartX = strikeEndX;
                        }

                        final boolean is09 = (fmt >= '0' && fmt <= '9');
                        final boolean isAF = (fmt >= 'a' && fmt <= 'f');

                        if (is09 || isAF) {
                            // Vanilla: color sets RGB and resets all styles
                            final int colorIdx = is09 ? (fmt - '0') : (fmt - 'a' + 10);
                            currentColor = (currentColor & 0xFF000000) | (this.colorCode[colorIdx] & 0x00FFFFFF);
                            currentShadow = (currentShadow & 0xFF000000) | (this.colorCode[colorIdx + 16] & 0x00FFFFFF);

                            styleRandom = false;
                            styleBold = false;
                            styleStrike = false;
                            styleUnder = false;
                            styleItalic = false;
                            styleRainbow = false;
                            styleFlip = false;
                        } else if (fmt == 'k') {
                            styleRandom = true;
                        } else if (fmt == 'l') {
                            styleBold = true;
                        } else if (fmt == 'm') {
                            styleStrike = true;
                            strikeStartX = penX - 1.0f;
                            strikeEndX = strikeStartX;
                        } else if (fmt == 'n') {
                            styleUnder = true;
                            underlineStartX = penX - 1.0f;
                            underlineEndX = underlineStartX;
                        } else if (fmt == 'o') {
                            styleItalic = true;
                        } else if (fmt == 'g') {
                            styleRainbow = true;
                            rainbowStep = 0;
                        } else if (fmt == 'h') {
                            styleFlip = true;
                        } else if (fmt == 'r') {
                            styleRandom = false;
                            styleBold = false;
                            styleStrike = false;
                            styleUnder = false;
                            styleItalic = false;
                            styleRainbow = false;
                            styleFlip = false;
                            rainbowStep = 0;
                            currentColor = baseColorARGB;
                            currentShadow = baseShadowARGB;
                        }

                        if (!rawMode) {
                            continue; // formatting consumed
                        } else {
                            i--; // in rawMode we still draw the code char itself
                        }
                    }
                }

                // 4) Random obfuscation (after formatting has been applied)
                if (!rawMode && styleRandom) {
                    ch = FontProviderMC.get(this.isSGA).getRandomReplacement(ch);
                }

                // 5) Space (ASCII / NBSP / NNBSP) → just advance penX
                if (ch == ' ' || ch == '\u00A0' || ch == '\u202F') {
                    final float spaceAdvance = 4 * getWhitespaceScale()
                        + (styleBold ? boldDx : 0.0f)
                        + getGlyphSpacing();
                    penX += spaceAdvance;

                    // keep underline/strike segment ends in sync with caret movement
                    if (styleUnder)
                        underlineEndX = penX;
                    if (styleStrike)
                        strikeEndX = penX;
                    continue;
                }

                // 6) Lookup glyph metrics/texture and push quads
                final FontProvider fp = FontStrategist.getFontProvider(ch, this.isSGA, FontConfig.enableCustomFont, unicodeFlag);

                // Rainbow (per glyph)
                if (styleRainbow) {
                    float hue = (rainbowStep * 15.0f) % 360.0f;
                    int rgb = ColorCodeUtils.hsvToRgb(hue, 1.0f, 1.0f);
                    currentColor = (currentColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                    currentShadow = (currentShadow & 0xFF000000) | ColorCodeUtils.calculateShadowColor(rgb);
                    rainbowStep++;
                }

                final float u0 = fp.getUStart(ch);
                final float v0 = fp.getVStart(ch);
                final float uSize = fp.getUSize(ch);
                final float vSize = fp.getVSize(ch);
                final float advX = fp.getXAdvance(ch) * getGlyphScaleX();
                final float gw = fp.getGlyphW(ch) * getGlyphScaleX();
                final float italicOffset = styleItalic ? 1.0f : 0.0f;
                final float shadowDx = fp.getShadowOffset();
                final ResourceLocation tex = fp.getTexture(ch);

                // Current baseline for this line
                final float yTop = ascentY + lineYOffset;
                final float yBottom = yTop + lineHeight;

                // Texture V flip for dinnerbone (flip texture only, not geometry Y)
                final float vTop = styleFlip ? (v0 + vSize) : v0;
                final float vBottom = styleFlip ? v0 : (v0 + vSize);

                final float x0 = penX;
                final float x1 = penX + gw - 1.0f;

                // push vertices (shadow → normal → bold offset)
                final int vStart = vtxWriterIndex;
                final int iStart = idxWriterIndex;
                int pushedQuads = 0;

                if (drawShadow) {
                    pushVtx(x0 + italicOffset + shadowDx, yTop + shadowDx, currentShadow, u0, vTop, u0, u0 + uSize, v0, v0 + vSize);
                    pushVtx(x0 - italicOffset + shadowDx, yBottom + shadowDx, currentShadow, u0, vBottom, u0, u0 + uSize, v0, v0 + vSize);
                    pushVtx(x1 + italicOffset + shadowDx, yTop + shadowDx, currentShadow, u0 + uSize, vTop, u0, u0 + uSize, v0, v0 + vSize);
                    pushVtx(x1 - italicOffset + shadowDx, yBottom + shadowDx, currentShadow, u0 + uSize, vBottom, u0, u0 + uSize, v0, v0 + vSize);
                    pushQuadIdx(vStart + pushedQuads * 4);
                    pushedQuads++;

                    if (styleBold) {
                        final float shadowDxBold = shadowDx + boldDx; // not 2 * boldDx
                        pushVtx(x0 + italicOffset + shadowDxBold, yTop + shadowDx,    currentShadow, u0,         vTop,    u0, u0 + uSize, v0, v0 + vSize);
                        pushVtx(x0 - italicOffset + shadowDxBold, yBottom + shadowDx, currentShadow, u0,         vBottom, u0, u0 + uSize, v0, v0 + vSize);
                        pushVtx(x1 + italicOffset + shadowDxBold, yTop + shadowDx,    currentShadow, u0 + uSize, vTop,    u0, u0 + uSize, v0, v0 + vSize);
                        pushVtx(x1 - italicOffset + shadowDxBold, yBottom + shadowDx, currentShadow, u0 + uSize, vBottom, u0, u0 + uSize, v0, v0 + vSize);
                        pushQuadIdx(vStart + pushedQuads * 4);
                        pushedQuads++;
                    }
                }

                // Normal glyph
                pushVtx(x0 + italicOffset, yTop, currentColor, u0, vTop, u0, u0 + uSize, v0, v0 + vSize);
                pushVtx(x0 - italicOffset, yBottom, currentColor, u0, vBottom, u0, u0 + uSize, v0, v0 + vSize);
                pushVtx(x1 + italicOffset, yTop, currentColor, u0 + uSize, vTop, u0, u0 + uSize, v0, v0 + vSize);
                pushVtx(x1 - italicOffset, yBottom, currentColor, u0 + uSize, vBottom, u0, u0 + uSize, v0, v0 + vSize);
                pushQuadIdx(vStart + pushedQuads * 4);
                pushedQuads++;

                if (styleBold) {
                    pushVtx(boldDx + x0 + italicOffset, yTop,    currentColor, u0,         vTop,    u0, u0 + uSize, v0, v0 + vSize);
                    pushVtx(boldDx + x0 - italicOffset, yBottom, currentColor, u0,         vBottom, u0, u0 + uSize, v0, v0 + vSize);
                    pushVtx(boldDx + x1 + italicOffset, yTop,    currentColor, u0 + uSize, vTop,    u0, u0 + uSize, v0, v0 + vSize);
                    pushVtx(boldDx + x1 - italicOffset, yBottom, currentColor, u0 + uSize, vBottom, u0, u0 + uSize, v0, v0 + vSize);
                    pushQuadIdx(vStart + pushedQuads * 4);
                    pushedQuads++;
                }

                // Record draw for this glyph batch
                pushDrawCmd(iStart, pushedQuads * 6, tex, ch > 255);

                // Advance caret (include spacing; bold adds an extra shadow offset like vanilla)
                penX += (advX + (styleBold ? boldDx : 0.0f)) + getGlyphSpacing();

                // Keep decoration extents in sync with caret
                underlineEndX = penX;
                strikeEndX = penX;
            }

            // 7) Flush remaining underline/strike on the last line
            if (styleUnder && underlineStartX != underlineEndX) {
                final int idx = idxWriterIndex;
                pushUntexRect(underlineStartX, ascentY + lineYOffset + underlineYOffset,
                    underlineEndX - underlineStartX, scaleY, currentColor);
                pushDrawCmd(idx, 6, null, false);
            }
            if (styleStrike && strikeStartX != strikeEndX) {
                final int idx = idxWriterIndex;
                pushUntexRect(strikeStartX, ascentY + lineYOffset + strikethroughYOffset,
                    strikeEndX - strikeStartX, scaleY, currentColor);
                pushDrawCmd(idx, 6, null, false);
            }

        } finally {
            endBatch();
        }

        // Return the final pen position (matches vanilla’s “right edge”), with +1 if shadow was drawn.
        return penX + (drawShadow ? 1.0f : 0.0f);
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
        if (chr == FORMATTING_CHAR && !AngelicaFontRenderContext.isRawTextRendering()) {
            return -1;
        }

        // Note: We DO NOT return -1 for & or < here anymore
        // Width calculation is handled properly in getStringWidthWithRgb()
        // This allows & and < to render normally when they're not part of valid color codes

        if (chr == ' ' || chr == '\u00A0' || chr == '\u202F') {
            return 4 * this.getWhitespaceScale();
        }

        FontProvider fp = FontStrategist.getFontProvider(chr, isSGA, FontConfig.enableCustomFont, underlying.getUnicodeFlag());

        return fp.getXAdvance(chr) * this.getGlyphScaleX();
    }

    /**
     * Calculate the width of a string, properly handling RGB color codes.
     * This method correctly skips over:
     * - Traditional § codes (2 chars)
     * - &RRGGBB format (7 chars)
     * - <RRGGBB> format (9 chars)
     * - </RRGGBB> format (10 chars)
     *
     * @param str The string to measure
     * @return The width in pixels
     */
    public float getStringWidthWithRgb(CharSequence str) {
        if (str == null || str.length() == 0) {
            return 0.0f;
        }

        float width = 0.0f, maxWidth = 0.0f;
        boolean isBold = false;
        final boolean rawMode = AngelicaFontRenderContext.isRawTextRendering();

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);

            if (ch == '\n') {
                if (width > maxWidth) maxWidth = width;
                width = 0.0f;
                isBold = false; // vanilla-style reset across lines
                continue;
            }

            // STRICT: only fully-formed color/format codes are zero-width
            int codeLen = rawMode ? 0 : ColorCodeUtils.detectColorCodeLength(str, i);
            if (codeLen > 0) {
                if (codeLen == 2 && i + 1 < str.length()) {
                    char fmt = Character.toLowerCase(str.charAt(i + 1));
                    if (fmt == 'l') {
                        isBold = true;
                    } else if (fmt == 'r' || (fmt >= '0' && fmt <= '9') || (fmt >= 'a' && fmt <= 'f')) {
                        isBold = false;
                    }
                }
                i += codeLen - 1; // skip whole token
                continue;
            }

            float charW = getCharWidthFine(ch);
            if (charW > 0) {
                width += charW;
                if (isBold) width += this.getShadowOffset();

                // Add spacing only if a visible glyph follows on the same line
                boolean nextVisibleSameLine = false;
                int j = i + 1;
                while (j < str.length()) {
                    char cj = str.charAt(j);
                    if (cj == '\n') break;
                    int n2 = rawMode ? 0 : ColorCodeUtils.detectColorCodeLength(str, j); // STRICT
                    if (n2 > 0) { j += n2; continue; }
                    if (getCharWidthFine(cj) > 0) nextVisibleSameLine = true;
                    break;
                }
                if (nextVisibleSameLine) width += getGlyphSpacing();
            }
        }

        return Math.max(width, maxWidth);
    }
}

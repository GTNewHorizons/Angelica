package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.shader.AutoShaderUpdater;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.IShaderDefinesInjector;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.IShaderReloadRunnable;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.SimpleShaderDefine;
import com.gtnewhorizon.gtnhlib.util.font.GlyphReplacements;
import com.gtnewhorizons.angelica.client.streaming.StreamingDrawer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import lombok.Setter;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL42;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutByte;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.FORMATTING_CHAR;
import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.GRADIENT_PAYLOAD;
import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.SECTION_X_LENGTH;
import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.SECTION_X_PAYLOAD;

/**
 * A batching replacement for {@code FontRenderer}
 *
 * @author eigenraven
 */
public final class BatchingFontRenderer {

    /** The underlying FontRenderer object that's being accelerated */
    private final FontRenderer underlying;
    /** Array of width of all the characters in default.png */
    private final int[] charWidth;
    /**
     * Array of RGB triplets defining the 16 standard chat colors followed by 16 darker version of the same colors for
     * drop shadows.
     */
    private final int[] colorCode;

    private static int AAStrength;
    private static int mvpMatrixLocation;
    private static int fontShaderId;

    final boolean isSGA;
    final boolean isSplash;

    private final int fontTexture;

    private static FontTextureArray mainTextureArray;

    /** For use with modded books. Affects calculations and forces some defaults. */
    @Setter
    boolean bookMode = false;

    public BatchingFontRenderer(FontRenderer underlying, int[] charWidth, int[] colorCode, ResourceLocation locationFontTexture) {
        this.underlying = underlying;
        this.charWidth = charWidth;
        this.colorCode = colorCode;

        this.isSGA = locationFontTexture.getResourcePath().equals("textures/font/ascii_sga.png");
        this.isSplash = FontStrategist.isSplashFontRendererActive(underlying);

        FontProviderMC.get(this.isSGA).charWidth = this.charWidth;

        // Determine GL texture by binding & querying the bound texture
        ((FontRendererAccessor) underlying).angelica$bindTexture(locationFontTexture);
        this.fontTexture = GLStateManager.getBoundTextureForServerState();
        if (mainTextureArray == null) {
            mainTextureArray = new FontTextureArray(256);
            mainTextureArray.addAtlasFromBoundTexture(0);
        }

        final String vsh = ShaderProgram.loadShaderSource(
            BatchingFontRenderer.class.getResourceAsStream("/assets/angelica/shaders/font/font.vsh")
        );
        final String fsh = ShaderProgram.loadShaderSource(
            BatchingFontRenderer.class.getResourceAsStream("/assets/angelica/shaders/font/font.fsh"),
            new SimpleShaderDefine("AA_MODE", FontConfig.fontAAMode)
        );

        final ShaderProgram program = new ShaderProgram(vsh, fsh);
        if (!isSplash) {
            AutoShaderUpdater.getInstance().registerShaderReload(program,
                new ResourceLocation("angelica", "shaders/font/font.vsh"),
                new ResourceLocation("angelica", "shaders/font/font.fsh"),
                new IShaderReloadRunnable() {
                    @Override
                    public void run(ShaderProgram shaderProgram) {
                        fontShaderId = shaderProgram.getProgram();
                        AAStrength = GLStateManager.glGetUniformLocation(fontShaderId, "strength");
                        mvpMatrixLocation = GLStateManager.glGetUniformLocation(fontShaderId, "u_MVPMatrix");
                    }

                    @Override
                    public IShaderDefinesInjector[] getDefines() {
                        return new IShaderDefinesInjector[] {
                            new SimpleShaderDefine("AA_MODE", FontConfig.fontAAMode)
                        };
                    }
                }
            );
        }
        //fontShaderId = ShaderProgram.createProgram(vsh, fsh);
        fontShaderId = program.getProgram();
        GLStateManager.glUseProgram(fontShaderId);

        GLStateManager.glUseProgram(0);
        AAStrength = GLStateManager.glGetUniformLocation(fontShaderId, "strength");
        mvpMatrixLocation = GLStateManager.glGetUniformLocation(fontShaderId, "u_MVPMatrix");
    }

    private int blendSrcRGB = GL11.GL_SRC_ALPHA;
    private int blendDstRGB = GL11.GL_ONE_MINUS_SRC_ALPHA;

    //TODO fix this (shader issue)
    private void pushUntexRect(float x, float y, float w, float h, int rgba) {
//        ensureCapacity();
//        pushVertex(x, y, rgba, 0, 0, 0, 0, 0, 0);
//        pushVertex(x, y + h, rgba, 0, 0, 0, 0, 0, 0);
//        pushVertex(x + w, y, rgba, 0, 0, 0, 0, 0, 0);
//        pushVertex(x + w, y + h, rgba, 0, 0, 0, 0, 0, 0);
//        pushQuadIdx();
    }

    private void pushTexRect(
        float x, float y, float w, float h,
        float uStart, float vStart, float uSz, float vSz,
        int rgba,
        int layer,
        boolean italic,
        boolean flipV,
        int texture
    ) { //TODO remove
        pushQuad(x, y, w, h, uStart, vStart, uSz, vSz, rgba, layer, italic, flipV);
    }

    private void pushShaderCmd(
        int startIdx,
        int idxCount,
        int texture,
        float x, float y,
        float width, float height
    ) {
        /*
        if (!batchCommands.isEmpty()) {
            final FontDrawCmd lastCmd = batchCommands.get(batchCommands.size() - 1);
            final int prevEndVtx = lastCmd.startVtx + lastCmd.idxCount;
            if (lastCmd instanceof ShaderDrawCmd shaderCmd) {
                if (prevEndVtx == startIdx && lastCmd.texture == texture) {
                    // Coalesce into one
                    lastCmd.idxCount += idxCount;
                    shaderCmd.xEnd = x + width;
                    shaderCmd.yEnd = y + height;
                    return;
                }
            }
        }
        final ShaderDrawCmd cmd = new ShaderDrawCmd();
        cmd.reset(startIdx, idxCount, texture);
        cmd.xStart = x;
        cmd.yStart = y;
        cmd.xEnd = x + width;
        cmd.yEnd = y + height;
        batchCommands.add(cmd);

         */
    }

    // === Actual text mesh generation
    public static boolean charInRange(char what, char fromInclusive, char toInclusive) {
        return (what >= fromInclusive) && (what <= toInclusive);
    }

    /**
     * Count visible characters from {@code start} to {@code end}, skipping format codes.
     * Stops at §r, any color code (§0-f, §x), or any color effect (§q, §g).
     * Style codes (§k-o) and non-terminating effects (§z wave, §v dinnerbone) are skipped.
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
                i++;
            } else {
                count++;
            }
        }
        return Math.max(count, 1);
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

    private static final int RAINBOW_LUT_SIZE = 24;
    private static final int[] RAINBOW_LUT = new int[RAINBOW_LUT_SIZE];
    static {
        for (int i = 0; i < RAINBOW_LUT_SIZE; i++) {
            RAINBOW_LUT[i] = hsvToRgb(i * 15f, 1f, 1f);
        }
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

    private static final double WAVE_TIME_SCALE = 5e-9;
    private static final float WAVE_FREQUENCY = 0.5f;

    public int drawString(final int anchorX, final int anchorY, final int color, final boolean enableShadow,
        final boolean unicodeFlag, final String string) {
        // noinspection SizeReplaceableByIsEmpty
        if (string == null || string.length() == 0) {
            return anchorX + (enableShadow ? 1 : 0);
        }
        final int shadowColor = (color & 0xfcfcfc) >> 2 | color & 0xff000000;

        FontProviderMC.get(this.isSGA).charWidth = this.charWidth;

        this.beginBatch();
        float curX = anchorX;
        try {
            final int stringEnd = string.length();

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
            boolean curShader = false;
            int gradientStartRgb = 0, gradientEndRgb = 0;
            int gradientCharIndex = 0, gradientTotalChars = 0;
            float gradientStep = 0f;
            int rainbowCharIndex = 0;
            int visibleCharIndex = 0;

            float glyphScaleY = getGlyphScaleY();
            float glyphScaleX = getGlyphScaleX();
            float heightNorth = anchorY + (underlying.FONT_HEIGHT - 1.0f) * (0.5f - glyphScaleY / 2);

            final float underlineY = heightNorth + (underlying.FONT_HEIGHT - 1.0f) * glyphScaleY;
            float underlineStartX = 0.0f;
            float underlineEndX = 0.0f;

            final float strikethroughY = heightNorth + ((float) (underlying.FONT_HEIGHT / 2) - 1.0f) * glyphScaleY;
            float strikethroughStartX = 0.0f;
            float strikethroughEndX = 0.0f;


            for (int charIdx = 0; charIdx < stringEnd; charIdx++) {
                char chr = string.charAt(charIdx);
                if (chr == FORMATTING_CHAR && (charIdx + 1) < stringEnd) {
                    final char fmtCode = Character.toLowerCase(string.charAt(charIdx + 1));
                    charIdx++;

                    if (curUnderline && underlineStartX != underlineEndX) {
                        pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, glyphScaleY, curColor);
                        underlineStartX = underlineEndX;
                    }
                    if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                        pushUntexRect(
                            strikethroughStartX,
                            strikethroughY,
                            strikethroughEndX - strikethroughStartX,
                            glyphScaleY,
                            curColor);
                        strikethroughStartX = strikethroughEndX;
                    }

                    if (fmtCode == 'x' && AngelicaConfig.enableRGBColors && charIdx + SECTION_X_PAYLOAD < stringEnd) {
                        int rgb = ColorCodeUtils.parseHexPairs(string, charIdx + 1, 6);
                        if (rgb != -1) {
                            curRainbow = false;
                            curGradient = false;
                            curColor = (curColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                            curShadowColor = (curShadowColor & 0xFF000000) | ((rgb & 0xFCFCFC) >> 2);
                            charIdx += SECTION_X_PAYLOAD;
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
                        strikethroughStartX = curX - 1;
                        strikethroughEndX = strikethroughStartX;
                    } else if (fmtCode == 'n') {
                        curUnderline = true;
                        underlineStartX = curX - 1;
                        underlineEndX = underlineStartX;
                    } else if (fmtCode == 'o') {
                        curItalic = true;
                    } else if (fmtCode == 'q' && AngelicaConfig.enableRainbow) {
                        curRainbow = true;
                        curGradient = false;
                        rainbowCharIndex = 0;
                    } else if (fmtCode == 'z' && AngelicaConfig.enableWaveText) {
                        curWave = !curWave;
                    } else if (fmtCode == 'v' && AngelicaConfig.enableDinnerboneText) {
                        curDinnerbone = !curDinnerbone;
                    } else if (fmtCode == 'g' && AngelicaConfig.enableGradients && charIdx + GRADIENT_PAYLOAD < stringEnd) {
                        int color1 = ColorCodeUtils.parseSectionXAt(string, charIdx + 1);
                        int color2 = ColorCodeUtils.parseSectionXAt(string, charIdx + 1 + SECTION_X_LENGTH);
                        if (color1 != -1 && color2 != -1) {
                            curGradient = true;
                            curRainbow = false;
                            gradientStartRgb = color1;
                            gradientEndRgb = color2;
                            gradientCharIndex = 0;
                            gradientTotalChars = countVisibleChars(string, charIdx + 1 + GRADIENT_PAYLOAD, stringEnd);
                            gradientStep = gradientTotalChars > 1 ? 1f / (gradientTotalChars - 1) : 0f;
                            charIdx += GRADIENT_PAYLOAD;
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
                        curShader = false;
                    } else if (fmtCode == 'y') {
                        curShader = true;
                    }
                    } // close else block for non-§x codes

                    continue;
                }

                if (FontConfig.enableCustomFont && FontConfig.enableGlyphReplacements) {
                    final char replacement = GlyphReplacements.getReplacementGlyph(chr);
                    if (replacement != 0) {
                        if (FontProviderCustom.getPrimary().isGlyphAvailable(replacement)
                            || FontProviderCustom.getFallback().isGlyphAvailable(replacement)
                        ) {
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

                visibleCharIndex++;

                // Check ASCII space, NBSP, NNBSP
                if (chr == ' ' || chr == '\u00A0' || chr == '\u202F') {
                    curX += 4 * this.getWhitespaceScale() + (curBold ? 1 : 0);
                    continue;
                }

                if (curRainbow) {
                    int rgbEffect = RAINBOW_LUT[rainbowCharIndex % RAINBOW_LUT_SIZE];
                    curColor = (curColor & 0xFF000000) | rgbEffect;
                    curShadowColor = (curShadowColor & 0xFF000000) | ((rgbEffect & 0xFCFCFC) >> 2);
                    rainbowCharIndex++;
                }
                if (curGradient && gradientTotalChars > 0) {
                    float t = Math.min(gradientCharIndex * gradientStep, 1f);
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
                final float shadowOffset = fontProvider.getShadowOffset();
                final int shadowCopies = FontConfig.shadowCopies;
                final int boldCopies = FontConfig.boldCopies;
                final int texture;
                if (fontProvider instanceof FontProviderMC) {
                    texture = this.fontTexture;
                } else {
                    texture = fontProvider.getTexture(chr);
                }
                final int layer = chr / 256;
                if (fontProvider instanceof FontProviderUnicode) {
                    if (!mainTextureArray.hasLayer(layer)) {
                        mainTextureArray.addAtlas(
                            layer,
                            new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", layer))
                        );
                    }
                }


                // Wave: Y offset via sine wave
                float renderY = heightNorth;

//                if (curShader) {
//                    pushTexRect(
//                        curX,
//                        renderY,
//                        glyphW - 1.0f,
//                        heightSouth,
//                        itOff,
//                        0xFFFFFFFF, //0xFF000000,
//                        uStart, vStart, uSz, vSz,
//                        curDinnerbone,
//                        texture
//                    );
//                    final int idxId = idxWriterIndex;
//                    final int vtxCount = 4;
//                    pushShaderCmd(
//                        idxId,
//                        vtxCount / 2 * 3,
//                        texture,
//                        curX,
//                        renderY,
//                        glyphW - 1.0f,
//                        heightSouth
//                    );
//
//                    curX += (xAdvance + (curBold ? 1.0f : 0.0f)) + getGlyphSpacing();
//                    if (bookMode) { curX = (int) curX; }
//                    underlineEndX = curX;
//                    strikethroughEndX = curX;
//                    continue;
//                }

                if (curWave) {
                    float time = HUDCaching.renderingCacheOverride ? 0f : (float)((System.nanoTime() & 0xFFFFFFFFFFFFL) * WAVE_TIME_SCALE);
                    renderY += (float) Math.sin(visibleCharIndex * WAVE_FREQUENCY + time) * AngelicaConfig.waveAmplitude;
                }

                if (enableShadow) {
                    for (int n = 1; n <= shadowCopies; n++) {
                        final float shadowOffsetPart = shadowOffset * ((float) n / shadowCopies);
                        pushTexRect(
                            curX + shadowOffsetPart, renderY + shadowOffsetPart,
                            glyphW - 1, heightSouth,
                            uStart, vStart, uSz, vSz,
                            curShadowColor,
                            layer,
                            curItalic,
                            curDinnerbone,
                            texture
                        );

                        if (curBold) {
                            pushTexRect(
                                curX + 2 * shadowOffsetPart, renderY + shadowOffsetPart,
                                glyphW - 1, heightSouth,
                                uStart, vStart, uSz, vSz,
                                curShadowColor,
                                layer,
                                curItalic,
                                curDinnerbone,
                                texture
                            );
                        }
                    }
                }

                pushTexRect(
                    curX, renderY,
                    glyphW - 1, heightSouth,
                    uStart, vStart, uSz, vSz,
                    curColor,
                    layer,
                    curItalic,
                    curDinnerbone,
                    texture
                );

                if (curBold) {
                    for (int n = 1; n <= boldCopies; n++) {
                        final float shadowOffsetPart = shadowOffset * ((float) n / boldCopies);
                        pushTexRect(
                            curX + shadowOffsetPart, renderY,
                            glyphW - 1, heightSouth,
                            uStart, vStart, uSz, vSz,
                            curColor,
                            layer,
                            curItalic,
                            curDinnerbone,
                            texture
                        );
                    }
                }

                /*
                Vertex-per-char counts for different configurations
                    default:        4
                    shadow only:    4(1 + shadowCopies)
                    bold only:      4(1 + boldCopies)
                    both:           4(1 + 2 * shadowCopies + boldCopies)
                 */

                curX += (xAdvance + (curBold ? 1 : 0)) + getGlyphSpacing();
                underlineEndX = curX;
                strikethroughEndX = curX;

                if ((curRainbow || curGradient) && curUnderline && underlineStartX != underlineEndX) {
                    pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, glyphScaleY, curColor);
                    underlineStartX = underlineEndX;
                }
                if ((curRainbow || curGradient) && curStrikethrough && strikethroughStartX != strikethroughEndX) {
                    pushUntexRect(strikethroughStartX, strikethroughY, strikethroughEndX - strikethroughStartX, glyphScaleY, curColor);
                    strikethroughStartX = strikethroughEndX;
                }
            }

            if (curUnderline && underlineStartX != underlineEndX) {
                pushUntexRect(underlineStartX, underlineY, underlineEndX - underlineStartX, glyphScaleY, curColor);
            }
            if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                pushUntexRect(
                    strikethroughStartX,
                    strikethroughY,
                    strikethroughEndX - strikethroughStartX,
                    glyphScaleY,
                    curColor);
            }

        } finally {
            this.endBatch();
        }
        return (int) (curX + (enableShadow ? 1 : 0));
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

    // ---------- RENDERING ----------




    // Layout in data:
    // [v, v, t, t, c, c, c, c, tb, tb, tb, tb]
    // v, t and tb are floats, c is bytes; 37 bytes total
    private static final int INSTANCE_SIZE = 37;

    private static int vboCapacity;

    private int mainVAO = 0;

    //TODO add bindless textures



    private int batchDepth = 0;

//    private ByteBuffer data = memAlloc(INSTANCE_SIZE * 128);
//    private long writePtr = memAddress0(data);
    private final StreamingDrawer stream = StreamingDrawer.create(1024 * 400 * INSTANCE_SIZE);
    private int instanceCount;




    private int initVAO() {
        int vao = GLStateManager.glGenVertexArrays();

        GLStateManager.glBindVertexArray(vao);

        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, InstancedHelper.getQuadEBO());

        // position
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, InstancedHelper.getQuadVBO());

        GLStateManager.glVertexAttribPointer(
            0,
            2,
            GL11.GL_FLOAT,
            false,
            8,
            0
        );
        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glVertexAttribDivisor(0, 0);

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, stream.getVBO());

        // Glyph data (posX, posY, width, height)
        GLStateManager.glVertexAttribPointer(
            1,
            4,
            GL11.GL_FLOAT,
            false,
            INSTANCE_SIZE,
            0
        );
        GLStateManager.glEnableVertexAttribArray(1);
        GLStateManager.glVertexAttribDivisor(1, 1);

        // UV data (u, v, uWidth, vWidth)
        GLStateManager.glVertexAttribPointer(
            2,
            4,
            GL11.GL_FLOAT,
            false,
            INSTANCE_SIZE,
            16
        );
        GLStateManager.glEnableVertexAttribArray(2);
        GLStateManager.glVertexAttribDivisor(2, 1);


        // color
        GLStateManager.glVertexAttribPointer(
            3,
            4,
            GL11.GL_UNSIGNED_BYTE,
            true,
            INSTANCE_SIZE,
            32
        );
        GLStateManager.glEnableVertexAttribArray(3);
        GLStateManager.glVertexAttribDivisor(3, 1);

        // layer
        GLStateManager.glVertexAttribIPointer(
            4,
            1,
            GL11.GL_UNSIGNED_BYTE,
            INSTANCE_SIZE,
            36
        );
        GLStateManager.glEnableVertexAttribArray(4);
        GLStateManager.glVertexAttribDivisor(4, 1);

        return vao;
    }

    public void pushQuad(
        float x, float y, float width, float height,
        float uMin, float vMin, float uWidth, float vWidth,
        int rgba,
        int layer,
        boolean italic, boolean dinnerbone
    ) {
        final long ptr = stream.writeSection(INSTANCE_SIZE);

        // vertices
        memPutFloat(ptr, x);
        memPutFloat(ptr + 4, y);
        memPutFloat(ptr + 8, width);
        memPutFloat(ptr + 12, height);

//            memPutFloat(ptr, round(x));
//            memPutFloat(ptr + 4, round(y));
//            memPutFloat(ptr + 8, round(width));
//            memPutFloat(ptr + 12, round(height));
        // tb, tb, tb, tb
        memPutFloat(ptr + 16, uMin);
        memPutFloat(ptr + 20, vMin);
        memPutFloat(ptr + 24, uWidth);
        memPutFloat(ptr + 28, vWidth);

        // c, c, c, c
        // 0xAARRGGBB
        memPutByte(ptr + 32, (byte) ((rgba >> 16) & 0xFF));
        memPutByte(ptr + 33, (byte) ((rgba >> 8) & 0xFF));
        memPutByte(ptr + 34, (byte) (rgba & 0xFF));
        memPutByte(ptr + 35, (byte) ((rgba >> 24) & 0xFF));

        // layer
        memPutByte(ptr + 36, (byte) layer);

        //TODO italic + other shit

        //TODO add this to vertex shader
//            final float vTop;
//            final float vBot;
//            if (flipV) {
//                vTop = vStart + vSz;
//                vBot = vStart;
//            } else {
//                vTop = vStart;
//                vBot = vStart + vSz;
//            }

        instanceCount++;
    }

    /**
     * Starts a new batch of font rendering operations. Can be called from within another batch with a matching end, to
     * allow for easier optimizing of blocks of font rendering code.
     */
    public void beginBatch() {
        batchDepth++;
    }

    public void endBatch() {
        if (--batchDepth <= 0) {
            // We finished any nested batches
            flushBatch();
        }
    }

    private int fontAAStrengthLast = -1;

    private void flushBatch() {
        if (instanceCount == 0) return;

        if (mainVAO == 0) {
            mainVAO = initVAO();
        }

        GLStateManager.glBindVertexArray(mainVAO);

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, stream.getVBO());

        final int offset = stream.finishUploading() / INSTANCE_SIZE;

        //vboCapacity = StreamingUploader.upload(getReadBuffer(), vboCapacity);


        final int prevProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        final boolean isBlendEnabledBefore = GLStateManager.glIsEnabled(GL11.GL_BLEND);

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(blendSrcRGB, blendDstRGB, GL11.GL_ONE, GL11.GL_ZERO);

        GLStateManager.glUseProgram(fontShaderId);
        if (FontConfig.fontAAStrength != fontAAStrengthLast) {
            fontAAStrengthLast = FontConfig.fontAAStrength;
            GLStateManager.glUniform1f(AAStrength, FontConfig.fontAAStrength / 120.f);
        }
        GLStateManager.uploadMVPMatrix(mvpMatrixLocation);

        mainTextureArray.bind();
//        GL31.glDrawElementsInstanced(
//            GL11.GL_TRIANGLES,
//            6,
//            GL11.GL_UNSIGNED_SHORT,
//            0,
//            instanceCount
//        );
        GL42.glDrawElementsInstancedBaseInstance(
            GL11.GL_TRIANGLES,
            6,
            GL11.GL_UNSIGNED_SHORT,
            0,
            instanceCount,
            offset
        );
        mainTextureArray.unbind();


        GLStateManager.glUseProgram(prevProgram);

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);

        if (!isBlendEnabledBefore) {
            GLStateManager.disableBlend();
        }

        clearBatch();
    }

    private void clearBatch() {
        instanceCount = 0;
    }

}

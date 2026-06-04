package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.SimpleShaderDefine;
import com.gtnewhorizon.gtnhlib.util.font.GlyphReplacements;
import com.gtnewhorizons.angelica.client.font.atlas.FontProviderCustom;
import com.gtnewhorizons.angelica.client.font.atlas.FontProviderMinecraft;
import com.gtnewhorizons.angelica.client.font.atlas.FontProviderSGA;
import com.gtnewhorizons.angelica.client.font.atlas.FontProviderSplash;
import com.gtnewhorizons.angelica.client.font.atlas.FontTextureArray;
import com.gtnewhorizons.angelica.client.streaming.StreamingDrawer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import com.gtnewhorizons.angelica.utils.InstancedHelper;
import it.unimi.dsi.fastutil.chars.Char2ShortOpenHashMap;
import jss.util.RandomXoshiro256StarStar;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.awt.*;

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
    /**
     * Array of RGB triplets defining the 16 standard chat colors followed by 16 darker version of the same colors for
     * drop shadows.
     */
    private final int[] colorCode;

    private static int defaultFontShader;
    private static int defaultMatrixLocation;

    private static int multisampleFontShader;
    private static int multisampleMatrixLocation;

    private final boolean isSGA;
    private final boolean isSplash;
    private boolean forceDefaults;

    // Minecraft
    private static FontTextureArray minecraftTextureArray;
    private static FontTextureArray unicodeTextureArray; //TODO find a way to make it work normally

    // Custom Font
    public static FontTextureArray customTextureArray;

    private FontTextureArray overrideTextureArray; // Used for splash & SGA;

    // Splash (will get deleted)
    private static BatchingFontRenderer splashFontRenderer; //TODO this seems to sometimes get deleted prematurely?

    private final ResourceLocation locationFontTexture;

    public static int[] asciiCharWidths;
    public static int[] sgaCharWidths;
    public static byte[] glyphWidths;

    /** For use with modded books. Affects calculations and forces some defaults. */
    boolean bookMode = false;

    public void setBookMode(boolean bookMode) {
        this.bookMode = bookMode;
        this.forceDefaults = this.isSplash || this.isSGA || bookMode;
    }

    private static final int FLAG_ITALIC = 0x1;
    private static final int FLAG_DINNERBONE = 0x2;
    private static final int FLAG_SECOND_TEXTURE = 0x4;

    private static final RandomXoshiro256StarStar fontRandom = new RandomXoshiro256StarStar();

    public BatchingFontRenderer(FontRenderer underlying, int[] colorCode, ResourceLocation locationFontTexture) {

        this.underlying = underlying;
        this.colorCode = colorCode;

        this.isSGA = locationFontTexture.getResourcePath().equals("textures/font/ascii_sga.png");
        this.isSplash = FontStrategist.isSplashFontRendererActive(underlying);
        this.locationFontTexture = locationFontTexture;
        this.forceDefaults = this.isSplash || this.isSGA;

        if (isSplash) {
            splashFontRenderer = this;

            overrideTextureArray = FontProviderSplash.create();

            GLSMHooks.SPLASH_DESTROY.addListener(event -> {
                if (splashFontRenderer != null) {
                    splashFontRenderer.delete(); //TODO add texture array
                    splashFontRenderer = null;
                }
                stream.switchContext();
            });
        }

        if (defaultFontShader <= 0) {
            reloadShaders();
        }
        if (customTextureArray == null) {
            reloadCustomFonts();
        }
    }

    public static void reloadShaders() {
        if (defaultFontShader > 0) {
            GLStateManager.glDeleteProgram(defaultFontShader);
            GLStateManager.glDeleteProgram(multisampleFontShader);
        }
        final SimpleShaderDefine vshItalic = new SimpleShaderDefine("FLAG_ITALIC", FLAG_ITALIC + "u");
        final SimpleShaderDefine vshDinnerbone = new SimpleShaderDefine("FLAG_DINNERBONE", FLAG_DINNERBONE + "u");
        final SimpleShaderDefine vshSecondTexture = new SimpleShaderDefine("FLAG_SECOND_TEXTURE", FLAG_SECOND_TEXTURE + "u");

        final SimpleShaderDefine fshAAMode = new SimpleShaderDefine("AA_MODE", FontConfig.fontAAMode);
        final SimpleShaderDefine fshAAStrength = new SimpleShaderDefine("AA_STRENGTH", FontConfig.fontAAStrength / 120f);
        final SimpleShaderDefine fshBrightness = FontConfig.fontBrightness != 0
            ? new SimpleShaderDefine("FONT_BRIGHTNESS", (1 + (FontConfig.fontBrightness / 10f)) + "f")
            : null;

        final SimpleShaderDefine multisampling = new SimpleShaderDefine("MULTISAMPLING");

        final String vsh = ShaderProgram.loadUnmodifiedSource(BatchingFontRenderer.class.getResourceAsStream("/assets/angelica/shaders/font/font.vsh"));
        final String fsh = ShaderProgram.loadUnmodifiedSource(BatchingFontRenderer.class.getResourceAsStream("/assets/angelica/shaders/font/font.fsh"));


        //defaultFontShader = ShaderProgram.createProgram(vsh, fsh);
        defaultFontShader = ShaderProgram.createProgram(
            ShaderProgram.injectDefines(vsh, vshItalic, vshDinnerbone, vshSecondTexture),
            ShaderProgram.injectDefines(fsh, fshAAMode, fshAAStrength, fshBrightness)
        );
        defaultMatrixLocation = GLStateManager.glGetUniformLocation(defaultFontShader, "u_MVPMatrix");

        multisampleFontShader = ShaderProgram.createProgram(
            ShaderProgram.injectDefines(vsh, vshItalic, vshDinnerbone, vshSecondTexture, multisampling),
            ShaderProgram.injectDefines(fsh, fshAAMode, fshAAStrength, fshBrightness, multisampling)
        );
        multisampleMatrixLocation = GLStateManager.glGetUniformLocation(multisampleFontShader, "u_MVPMatrix");

        GLStateManager.glUseProgram(multisampleFontShader);
        //GLStateManager.glUniform1i(GLStateManager.glGetUniformLocation(multisampleFontShader, "sampler0"), 0);
        GLStateManager.glUniform1i(GLStateManager.glGetUniformLocation(multisampleFontShader, "sampler1"), 1);
        GLStateManager.glUseProgram(0);
    }

    public static void reloadCustomFonts() {
        if (customTextureArray != null) {
            customTextureArray.delete();
            customTextureArray = null;
        }

        if (!FontConfig.enableCustomFont) {
            return;
        }

        final Font[] fonts = FontStrategist.getConfigFonts();
        if (fonts != null) {
            customTextureArray = FontProviderCustom.createTextureArray(fonts);
        }

    }

    public int[] getCharWidths() {
        return ((FontRendererAccessor) underlying).angelica$getCharWidths();
    }

    public byte[] getGlyphWidths() {
        return ((FontRendererAccessor) underlying).angelica$getGlyphWidths();
    }

    public boolean forceUnicode() {
        return underlying.getUnicodeFlag() || this.bookMode;
    }

    public void initializeTextures() {
        ((FontRendererAccessor) underlying).angelica$bindTexture(locationFontTexture);

        glyphWidths = this.getGlyphWidths();

        if (this.isSGA) {
            sgaCharWidths = this.getCharWidths();
            overrideTextureArray = FontProviderSGA.create();
            return;
        }

        asciiCharWidths = this.getCharWidths();

        if (this.isSplash) {
            overrideTextureArray.getGlyphData((char) 0); // Load first texture from bound texture atlas
            return;
        }

        if (minecraftTextureArray != null) {
            minecraftTextureArray.delete();
        }
        minecraftTextureArray = FontProviderMinecraft.create();
    }

    private int blendSrcRGB = GL11.GL_SRC_ALPHA;
    private int blendDstRGB = GL11.GL_ONE_MINUS_SRC_ALPHA;

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

    public float getGlyphScaleX() {
        return forceDefaults ? 1 : (float) (FontConfig.glyphScale * Math.pow(2, FontConfig.glyphAspect));
    }

    public float getGlyphScaleY() {
        return forceDefaults ? 1 : (float) (FontConfig.glyphScale / Math.pow(2, FontConfig.glyphAspect));
    }

    public float getGlyphSpacing() {
        return forceDefaults ? 0 : FontConfig.glyphSpacing;
    }

    public float getWhitespaceScale() {
        return forceDefaults ? 1 : FontConfig.whitespaceScale;
    }

    public float getShadowOffset() {
        return forceDefaults ? 1 : FontConfig.fontShadowOffset;
    }

    private static final double WAVE_TIME_SCALE = 5e-9;
    private static final float WAVE_FREQUENCY = 0.5f;

    public int drawString(final String string, final int anchorX, final int anchorY,
                          final int color, final boolean enableShadow) {
        // noinspection SizeReplaceableByIsEmpty
        if (string == null || string.length() == 0) {
            return anchorX + (enableShadow ? 1 : 0);
        }
        final int shadowColor = (color & 0xfcfcfc) >> 2 | color & 0xff000000;


        this.beginBatch();
        float curX = anchorX;
        final int stringEnd = string.length();

        int curColor = color;
        boolean unicodeFlag = this.forceUnicode();
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
        FontOverlayShader curShader = null;
        boolean curShadow = false;
        boolean curShadowCustomColor = false;
        int curShadowColorOverride = 0;
        int gradientStartRgb = 0, gradientEndRgb = 0;
        int gradientCharIndex = 0, gradientTotalChars = 0;
        float gradientStep = 0f;
        int rainbowCharIndex = 0;
        int visibleCharIndex = 0;

        final float glyphScaleY = getGlyphScaleY();
        final float glyphScaleX = getGlyphScaleX();
        final float glyphSpacing = getGlyphSpacing();
        float heightNorth = anchorY + (underlying.FONT_HEIGHT - 1.0f) * (0.5f - glyphScaleY / 2);

        final float underlineY = heightNorth + (underlying.FONT_HEIGHT - 1.0f) * glyphScaleY;
        float underlineStartX = 0.0f;
        float underlineEndX = 0.0f;

        final float strikethroughY = heightNorth + ((float) (underlying.FONT_HEIGHT / 2) - 1.0f) * glyphScaleY;
        float strikethroughStartX = 0.0f;
        float strikethroughEndX = 0.0f;

        final int boldCopies = FontConfig.boldCopies;


        for (int charIdx = 0; charIdx < stringEnd; charIdx++) {
            char chr = string.charAt(charIdx);

            // COLOR CODE BLOCK START
            if (chr == FORMATTING_CHAR && (charIdx + 1) < stringEnd) {
                final char fmtCode = Character.toLowerCase(string.charAt(charIdx + 1));
                charIdx++;

                if (curUnderline && underlineStartX != underlineEndX) {
                    pushQuad(
                        underlineStartX, underlineY,
                        underlineEndX - underlineStartX, glyphScaleY,
                        curColor,
                        0,
                        curItalic, curDinnerbone
                    );
                    underlineStartX = underlineEndX;
                }
                if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
                    pushQuad(
                        strikethroughStartX, strikethroughY,
                        strikethroughEndX - strikethroughStartX, glyphScaleY,
                        curColor,
                        0,
                        curItalic, curDinnerbone
                    );
                    strikethroughStartX = strikethroughEndX;
                }

                final boolean is09 = charInRange(fmtCode, '0', '9');
                if (is09 || charInRange(fmtCode, 'a', 'f')) {
                    curRandom = false;
                    curBold = false;
                    curStrikethrough = false;
                    curUnderline = false;
                    curItalic = false;
                    curRainbow = false;
                    curGradient = false;
                    // wave/dinnerbone does NOT get reset — they're positional effects, independent of color

                    final int colorIdx = is09 ? (fmtCode - '0') : (fmtCode - 'a' + 10);
                    final int rgb = this.colorCode[colorIdx];
                    curColor = (curColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                    final int shadowRgb = this.colorCode[colorIdx + 16];
                    curShadowColor = (curShadowColor & 0xFF000000) | (shadowRgb & 0x00FFFFFF);
                    continue;
                }

                switch (fmtCode) {
                    case 'x' -> {
                        if (AngelicaConfig.enableRGBColors && charIdx + SECTION_X_PAYLOAD < stringEnd) {
                            int rgb = ColorCodeUtils.parseHexPairs(string, charIdx + 1, 6);
                            if (rgb != -1) {
                                curRainbow = false;
                                curGradient = false;
                                curColor = (curColor & 0xFF000000) | (rgb & 0x00FFFFFF);
                                curShadowColor = (curShadowColor & 0xFF000000) | ((rgb & 0xFCFCFC) >> 2);
                                charIdx += SECTION_X_PAYLOAD;
                            }
                        }
                    }
                    case 'k' -> {
                        curRandom = true;
                    }
                    case 'l' -> {
                        curBold = true;
                    }
                    case 'm' -> {
                        curStrikethrough = true;
                        strikethroughStartX = curX - 1;
                        strikethroughEndX = strikethroughStartX;
                    }
                    case 'n' -> {
                        curUnderline = true;
                        underlineStartX = curX - 1;
                        underlineEndX = underlineStartX;
                    }
                    case 'o' -> {
                        curItalic = true;
                    }
                    case 'q' -> {
                        if (AngelicaConfig.enableRainbow) {
                            curRainbow = true;
                            curGradient = false;
                            rainbowCharIndex = 0;
                        }
                    }
                    case 'z' -> {
                        if (AngelicaConfig.enableWaveText) {
                            curWave = !curWave;
                        }
                    }
                    case 'v' -> {
                        if (AngelicaConfig.enableDinnerboneText) {
                            curDinnerbone = !curDinnerbone;
                        }
                    }
                    case 'u' -> {
                        int customRgb = (charIdx + SECTION_X_LENGTH < stringEnd)
                            ? ColorCodeUtils.parseSectionXAt(string, charIdx + 1)
                            : -1;
                        if (customRgb != -1) {
                            curShadow = true;
                            curShadowCustomColor = true;
                            curShadowColorOverride = customRgb;
                            charIdx += SECTION_X_LENGTH;
                        } else {
                            curShadow = !curShadow;
                            if (!curShadow) curShadowCustomColor = false;
                        }
                    }
                    case 'g' -> {
                        if (AngelicaConfig.enableGradients && charIdx + GRADIENT_PAYLOAD < stringEnd) {
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
                        }
                    }
                    case 'r' -> {
                        curRandom = false;
                        curBold = false;
                        curStrikethrough = false;
                        curUnderline = false;
                        curItalic = false;
                        curRainbow = false;
                        curWave = false;
                        curDinnerbone = false;
                        curGradient = false;
                        curShadow = false;
                        curShadowCustomColor = false;
                        curColor = color;
                        curShadowColor = shadowColor;
                        if (curShader != null) {
                            curShader = curShader.end(this);
                        }
                    }
                    default -> {
                        if (FontShaderManager.isOverlayShader(fmtCode) || fmtCode == 'y') { //TODO remove y test
                            if (curShader == null) { //TODO shadermanager integration
                                curShader = FontOverlayShader.TEMPLATE.begin(this);
                            }
                        }
                    }
                }
                continue;
            }
            // COLOR CODE BLOCK END

            float shadowOffset = FontConfig.fontShadowOffset;
            float yScaleMultiplier = 1;
            int flags = (curItalic ? FLAG_ITALIC : 0) | (curDinnerbone ? FLAG_DINNERBONE : 0);


            if (chr == ColorCodeUtils.ESCAPED_AMPERSAND) { chr = '&'; }

            final FontTextureArray fontProvider;
            if (this.overrideTextureArray != null) {
                fontProvider = overrideTextureArray;
            }
            else if (this.forceUnicode()) { //TODO make this override texture array
                fontProvider = unicodeTextureArray;
                shadowOffset *= FontConfig.fontShadowOffsetUC;
            }
            else if (customTextureArray != null) {
                final char replacement = GlyphReplacements.getReplacementGlyph(chr);
                if (replacement != 0) {
                    if (FontConfig.enableGlyphReplacements && customTextureArray.isGlyphAvailable(replacement)) {
                        chr = replacement;
                    }
                }
                if (customTextureArray.isGlyphAvailable(chr)) {
                    fontProvider = customTextureArray;
                    yScaleMultiplier = FontConfig.customFontScale;
                    flags |= FLAG_SECOND_TEXTURE;
                    requiresMultisampling = true;
                } else {
                    fontProvider = minecraftTextureArray;
                }
            }
            else {
                fontProvider = minecraftTextureArray;
            }

            if (curRandom) {
                chr = getRandomReplacement(chr, this.isSGA);
            }

            heightNorth = anchorY + (underlying.FONT_HEIGHT - 1.0f) * (0.5f - glyphScaleY * yScaleMultiplier / 2);
            float heightSouth = (underlying.FONT_HEIGHT - 1.0f) * glyphScaleY * yScaleMultiplier;

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
                int gr = (int) ((gradientStartRgb >> 16 & 0xFF) * (1 - t) + (gradientEndRgb >> 16 & 0xFF) * t);
                int gg = (int) ((gradientStartRgb >> 8 & 0xFF) * (1 - t) + (gradientEndRgb >> 8 & 0xFF) * t);
                int gb = (int) ((gradientStartRgb & 0xFF) * (1 - t) + (gradientEndRgb & 0xFF) * t);
                int rgbEffect = (gr << 16) | (gg << 8) | gb;
                curColor = (curColor & 0xFF000000) | rgbEffect;
                curShadowColor = (curShadowColor & 0xFF000000) | ((rgbEffect & 0xFCFCFC) >> 2);
                gradientCharIndex++;
            }

            final GlyphData data = fontProvider.getGlyphData(chr);
            final float uStart = data.uStart;
            final float vStart = data.vStart;
            final float xAdvance = data.xAdvance * glyphScaleX;
            final float glyphW = data.glyphW * glyphScaleX; //TODO -1
            final float uSize = data.uSize;
            final float vSize = data.vSize;
            final int layer = fontProvider.getDepth(chr);


            // Wave: Y offset via sine wave
            float renderY = heightNorth;

            if (curWave) {
                float time = HUDCaching.renderingCacheOverride ? 0f : (float) ((System.nanoTime() & 0xFFFFFFFFFFFFL) * WAVE_TIME_SCALE);
                renderY += (float) Math.sin(visibleCharIndex * WAVE_FREQUENCY + time) * AngelicaConfig.waveAmplitude;
            }

            if (curShader != null) {
                curShader.updateBounds(curX, renderY, glyphW - 1.0f, heightSouth);
                curX += (xAdvance + (curBold ? 1 : 0)) + glyphSpacing; //TODO unify
                underlineEndX = curX;
                strikethroughEndX = curX;
                continue;
            }

            final boolean drawShadow = enableShadow || curShadow;
            if (drawShadow) {
                final int effectiveShadowColor = curShadowCustomColor
                    ? ((curColor & 0xFF000000) | (curShadowColorOverride & 0x00FFFFFF))
                    : curShadowColor;
                pushQuad(
                    curX + shadowOffset, renderY + shadowOffset,
                    glyphW - 1, heightSouth,
                    uStart, vStart, uSize, vSize,
                    effectiveShadowColor,
                    layer,
                    flags
                );

                if (curBold) {
                    pushQuad(
                        curX + 2 * shadowOffset, renderY + shadowOffset,
                        glyphW - 1, heightSouth,
                        uStart, vStart, uSize, vSize,
                        curShadowColor,
                        layer,
                        flags
                    );
                }
            }

            pushQuad(
                curX, renderY,
                glyphW - 1, heightSouth,
                uStart, vStart, uSize, vSize,
                curColor,
                layer,
                flags
            );

            if (curBold) {
                for (int n = 1; n <= boldCopies; n++) {
                    final float boldOffsetPart = (float) n / boldCopies;
                    pushQuad(
                        curX + boldOffsetPart, renderY,
                        glyphW - 1, heightSouth,
                        uStart, vStart, uSize, vSize,
                        curColor,
                        layer,
                        flags
                    );
                }
            }
            curX += (xAdvance + (curBold ? 1 : 0)) + glyphSpacing;
            underlineEndX = curX;
            strikethroughEndX = curX;

            if ((curRainbow || curGradient) && curUnderline && underlineStartX != underlineEndX) {
                pushQuad(
                    underlineStartX, underlineY,
                    underlineEndX - underlineStartX, glyphScaleY,
                    curColor,
                    layer,
                    curItalic, curDinnerbone
                );
                underlineStartX = underlineEndX;
            }
            if ((curRainbow || curGradient) && curStrikethrough && strikethroughStartX != strikethroughEndX) {
                pushQuad(
                    strikethroughStartX, strikethroughY,
                    strikethroughEndX - strikethroughStartX, glyphScaleY,
                    curColor,
                    layer,
                    curItalic, curDinnerbone
                );
                strikethroughStartX = strikethroughEndX;
            }
        }

        if (curUnderline && underlineStartX != underlineEndX) {
            pushQuad(
                underlineStartX, underlineY,
                underlineEndX - underlineStartX, glyphScaleY,
                curColor,
                0,
                curItalic, curDinnerbone
            );
        }
        if (curStrikethrough && strikethroughStartX != strikethroughEndX) {
            pushQuad(
                strikethroughStartX, strikethroughY,
                strikethroughEndX - strikethroughStartX, glyphScaleY,
                curColor,
                0,
                curItalic, curDinnerbone
            );
        }

        if (curShader != null) {
            curShader.end(this);
        }
        this.endBatch();
        return (int) (curX + (enableShadow ? 1 : 0));
    }

    private static char getRandomReplacement(char chr, boolean isSGA) {
        int lutIndex = lookupMcFontPosition(chr);
        if (lutIndex != 0) {
            int randomReplacementIndex;
            final int[] charWidth = isSGA ? sgaCharWidths : asciiCharWidths;
            do {
                randomReplacementIndex = fontRandom.nextInt(charWidth.length);
            } while (charWidth[lutIndex] != charWidth[randomReplacementIndex]);

            lutIndex = randomReplacementIndex;
            return MCFONT_CHARS.charAt(lutIndex);
        }
        return chr;
    }

    private FontTextureArray getFontProvider(char chr) {
        if (this.overrideTextureArray != null) {
            return overrideTextureArray;
        }
        if (this.forceUnicode()) {
            return unicodeTextureArray;
        }
        if (customTextureArray != null && customTextureArray.isGlyphAvailable(chr)) {
            return customTextureArray; //TODO fix this not working with no fonts selected
        }
        return minecraftTextureArray;
    }


    private static final Char2ShortOpenHashMap MCFONT_ASCII_MAP = new Char2ShortOpenHashMap();

    /**
     * The full list of characters present in the default Minecraft font, excluding the Unicode font
     */
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final String MCFONT_CHARS = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u2302\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000";


    static {
        MCFONT_ASCII_MAP.defaultReturnValue((short) 0);
        for (short i = 0; i < MCFONT_CHARS.length(); i++) {
            MCFONT_ASCII_MAP.put(MCFONT_CHARS.charAt(i), i);
        }
    }

    public static int lookupMcFontPosition(char ch) {
        return MCFONT_ASCII_MAP.get(ch);
    }

    public static boolean isGlyphAvailable(char chr) {
        return MCFONT_ASCII_MAP.containsKey(chr);
    }

    public float getCharWidthFine(char chr) {
        if (chr == ColorCodeUtils.ESCAPED_AMPERSAND) { chr = '&'; }
        if (chr == FORMATTING_CHAR) { return -1; }

        if (chr == ' ' || chr == '\u00A0' || chr == '\u202F') {
            return 4 * this.getWhitespaceScale();
        }

        final FontTextureArray fp = getFontProvider(chr);
        //FontProvider fp = FontStrategist.getFontProvider(this, chr, FontConfig.enableCustomFont, underlying.getUnicodeFlag());

        return fp.getGlyphData(chr).xAdvance * this.getGlyphScaleX();
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


    private static final int INSTANCE_SIZE = 38;

    private int batchDepth = 0;

    //TODO make it work with any initial capacity & fix resize
    private static final StreamingDrawer stream = StreamingDrawer.create(
        INSTANCE_SIZE, 256, BatchingFontRenderer::initVAO
    );

    private boolean requiresMultisampling;

    private static int initVAO(int vao, int vbo) {
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

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

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

        // flags
        GLStateManager.glVertexAttribIPointer(
            5,
            1,
            GL11.GL_UNSIGNED_BYTE,
            INSTANCE_SIZE,
            37
        );
        GLStateManager.glEnableVertexAttribArray(5);
        GLStateManager.glVertexAttribDivisor(5, 1);

        return INSTANCE_SIZE;
    }

    public void pushQuad(
        float x, float y, float width, float height,
        int rgba,
        int layer,
        boolean italic, boolean dinnerbone
    ) {
        pushQuad(
            x, y, width, height,
            -1, -1, 0, 0,
            rgba,
            layer,
            (italic ? FLAG_ITALIC : 0) | (dinnerbone ? FLAG_DINNERBONE : 0)
        );
    }

    public void pushQuad(
        float x, float y, float width, float height,
        float uMin, float vMin, float uWidth, float vWidth,
        int rgba,
        int layer,
        int flags
    ) {
        final long ptr = stream.writeSection(INSTANCE_SIZE);

        // vertices
        memPutFloat(ptr, x);
        memPutFloat(ptr + 4, y);
        memPutFloat(ptr + 8, width);
        memPutFloat(ptr + 12, height);

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
        memPutByte(ptr + 35, (byte) ((rgba >> 24)));

        // layer
        memPutByte(ptr + 36, (byte) layer);
        memPutByte(ptr + 37, (byte) flags);
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

    protected void flushBatch() {
        if (stream.isEmpty()) return;

        GLStateManager.glBindVertexArray(stream.getVAO());

        final int prevProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        //final boolean isBlendEnabledBefore = GLStateManager.glIsEnabled(GL11.GL_BLEND);

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(blendSrcRGB, blendDstRGB, GL11.GL_ONE, GL11.GL_ZERO);

        //TODO cache mvp
        if (requiresMultisampling) {
            GLStateManager.glUseProgram(multisampleFontShader);
            GLStateManager.uploadMVPMatrix(multisampleMatrixLocation);

            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            customTextureArray.bind();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        } else {
            GLStateManager.glUseProgram(defaultFontShader);
            GLStateManager.uploadMVPMatrix(defaultMatrixLocation);
        }

        (this.overrideTextureArray != null ? overrideTextureArray : minecraftTextureArray).bind();
//        GL31.glDrawElementsInstanced( TODO make this the fallback
//            GL11.GL_TRIANGLES,
//            6,
//            GL11.GL_UNSIGNED_SHORT,
//            0,
//            instanceCount
//        );

        stream.drawElementsInstanced(
            GL11.GL_TRIANGLES,
            6,
            GL11.GL_UNSIGNED_SHORT,
            0
        );

        GLStateManager.glUseProgram(prevProgram);

        GLStateManager.glBindVertexArray(0);

//        if (!isBlendEnabledBefore) {
//            GLStateManager.disableBlend();
//        }

        clearBatch();
    }

    private void clearBatch() {
        requiresMultisampling = false;
    }

    private void delete() {
        if (overrideTextureArray != null) {
            overrideTextureArray.delete();
            overrideTextureArray = null;
        }
    }

}

package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.shader.ShaderProgram;
import com.gtnewhorizon.gtnhlib.client.renderer.shader.SimpleShaderDefine;
import com.gtnewhorizon.gtnhlib.util.font.GlyphReplacements;
import com.gtnewhorizons.angelica.client.font.atlas.AtlasProviderCustom;
import com.gtnewhorizons.angelica.client.font.atlas.AtlasProviderDefault;
import com.gtnewhorizons.angelica.client.font.atlas.AtlasProviderSGA;
import com.gtnewhorizons.angelica.client.font.atlas.AtlasProviderSplash;
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
import lombok.Setter;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL42;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

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

    final boolean isSGA;
    final boolean isSplash;

    // Minecraft
    private static FontTextureArray defaultTextureArray;
    private static FontTextureArray unicodeTextureArray;

    // SGA
    private static FontTextureArray sgaTextureArray;

    // Custom Font
    public static FontTextureArray primaryTextureArray;
//    public static FontTextureArray secondaryTextureArray;

    // Splash (will get deleted)
    private static BatchingFontRenderer splashFontRenderer;
    private static FontTextureArray splashTextureArray;

    private final ResourceLocation locationFontTexture;

    public static int[] asciiCharWidths;
    public static int[] sgaCharWidths;
    public static byte[] glyphWidths;

    /** For use with modded books. Affects calculations and forces some defaults. */
    @Setter
    boolean bookMode = false;

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

        if (isSplash) {
            splashFontRenderer = this;

            splashTextureArray = new FontTextureArray(
                256, 1, new int[]{0}, GL11.GL_NEAREST, new AtlasProviderSplash()
            );

            GLSMHooks.SPLASH_DESTROY.addListener(event -> {
                if (splashTextureArray != null) {
                    splashTextureArray.delete();
                    splashTextureArray = null;
                }
                if (splashFontRenderer != null) {
                    splashFontRenderer.delete();
                    splashFontRenderer = null;
                } //TODO verify GC removal
            });
        }

        if (defaultFontShader <= 0) {
            recompileShaders();
        }
        initializeCustomFonts();
    }

    private void recompileShaders() {
        final ShaderProgram defaultProgram = recompileShader(false);

        //defaultFontShader = ShaderProgram.createProgram(vsh, fsh);
        defaultFontShader = defaultProgram.getProgram();
        defaultMatrixLocation = GLStateManager.glGetUniformLocation(defaultFontShader, "u_MVPMatrix");

        final ShaderProgram multisampleProgram = recompileShader(true);
        multisampleFontShader = multisampleProgram.getProgram();
        multisampleMatrixLocation = GLStateManager.glGetUniformLocation(multisampleFontShader, "u_MVPMatrix");
        multisampleProgram.use();
        multisampleProgram.bindTextureSlots("sampler0", "sampler1");
        GL20.glUseProgram(0); //TODO where did clear go?
    }

    private ShaderProgram recompileShader(boolean multisampling) {
        final SimpleShaderDefine define = multisampling
            ? new SimpleShaderDefine("MULTISAMPLING", "")
            : new SimpleShaderDefine("PLACEHOLDER", ""); //TODO
        final String vsh = ShaderProgram.loadShaderSource(
            BatchingFontRenderer.class.getResourceAsStream("/assets/angelica/shaders/font/font.vsh"),
            new SimpleShaderDefine("FLAG_ITALIC", FLAG_ITALIC + "u"),
            new SimpleShaderDefine("FLAG_DINNERBONE", FLAG_DINNERBONE + "u"),
            new SimpleShaderDefine("FLAG_SECOND_TEXTURE", FLAG_SECOND_TEXTURE + "u"),
            define
        );
        final String fsh = ShaderProgram.loadShaderSource(
            BatchingFontRenderer.class.getResourceAsStream("/assets/angelica/shaders/font/font.fsh"),
            new SimpleShaderDefine("AA_MODE", FontConfig.fontAAMode),
            new SimpleShaderDefine("AA_STRENGTH", 7 / 120f),
            define
        );

        return new ShaderProgram(vsh, fsh);
//        if (!isSplash) {
//            AutoShaderUpdater.getInstance().registerShaderReload(program,
//                new ResourceLocation("angelica", "shaders/font/font.vsh"),
//                new ResourceLocation("angelica", "shaders/font/font.fsh"),
//                new IShaderReloadRunnable() {
//                    @Override
//                    public void run(ShaderProgram shaderProgram) {
//                        defaultFontShader = shaderProgram.getProgram();
//                        mvpMatrixLocation = GLStateManager.glGetUniformLocation(defaultFontShader, "u_MVPMatrix");
//                    }
//
//                    @Override
//                    public IShaderDefinesInjector[] getDefines() {
//                        return new IShaderDefinesInjector[]{
//                            new SimpleShaderDefine("AA_MODE", FontConfig.fontAAMode),
//                            new SimpleShaderDefine("FLAG_ITALIC", FLAG_ITALIC + "u"),
//                            new SimpleShaderDefine("FLAG_DINNERBONE", FLAG_DINNERBONE + "u")
//                        };
//                    }
//                }
//            );
//        }
    }

    public static void initializeCustomFonts() {
        if (primaryTextureArray == null && FontStrategist.primaryFont != null) {
            System.out.println("Creating primary texture array. Font: " + FontStrategist.primaryFont.getFontName());
            primaryTextureArray = createTextureArray(FontStrategist.primaryFont);
        }
//        if (secondaryTextureArray == null && FontStrategist.secondaryFont != null) {
//            System.out.println("Creating secondary texture array. Font: " + FontStrategist.secondaryFont.getFontName());
//
//            secondaryTextureArray = createTextureArray(FontStrategist.secondaryFont);
//        }
    }


    public static FontTextureArray createTextureArray(final Font font) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setFont(font);
        FontMetrics fontMetrics = g2d.getFontMetrics();
        g2d.dispose();

        int[] layerLookup = new int[256];
        int layer = 0;
        loop:
        for (int atlasId = 0; atlasId < 256; atlasId++) {
            for (int i = 0; i < 256; i++) {
                final char ch = (char) (i + atlasId * 256);
                if (font.canDisplay(ch)) {
                    layerLookup[atlasId] = layer;
                    layer++;
                    continue loop;
                }
                // Can't display any chars, skip.
                layerLookup[atlasId] = -1;
            }
        }

        final int size = getAtlasSize(font, fontMetrics, 2);
        System.out.println("Font Atlas size: " + size);
        System.out.println("Amount layers: " + layer);
        System.out.println("Layers indices: " + Arrays.toString(layerLookup));

        return new FontTextureArray(
            size,
            layer,
            layerLookup,
            GL11.GL_LINEAR,
            new AtlasProviderCustom(font)
        );
    }


    private static int getAtlasSize(Font font, FontMetrics fontMetrics, int separator) {
        int size = 256;

        loop:
        while (size <= 2048) {
            for (int atlasId = 0; atlasId < 256; atlasId++) {
                final int lineHeight = fontMetrics.getHeight();
                final int charHeight = (lineHeight + separator);
                int imgX = 0;
                int imgY = 0; //TODO idk
                for (int i = 0; i < 256; i++) {
                    final char ch = (char) (i + 256 * atlasId);
                    if (!font.canDisplay(ch)) continue;

                    final int charWidth = fontMetrics.charWidth(ch);

                    if (imgX + charWidth >= size) {
                        imgX = 0;
                        imgY += charHeight;
                    }
                    imgX += (charWidth + separator);

                }
                //if (imgX == 0 && imgY == 0) return 0; TODO integrate this somehow
                // Total used height includes final row
                int usedHeight = imgY + charHeight;

                if (usedHeight > size) {
                    size *= 2;
                    continue loop;
                }
            }
            return size;
        }

        System.out.println("Size ended up being over 2048. This is a bug!");
        return size;
    }

    public int[] getCharWidths() {
        return ((FontRendererAccessor) underlying).angelica$getCharWidths();
    }

    public byte[] getGlyphWidths() {
        return ((FontRendererAccessor) underlying).angelica$getGlyphWidths();
    }

    public void initializeTextures() {
        ((FontRendererAccessor) underlying).angelica$bindTexture(locationFontTexture);

        glyphWidths = this.getGlyphWidths();

        if (this.isSGA) {
            sgaCharWidths = this.getCharWidths();
            sgaTextureArray = new FontTextureArray(
                256, 1, new int[]{0}, GL11.GL_NEAREST, new AtlasProviderSGA()
            );
            sgaTextureArray.getGlyphData((char) 0); // Load first texture from bound texture atlas
            return;
        }

        asciiCharWidths = this.getCharWidths();

        if (this.isSplash) {
            splashTextureArray.getGlyphData((char) 0); // Load first texture from bound texture atlas
            return;
        }

        int layer = 0;
        final int[] layersLookupArray = new int[256];
        for (int i = 0; i < 256; i++) {
//                try {
//                    //Minecraft.getMinecraft().getResourceManager().getResource(getUnicodePage(i));
//                } catch (Exception ignored) {
//                    System.out.println("Could not find Layer " + i + ".");
//                }
            layersLookupArray[i] = layer;
            layer++;
        }
        if (defaultTextureArray != null) {
            defaultTextureArray.delete();
        }
        defaultTextureArray = new FontTextureArray(
            256, layer, layersLookupArray, GL11.GL_NEAREST, new AtlasProviderDefault()
        );
        defaultTextureArray.getGlyphData((char) 0); // Load first texture from bound texture atlas
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
                        // wave/dinnerbone NOT reset — they're positional effects, independent of color

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
                            curColor = color;
                            curShadowColor = shadowColor;
                            curShader = false;
                        }
                        case 'y' -> {
                            curShader = true; //TODO extend this functionality
                        }
                    }
                    continue;
                }
                // COLOR CODE BLOCK END

                if (FontConfig.enableCustomFont && FontConfig.enableGlyphReplacements) {
                    final char replacement = GlyphReplacements.getReplacementGlyph(chr);
                    if (replacement != 0) {
//                        if (FontProviderCustom.getPrimary().isGlyphAvailable(replacement)
//                            || FontProviderCustom.getFallback().isGlyphAvailable(replacement)
//                        ) {
//                            chr = replacement;
//                        } TODO
                    }
                }

                if (curRandom) {
                    chr = getRandomReplacement(chr, this.isSGA);
                }

                //final FontProvider fontProvider = FontStrategist.getFontProvider(this, chr, FontConfig.enableCustomFont, unicodeFlag);
                //final FontTextureArray fontProvider = getFontProvider(chr, FontConfig.enableCustomFont, unicodeFlag);

                final FontTextureArray fontArray = getFontProvider(chr, FontConfig.enableCustomFont, unicodeFlag);
                final boolean isCustomFont = fontArray == primaryTextureArray;
                if (isCustomFont) {
                    requiresMultisampling = true;
                }
                final float yScaleMultiplier = isCustomFont ? FontConfig.customFontScale : 1; //TODO make it not scuffed

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
                    int gr = (int)((gradientStartRgb >> 16 & 0xFF) * (1-t) + (gradientEndRgb >> 16 & 0xFF) * t);
                    int gg = (int)((gradientStartRgb >> 8 & 0xFF) * (1-t) + (gradientEndRgb >> 8 & 0xFF) * t);
                    int gb = (int)((gradientStartRgb & 0xFF) * (1-t) + (gradientEndRgb & 0xFF) * t);
                    int rgbEffect = (gr << 16) | (gg << 8) | gb;
                    curColor = (curColor & 0xFF000000) | rgbEffect;
                    curShadowColor = (curShadowColor & 0xFF000000) | ((rgbEffect & 0xFCFCFC) >> 2);
                    gradientCharIndex++;
                }

                //((FontRendererAccessor) underlying).angelica$bindTexture(locationFontTexture);

                final GlyphData data = fontArray.getGlyphData(chr);
                final float uStart = data.uStart;
                final float vStart = data.vStart;
                final float xAdvance = data.xAdvance * glyphScaleX;
                final float glyphW = data.glyphW * glyphScaleX;
                final float uSize = data.uSize;
                final float vSize = data.vSize;
//                final float uStart = fontProvider.getUStart(chr);
//                final float vStart = fontProvider.getVStart(chr);
//                final float xAdvance = fontProvider.getXAdvance(chr) * glyphScaleX;
//                final float glyphW = fontProvider.getGlyphW(chr) * glyphScaleX;
//                final float uSize = fontProvider.getUSize(chr);
//                final float vSize = fontProvider.getVSize(chr);
//                final float shadowOffset = fontProvider.getShadowOffset();
                final float shadowOffset = FontConfig.fontShadowOffset;
                final int shadowCopies = FontConfig.shadowCopies;
                final int boldCopies = FontConfig.boldCopies;
                final int layer = fontArray.getDepth(chr);
//                if (fontProvider instanceof FontProviderUnicode) { //TODO
//                    if (!mainTextureArray.hasLayer(layer)) {
//                        mainTextureArray.addAtlas(layer, FontTextureArray.getUnicodePage(layer));
//                    }
//                }


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
                        pushQuad(
                            curX + shadowOffsetPart, renderY + shadowOffsetPart,
                            glyphW - 1, heightSouth,
                            uStart, vStart, uSize, vSize,
                            curShadowColor,
                            layer,
                            curItalic,
                            curDinnerbone,
                            isCustomFont
                        );

                        if (curBold) {
                            pushQuad(
                                curX + 2 * shadowOffsetPart, renderY + shadowOffsetPart,
                                glyphW - 1, heightSouth,
                                uStart, vStart, uSize, vSize,
                                curShadowColor,
                                layer,
                                curItalic,
                                curDinnerbone,
                                isCustomFont
                            );
                        }
                    }
                }

                pushQuad(
                    curX, renderY,
                    glyphW - 1, heightSouth,
                    uStart, vStart, uSize, vSize,
                    curColor,
                    layer,
                    curItalic,
                    curDinnerbone,
                    isCustomFont
                );

                if (curBold) {
                    for (int n = 1; n <= boldCopies; n++) {
                        final float shadowOffsetPart = shadowOffset * ((float) n / boldCopies);
                        pushQuad(
                            curX + shadowOffsetPart, renderY,
                            glyphW - 1, heightSouth,
                            uStart, vStart, uSize, vSize,
                            curColor,
                            layer,
                            curItalic,
                            curDinnerbone,
                            isCustomFont
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

        } finally {
            this.endBatch();
        }
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

    private FontTextureArray getFontProvider(char chr, boolean customFontEnabled, boolean forceUnicode) {
        if (this.isSGA) {
            return sgaTextureArray;
            //return FontProviderMC.getSGA();
        }
        if (this.bookMode) {
            //return FontProviderUnicode.get();

        }
        if (customFontEnabled && !this.isSplash) {
//            FontProvider fp;
//            fp = FontProviderCustom.getPrimary();
//            if (fp.isGlyphAvailable(chr)) { return fp; }
//            fp = FontProviderCustom.getFallback();
//            if (fp.isGlyphAvailable(chr)) { return fp; }
//            return FontProviderUnicode.get();
            if (primaryTextureArray != null && primaryTextureArray.isGlyphAvailable(chr)) return primaryTextureArray;
            //if (secondaryTextureArray != null && secondaryTextureArray.isGlyphAvailable(chr)) return secondaryTextureArray;
            return defaultTextureArray;
        } else {
//            if (!forceUnicode && FontProviderMC.getDefault().isGlyphAvailable(chr)) {
//                return FontProviderMC.getDefault();
//            } else {
//                return FontProviderUnicode.get();
//            }
            return this.isSplash ? splashTextureArray : defaultTextureArray;
        }
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
        if (chr == FORMATTING_CHAR) { return -1; }

        if (chr == ' ' || chr == '\u00A0' || chr == '\u202F') {
            return 4 * this.getWhitespaceScale();
        }

        final FontTextureArray fp = getFontProvider(chr, FontConfig.enableCustomFont, underlying.getUnicodeFlag());
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

    private int mainVAO = 0;

    private int batchDepth = 0;

    //TODO make it work with any initial capacity & fix resize
    private final StreamingDrawer stream = StreamingDrawer.create(1024 * 400 * INSTANCE_SIZE); //TODO test resize
    private int instanceCount; //TODO merge into stream
    private boolean requiresMultisampling;

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

        return vao;
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
            italic, dinnerbone, false
        );
    }

    public void pushQuad(
        float x, float y, float width, float height,
        float uMin, float vMin, float uWidth, float vWidth,
        int rgba,
        int layer,
        boolean italic, boolean dinnerbone, boolean secondaryTexture
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

        final int flags = (italic ? FLAG_ITALIC : 0)
            | (dinnerbone ? FLAG_DINNERBONE : 0)
            | (secondaryTexture ? FLAG_SECOND_TEXTURE : 0);
        memPutByte(ptr + 37, (byte) flags);

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

    private void flushBatch() {
        if (instanceCount == 0) return;

        if (mainVAO == 0) {
            mainVAO = initVAO();
        }

        GLStateManager.glBindVertexArray(mainVAO);

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, stream.getVBO()); //TODO bindless vbo

        final int offset = stream.finishUploading() / INSTANCE_SIZE;

        //vboCapacity = StreamingUploader.upload(getReadBuffer(), vboCapacity);


        final int prevProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        //final boolean isBlendEnabledBefore = GLStateManager.glIsEnabled(GL11.GL_BLEND);

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(blendSrcRGB, blendDstRGB, GL11.GL_ONE, GL11.GL_ZERO);

        //TODO cache mvp
        if (requiresMultisampling) {
            GLStateManager.glUseProgram(multisampleFontShader);
            GLStateManager.uploadMVPMatrix(multisampleMatrixLocation);

            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            primaryTextureArray.bind();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        } else {
            GLStateManager.glUseProgram(defaultFontShader);
            GLStateManager.uploadMVPMatrix(defaultMatrixLocation);
        }

        (this.isSplash ? splashTextureArray : defaultTextureArray).bind();
//        GL31.glDrawElementsInstanced(
//            GL11.GL_TRIANGLES,
//            6,
//            GL11.GL_UNSIGNED_SHORT,
//            0,
//            instanceCount
//        );
        GL42.glDrawElementsInstancedBaseInstance( //TODO
            GL11.GL_TRIANGLES,
            6,
            GL11.GL_UNSIGNED_SHORT,
            0,
            instanceCount,
            offset
        );

        GLStateManager.glUseProgram(prevProgram);

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);

//        if (!isBlendEnabledBefore) {
//            GLStateManager.disableBlend();
//        }

        clearBatch();
    }

    private void clearBatch() {
        instanceCount = 0;
        requiresMultisampling = false;
    }

    private void delete() {
        GLStateManager.glDeleteVertexArrays(mainVAO);
        stream.delete();
    }

}

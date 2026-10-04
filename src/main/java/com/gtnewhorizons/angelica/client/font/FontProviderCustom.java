package com.gtnewhorizons.angelica.client.font;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import jss.util.RandomXoshiro256StarStar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Objects;

import static com.gtnewhorizons.angelica.client.font.FontStrategist.getFontName;

public final class FontProviderCustom implements FontProvider {

    public static final Logger LOGGER = LogManager.getLogger("Angelica");
    static final int ATLAS_SIZE = 128;
    static final int ATLAS_COUNT = 512;
    private final RandomXoshiro256StarStar fontRandom = new RandomXoshiro256StarStar();
    private final FontAtlas[] fontAtlases = new FontAtlas[ATLAS_COUNT];
    private final int[] atlasTextures = new int[ATLAS_COUNT];
    private Font font;

    FontProviderCustom(Font font) {
        this.font = font;
    }

    private static Font configured(String fontName) {
        if (Objects.equals(fontName, "(none)")) {
            return null;
        }
        for (Font available : FontStrategist.getAvailableFonts()) {
            if (Objects.equals(fontName, getFontName(available))) {
                return available.deriveFont((float) FontConfig.customFontQuality);
            }
        }
        LOGGER.info("Could not find previously set font \"{}\". ", fontName);
        return null;
    }

    private static class InstLoader {
        static final FontProviderCustom instance0 = new FontProviderCustom(configured(FontConfig.customFontNamePrimary));
        static final FontProviderCustom instance1 = new FontProviderCustom(configured(FontConfig.customFontNameFallback));
    }
    public static FontProviderCustom getPrimary() { return InstLoader.instance0; }
    public static FontProviderCustom getFallback() { return InstLoader.instance1; }

    public void setFont(Font font) {
        synchronized (this) {
            this.font = font;
            Arrays.fill(this.fontAtlases, null);
        }

        for (int i = 0; i < ATLAS_COUNT; i++) {
            if (this.atlasTextures[i] != 0) {
                GLStateManager.glDeleteTextures(this.atlasTextures[i]);
                this.atlasTextures[i] = 0;
            }
        }
    }

    public void reloadFont(int fontID) {
        setFont(FontStrategist.getAvailableFonts()[fontID].deriveFont((float) FontConfig.customFontQuality));
    }

    private static final class FontAtlas {
        static final int STRIDE = 6;
        static final int U_START = 0;
        static final int V_START = 1;
        static final int X_ADVANCE = 2;
        static final int GLYPH_W = 3;
        static final int U_SIZE = 4;
        static final int V_SIZE = 5;

        final float[] metrics;
        final long availLo;
        final long availHi;

        FontAtlas(float[] metrics, long availLo, long availHi) {
            this.metrics = metrics;
            this.availLo = availLo;
            this.availHi = availHi;
        }

        boolean has(int slot) {
            return bit(this.availLo, this.availHi, slot);
        }

        static boolean bit(long lo, long hi, int slot) {
            return ((slot < 64 ? lo >>> slot : hi >>> (slot - 64)) & 1L) != 0L;
        }
    }

    private static final FontAtlas EMPTY = new FontAtlas(new float[ATLAS_SIZE * FontAtlas.STRIDE], 0L, 0L);

    private static void setHints(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static BufferedImage layout(Font font, int id, long availLo, long availHi, float[] metricsOut) {
        final float quality = font.getSize2D();
        int atlasChars = Long.bitCount(availLo) + Long.bitCount(availHi);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();

        final int atlasTilesX = (int) Math.ceil(Math.sqrt(atlasChars) * 1.5f);
        final int atlasTilesY = (int) Math.ceil((double) atlasChars / atlasTilesX);
        final float charSeparator = quality / 3f;
        int rowWidth = 0;
        int maxRowWidth = 0;
        atlasChars = 0;

        for (int i = 0; i < ATLAS_SIZE; i++) {
            if (atlasChars % atlasTilesX == 0) {
                maxRowWidth = Math.max(maxRowWidth, rowWidth);
                rowWidth = 0;
            }
            if (FontAtlas.bit(availLo, availHi, i)) {
                rowWidth += (int) (charSeparator + fm.charWidth((char) (i + ATLAS_SIZE * id)));
                atlasChars++;
            }
        }
        maxRowWidth = Math.max(maxRowWidth, rowWidth);

        final int lineHeight = fm.getHeight();
        final float desc = fm.getDescent();

        final int imageWidth = (int) (maxRowWidth + charSeparator);
        final int imageHeight = (int) ((charSeparator + lineHeight) * atlasTilesY + charSeparator);

        final boolean rasterize = metricsOut == null;
        if (rasterize) {
            g2d.dispose();
            image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            g2d = image.createGraphics();
            setHints(g2d);
            g2d.setFont(font);
        } else {
            setHints(g2d);
        }
        fm = g2d.getFontMetrics();

        int tileX = 0, tileY = 0; // position in atlas tiles
        int imgX = (int) charSeparator; // position in pixels

        for (int i = 0; i < ATLAS_SIZE; i++) {
            if (!FontAtlas.bit(availLo, availHi, i)) { continue; }
            final char ch = (char) (i + ATLAS_SIZE * id);

            if (tileX >= atlasTilesX) {
                tileX = 0;
                imgX = (int) charSeparator;
                tileY++;
            }

            final int charWidth = fm.charWidth(ch);
            if (rasterize) {
                g2d.drawString(Character.toString(ch), imgX, (lineHeight + charSeparator) * (tileY + 1) - desc);
            } else {
                final float charAspectRatio = (float) charWidth / lineHeight;
                final float inset = quality / 16;
                final int o = i * FontAtlas.STRIDE;
                metricsOut[o + FontAtlas.U_START] = (float) (imgX - inset * charAspectRatio) / imageWidth;
                metricsOut[o + FontAtlas.V_START] = ((lineHeight + charSeparator) * (tileY + 1) - lineHeight - inset) / imageHeight;
                metricsOut[o + FontAtlas.X_ADVANCE] = charAspectRatio * 8 * charWidth / (charWidth + 2 * inset * charAspectRatio);
                metricsOut[o + FontAtlas.GLYPH_W] = charAspectRatio * 8 + 1;
                metricsOut[o + FontAtlas.U_SIZE] = (float) (charWidth + 2 * inset * charAspectRatio) / imageWidth;
                metricsOut[o + FontAtlas.V_SIZE] = (float) (lineHeight + 2 * inset) / imageHeight;
            }
            imgX += (int) (charWidth + charSeparator);
            tileX++;
        }
        g2d.dispose();

        return rasterize ? image : null;
    }

    private FontAtlas getAtlas(char chr) {
        final FontAtlas fa = this.fontAtlases[chr / ATLAS_SIZE];
        return fa != null ? fa : buildAtlas(chr / ATLAS_SIZE);
    }

    private synchronized FontAtlas buildAtlas(int id) {
        FontAtlas fa = this.fontAtlases[id];
        if (fa != null) { return fa; }
        final Font f = this.font;
        if (f == null) { return EMPTY; }
        long availLo = 0L;
        long availHi = 0L;
        for (int i = 0; i < ATLAS_SIZE; i++) {
            if (f.canDisplay((char) (i + ATLAS_SIZE * id))) {
                if (i < 64) { availLo |= 1L << i; } else { availHi |= 1L << (i - 64); }
            }
        }
        if ((availLo | availHi) == 0L) {
            fa = EMPTY;
        } else {
            final float[] metrics = new float[ATLAS_SIZE * FontAtlas.STRIDE];
            layout(f, id, availLo, availHi, metrics);
            fa = new FontAtlas(metrics, availLo, availHi);
        }
        this.fontAtlases[id] = fa;
        return fa;
    }

    private synchronized BufferedImage rasterize(int id) {
        final Font f = this.font;
        final FontAtlas fa = this.fontAtlases[id];
        if (f == null || fa == null || fa == EMPTY) { return null; }
        return layout(f, id, fa.availLo, fa.availHi, null);
    }

    private int uploadAtlas(int id) {
        final BufferedImage image = rasterize(id);
        if (image == null) { return 0; }

        final int texture = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        final IntBuffer pixelBuffer = memAllocInt(pixels.length);
        pixelBuffer.put(pixels);
        pixelBuffer.flip();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, image.getWidth(), image.getHeight(), 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        memFree(pixelBuffer);

        this.atlasTextures[id] = texture;
        return texture;
    }

    @Override
    public boolean isGlyphAvailable(char chr) {
        if (this.font == null) { return false; }
        return getAtlas(chr).has(chr % ATLAS_SIZE);
    }

    /**
     * A random glyph of the same width, for {@code §k}. Kept inside the character's own
     * atlas: reaching outside one would measure and rasterize another atlas mid-frame.
     * Slots the font has no glyph for are skipped.
     */
    @Override
    public char getRandomReplacement(char chr) {
        if (this.font == null) {
            return chr;
        }
        final FontAtlas atlas = getAtlas(chr);
        if (!atlas.has(chr % ATLAS_SIZE)) {
            return chr;
        }
        final float targetAdvance = atlas.metrics[(chr % ATLAS_SIZE) * FontAtlas.STRIDE + FontAtlas.X_ADVANCE];
        final int atlasStart = (chr / ATLAS_SIZE) * ATLAS_SIZE;
        for (int attempt = 0; attempt < RANDOM_GLYPH_TRIES; attempt++) {
            final int slot = fontRandom.nextInt(ATLAS_SIZE);
            if (atlas.has(slot) && atlas.metrics[slot * FontAtlas.STRIDE + FontAtlas.X_ADVANCE] == targetAdvance) {
                return (char) (atlasStart + slot);
            }
        }
        return chr;
    }

    private float metric(char chr, int field) {
        return getAtlas(chr).metrics[(chr % ATLAS_SIZE) * FontAtlas.STRIDE + field];
    }

    @Override
    public float getUStart(char chr) {
        return metric(chr, FontAtlas.U_START);
    }

    @Override
    public float getVStart(char chr) {
        return metric(chr, FontAtlas.V_START);
    }

    @Override
    public float getXAdvance(char chr) {
        return metric(chr, FontAtlas.X_ADVANCE) * FontConfig.customFontScale;
    }

    @Override
    public float getGlyphW(char chr) {
        return metric(chr, FontAtlas.GLYPH_W) * FontConfig.customFontScale;
    }

    @Override
    public float getUSize(char chr) {
        return metric(chr, FontAtlas.U_SIZE);
    }

    @Override
    public float getVSize(char chr) {
        return metric(chr, FontAtlas.V_SIZE);
    }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset;
    }

    @Override
    public int getTexture(char chr) {
        final int id = chr / ATLAS_SIZE;
        final int texture = this.atlasTextures[id];
        return texture != 0 ? texture : uploadAtlas(id);
    }

    @Override
    public float getYScaleMultiplier() {
        return FontConfig.customFontScale;
    }
}

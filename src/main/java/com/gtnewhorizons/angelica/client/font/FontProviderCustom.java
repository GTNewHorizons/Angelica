package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.config.FontConfig;
import lombok.Setter;
import lombok.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

public final class FontProviderCustom implements FontProvider {

    public static final Logger LOGGER = LogManager.getLogger("Angelica");
    public static final String FONT_DIR = "fonts/custom/";
    static final int ATLAS_SIZE = 128;
    static final int ATLAS_COUNT = 512;
    private final byte id; // 0 - primary font, 1 - fallback font
    private FontAtlas[] fontAtlases = new FontAtlas[ATLAS_COUNT];
    private float currentFontQuality = FontConfig.customFontQuality;
    @Setter
    private Font font;

    private FontProviderCustom(byte id) {
        this.id = id;
        Font[] availableFonts = FontStrategist.getAvailableFonts();
        String myFontName = switch (this.id) {
            case 0 -> FontConfig.customFontNamePrimary;
            case 1 -> FontConfig.customFontNameFallback;
            default -> null;
        };
        if (Objects.equals(myFontName, "(none)")) {
            font = null;
            return;
        }
        int fontPos = -1;
        for (int i = 0; i < availableFonts.length; i++) {
            if (Objects.equals(myFontName, availableFonts[i].getName())) {
                fontPos = i;
                break;
            }
        }
        if (fontPos == -1) {
            LOGGER.info("Could not find previously set font \"{}\". ", myFontName);
            font = null;
            return;
        }
        font = availableFonts[fontPos].deriveFont(currentFontQuality);
    }
    private static class InstLoader {
        static final FontProviderCustom instance0 = new FontProviderCustom((byte)0);
        static final FontProviderCustom instance1 = new FontProviderCustom((byte)1);
    }
    public static FontProviderCustom getPrimary() { return InstLoader.instance0; }
    public static FontProviderCustom getFallback() { return InstLoader.instance1; }

    public void reloadFont(int fontID) {
        currentFontQuality = FontConfig.customFontQuality;
        font = FontStrategist.getAvailableFonts()[fontID].deriveFont(currentFontQuality);

        File[] files = new File(getFontDir()).listFiles();
        if (files != null) {
            for (File f : files) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    f.delete();
                }
            }
        }

        TextureManager tm = Minecraft.getMinecraft().getTextureManager();
        Map mapTextureObjects = tm.mapTextureObjects;
        for (int i = 0; i < ATLAS_COUNT; i++) {
            ResourceLocation key = new ResourceLocation(getAtlasResourceName(i));
            if (mapTextureObjects.containsKey(key)) {
                ITextureObject obj = (ITextureObject) mapTextureObjects.get(key);
                TextureUtil.deleteTexture(obj.getGlTextureId());
                mapTextureObjects.remove(key);
            }
        }

        fontAtlases = new FontAtlas[ATLAS_COUNT];
    }

    private String getFontDir() {
        return FONT_DIR + "f" + this.id + "/";
    }

    private String getAtlasFilename(int atlasId) {
        return "f" + this.id + "p" + atlasId;
    }

    String getAtlasResourceName(int atlasId) {
        return "minecraft:angelica_c" + getAtlasFilename(atlasId);
    }

    String getAtlasFullPath(int atlasId) {
        return getFontDir() + getAtlasFilename(atlasId) + ".png";
    }

    @Value
    private class GlyphData {
        float uStart;
        float vStart;
        float xAdvance;
        float glyphW;
        float uSz;
        float vSz;
    }

    private class FontAtlas {

        GlyphData[] glyphData = new GlyphData[ATLAS_SIZE];
        private ResourceLocation texture;
        private final int id;

        FontAtlas(int id) {
            this.id = id;
        }

        void construct(Font font) {
            float separator = currentFontQuality / 3f;

            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.dispose();
            int width = 0;
            int actualChars = 0;
            for (int i = 0; i < ATLAS_SIZE; i++) {
                final char ch = (char)(i + ATLAS_SIZE * this.id);
                if (font.canDisplay(ch)) {
                    width += (int) (separator + fm.charWidth(ch));
                    actualChars++;
                }
            }
            if (actualChars == 0) {
                return;
            }
            final int atlasTilesX = (int) Math.ceil(Math.sqrt(actualChars) * 1.5f);
            final int atlasTilesY = (int) Math.ceil((double) actualChars / atlasTilesX);
            LOGGER.info("constructing custom font atlas (ID {}) of nominal size {} chars, actual: {}, {} rows by {} columns",
                this.id, ATLAS_SIZE, actualChars, atlasTilesX, atlasTilesY);
            width = 0;
            actualChars = 0;
            int maxRowWidth = 0;
            for (int i = 0; i < ATLAS_SIZE; i++) {
                if (actualChars % atlasTilesX == 0) {
                    maxRowWidth = Math.max(maxRowWidth, width);
                    width = 0;
                }
                final char ch = (char)(i + ATLAS_SIZE * this.id);
                if (font.canDisplay(ch)) {
                    width += (int) (separator + fm.charWidth(ch));
                    actualChars++;
                }
            }
            maxRowWidth = Math.max(maxRowWidth, width);

            final int lineHeight = fm.getHeight();
            final float desc = fm.getDescent();

            final int imageWidth = (int) (maxRowWidth + separator);
            final int imageHeight = (int) ((separator + lineHeight) * atlasTilesY + separator);

            image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setFont(font);
            fm = g2d.getFontMetrics();

            int tileX = 0, tileY = 0; // position in atlas tiles
            int imgX = (int) separator; // position in pixels

            for (int i = 0; i < ATLAS_SIZE; i++) {
                final char ch = (char)(i + ATLAS_SIZE * this.id);
                if (!font.canDisplay(ch)) { continue; }

                if (tileX >= atlasTilesX) {
                    tileX = 0;
                    imgX = (int) separator;
                    tileY++;
                }

                final int charWidth = fm.charWidth(ch);
                final float charAspectRatio = (float) charWidth / lineHeight;
                final float inset = currentFontQuality / 16;
                g2d.drawString(Character.toString(ch), imgX, (lineHeight + separator) * (tileY + 1) - desc);
                final float uStart = (float) (imgX - inset * charAspectRatio) / imageWidth;
                final float vStart = ((lineHeight + separator) * (tileY + 1) - lineHeight - inset) / imageHeight;
                final float xAdvance = charAspectRatio * 8 * charWidth / (charWidth + 2 * inset * charAspectRatio);
                final float glyphW = charAspectRatio * 8 + 1;
                final float uSz = (float) (charWidth + 2 * inset * charAspectRatio) / imageWidth;
                final float vSz = (float) (lineHeight + 2 * inset) / imageHeight;
                glyphData[i] = new GlyphData(uStart, vStart, xAdvance, glyphW, uSz, vSz);
                imgX += (int) (charWidth + separator);
                tileX++;
            }
            g2d.dispose();
            try {
                LOGGER.info("writing custom font atlas texture ({}x{} px) to {}", imageWidth, imageHeight, getAtlasFullPath(this.id));
                Files.createDirectories(Paths.get(getFontDir()));
                ImageIO.write(image, "png", new File(getAtlasFullPath(this.id)));
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.texture = new ResourceLocation(getAtlasResourceName(this.id));
        }
    }

    private FontAtlas getAtlas(char chr) {
        int id = chr / ATLAS_SIZE;
        FontAtlas fa = fontAtlases[id];
        if (fa == null) {
            fa = new FontAtlas(id);
            fa.construct(font);
        }
        fontAtlases[id] = fa;
        return fa;
    }

    @Override
    public boolean isGlyphAvailable(char chr) {
        if (font == null) { return false; }
        return (getAtlas(chr).glyphData[chr % ATLAS_SIZE] != null);
    }

    @Override
    public char getRandomReplacement(char chr) {
        return chr;
    }

    @Override
    public float getUStart(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].uStart;
    }

    @Override
    public float getVStart(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].vStart;
    }

    @Override
    public float getXAdvance(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].xAdvance * FontConfig.customFontScale;
    }

    @Override
    public float getGlyphW(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].glyphW * FontConfig.customFontScale;
    }

    @Override
    public float getUSize(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].uSz;
    }

    @Override
    public float getVSize(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].vSz;
    }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset;
    }

    @Override
    public ResourceLocation getTexture(char chr) {
        return getAtlas(chr).texture;
    }

    @Override
    public float getYScaleMultiplier() {
        return FontConfig.customFontScale;
    }
}

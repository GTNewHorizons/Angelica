package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Setter;
import lombok.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static com.gtnewhorizons.angelica.AngelicaMod.LOGGER;

public final class FontProviderCustom implements FontProvider {

    static final int ATLAS_SIZE = 128;
    static final int ATLAS_COUNT = 512;
    private FontAtlas[] fontAtlases = new FontAtlas[ATLAS_COUNT];
    private static float currentFontQuality = FontConfig.customFontQuality;
    @Setter
    private Font font;

    private static final boolean DUMP_ATLASES = true;


    private FontProviderCustom(byte id) {
        // 0 - primary font, 1 - fallback font
        Font[] availableFonts = FontStrategist.getAvailableFonts();
        String myFontName = switch (id) {
            case 0 -> FontConfig.customFontNamePrimary;
            case 1 -> FontConfig.customFontNameFallback;
            default -> null;
        };
        if ("(none)".equals(myFontName)) {
            this.font = null;
            return;
        }
        int fontPos = -1;
        for (int i = 0; i < availableFonts.length; i++) {
            if (Objects.equals(myFontName, availableFonts[i].getFontName())) {
                fontPos = i;
                break;
            }
        }
        if (fontPos == -1) {
            LOGGER.info("Could not find previously set font \"{}\". ", myFontName);
            this.font = null;
            return;
        }
        this.font = availableFonts[fontPos].deriveFont(currentFontQuality);
    }
    private static final class InstLoader {
        static final FontProviderCustom instance0 = new FontProviderCustom((byte)0);
        static final FontProviderCustom instance1 = new FontProviderCustom((byte)1);
    }
    public static FontProviderCustom getPrimary() { return InstLoader.instance0; }
    public static FontProviderCustom getFallback() { return InstLoader.instance1; }

    public void reloadFont(int fontID) {
        currentFontQuality = FontConfig.customFontQuality;
        this.font = FontStrategist.getAvailableFonts()[fontID].deriveFont(currentFontQuality);

        for (FontAtlas atlas : fontAtlases) {
            if (atlas != null) {
                GL11.glDeleteTextures(atlas.texture);
            }
        }

        if (DUMP_ATLASES) {
            for (int i = 0; i < fontAtlases.length; i++) {
                final FontAtlas atlas = new FontAtlas(i);
                fontAtlases[i] = atlas;
                atlas.construct(font);
            }
        }

        this.fontAtlases = new FontAtlas[ATLAS_COUNT];
    }

    @Value
    private static class GlyphData {
        float uStart;
        float vStart;
        float xAdvance;
        float glyphW;
        float uSz;
        float vSz;
    }

    private static final class FontAtlas {

        GlyphData[] glyphData = new GlyphData[ATLAS_SIZE];
        private int texture;
        private final int id;

        FontAtlas(int id) {
            this.id = id;
        }

        void construct(Font font) {
            int atlasChars = 0;
            for (int i = 0; i < ATLAS_SIZE; i++) {
                final char ch = (char) (i + ATLAS_SIZE * this.id);
                if (font.canDisplay(ch)) { atlasChars++; }
            }
            if (atlasChars == 0) { return; }

            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.dispose();

            final int atlasTilesX = (int) Math.ceil(Math.sqrt(atlasChars) * 1.5f);
            final int atlasTilesY = (int) Math.ceil((double) atlasChars / atlasTilesX);
            final float charSeparator = currentFontQuality / 3f;
            int rowWidth = 0;
            int maxRowWidth = 0;
            atlasChars = 0;

            for (int i = 0; i < ATLAS_SIZE; i++) {
                if (atlasChars % atlasTilesX == 0) {
                    maxRowWidth = Math.max(maxRowWidth, rowWidth);
                    rowWidth = 0;
                }
                final char ch = (char)(i + ATLAS_SIZE * this.id);
                if (font.canDisplay(ch)) {
                    rowWidth += (int) (charSeparator + fm.charWidth(ch));
                    atlasChars++;
                }
            }
            maxRowWidth = Math.max(maxRowWidth, rowWidth);

            final int lineHeight = fm.getHeight();
            final float desc = fm.getDescent();

            final int imageWidth = (int) (maxRowWidth + charSeparator);
            final int imageHeight = (int) ((charSeparator + lineHeight) * atlasTilesY + charSeparator);

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
            int imgX = (int) charSeparator; // position in pixels

            for (int i = 0; i < ATLAS_SIZE; i++) {
                final char ch = (char) (i + ATLAS_SIZE * this.id);
                if (!font.canDisplay(ch)) { continue; }

                if (tileX >= atlasTilesX) {
                    tileX = 0;
                    imgX = (int) charSeparator;
                    tileY++;
                }

                final int charWidth = fm.charWidth(ch);
                final float charAspectRatio = (float) charWidth / lineHeight;
                final float inset = currentFontQuality / 16;
                g2d.drawString(Character.toString(ch), imgX, (lineHeight + charSeparator) * (tileY + 1) - desc);
                final float uStart = (imgX - inset * charAspectRatio) / imageWidth;
                final float vStart = ((lineHeight + charSeparator) * (tileY + 1) - lineHeight - inset) / imageHeight;
                final float xAdvance = charAspectRatio * 8 * charWidth / (charWidth + 2 * inset * charAspectRatio);
                final float glyphW = charAspectRatio * 8 + 1;
                final float uSz = (charWidth + 2 * inset * charAspectRatio) / imageWidth;
                final float vSz = (lineHeight + 2 * inset) / imageHeight;
                this.glyphData[i] = new GlyphData(uStart, vStart, xAdvance, glyphW, uSz, vSz);
                imgX += (int) (charWidth + charSeparator);
                tileX++;
            }
            g2d.dispose();
            if (DUMP_ATLASES) {
                try {
                    File dir = new File(Minecraft.getMinecraft().mcDataDir, "debug/fonts");
                    if (!dir.exists()) dir.mkdirs();
                    ImageIO.write(image, "png", new File(dir, id + ".png"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            this.texture = TextureUtil.uploadTextureImageAllocate(GLStateManager.glGenTextures(), image, false, false);
            //this.texture = new ResourceLocation(getAtlasResourceName(this.id));
        }
    }

    private FontAtlas getAtlas(char chr) {
        int id = chr / ATLAS_SIZE;
        FontAtlas fa = this.fontAtlases[id];
        if (fa == null) {
            fa = new FontAtlas(id);
            fa.construct(this.font);
            this.fontAtlases[id] = fa;
        }
        return fa;
    }

    @Override
    public boolean isGlyphAvailable(char chr) {
        if (this.font == null) { return false; }
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
    public int getTexture(char chr) {
        return getAtlas(chr).texture;
    }

    @Override
    public float getYScaleMultiplier() {
        return FontConfig.customFontScale;
    }
}

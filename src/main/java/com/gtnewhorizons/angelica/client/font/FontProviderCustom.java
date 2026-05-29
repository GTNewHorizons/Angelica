package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.client.renderer.textures.TextureLoader;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Objects;

import static com.gtnewhorizons.angelica.AngelicaMod.LOGGER;

public final class FontProviderCustom implements FontProvider {

    static final int ATLAS_CHARS = 256;
    //static final int ATLAS_SIZE = 256;
    static final int ATLAS_COUNT = 256;
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

    private static final class FontAtlas {

        private final GlyphData[] glyphData = new GlyphData[ATLAS_CHARS];
        private int texture;
        private final int id;

        FontAtlas(int id) {
            this.id = id;
        }

        void construct(Font font) {
            int atlasChars = 0;
            for (int i = 0; i < ATLAS_CHARS; i++) {
                final char ch = (char) (i + ATLAS_CHARS * this.id);
                if (font.canDisplay(ch)) { atlasChars++; }
            }
            if (atlasChars == 0) { return; }

            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.dispose();

            final int atlasTilesX = 512;
            final int atlasTilesY = 512;
            final int charSeparator = 2; //TODO idk
//            atlasChars = 0;
//
//            for (int i = 0; i < ATLAS_CHARS; i++) {
//                final char ch = (char)(i + ATLAS_CHARS * this.id);
//                if (font.canDisplay(ch)) {
//                    rowWidth += (int) (charSeparator + fm.charWidth(ch));
//                    atlasChars++;
//                }
//            }

            final int lineHeight = fm.getHeight();
            final float desc = fm.getDescent();

            final int imageWidth = atlasTilesX;
            final int imageHeight = atlasTilesY;

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

            //int tileY = 0; // position in atlas tiles

            final int charHeight = (lineHeight + charSeparator);

            int imgX = 0; // position in pixels
            int imgY = charHeight;

            for (int i = 0; i < ATLAS_CHARS; i++) {
                final char ch = (char) (i + ATLAS_CHARS * this.id);
                if (!font.canDisplay(ch)) { continue; }

                final int charWidth = fm.charWidth(ch);

                if (imgX + charWidth >= atlasTilesX) {
                    imgX = 0;
                    imgY += charHeight;
                }

                final float charAspectRatio = (float) charWidth / lineHeight;
                final float inset = currentFontQuality / 16;
                g2d.drawString(Character.toString(ch), imgX, imgY - desc);
                final float uStart = (imgX - inset * charAspectRatio) / imageWidth;
                final float vStart = (imgY - lineHeight - inset + 1) / imageHeight;
                final float xAdvance = charAspectRatio * 8 * charWidth / (charWidth + 2 * inset * charAspectRatio);
                final float glyphW = charAspectRatio * 8 + 1;
                final float uSz = (charWidth + 2 * inset * charAspectRatio) / imageWidth;
                final float vSz = (lineHeight + 2 * inset) / imageHeight;
                this.glyphData[i] = new GlyphData(uStart, vStart, xAdvance, glyphW, uSz, vSz);
                imgX += (charWidth + charSeparator);
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

            this.texture = GLStateManager.glGenTextures();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GLStateManager.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA,
                imageWidth, imageHeight,
                0,
                GL12.GL_BGRA,
                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                getPixelBuffer(image)
            );
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }

    private static IntBuffer getPixelBuffer(BufferedImage image) {
        final IntBuffer pixelBuffer = TextureLoader.dataBuffer;
        pixelBuffer.clear();
        final int[] pixelValues = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        pixelBuffer.put(pixelValues);
        pixelBuffer.position(0).limit(pixelValues.length);
        return pixelBuffer;
    }

    private FontAtlas getAtlas(char chr) {
        int id = chr / ATLAS_CHARS;
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
        return (getAtlas(chr).glyphData[chr % ATLAS_CHARS] != null);
    }

    @Override
    public char getRandomReplacement(char chr) {
        return chr;
    }

    @Override
    public float getUStart(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_CHARS].uStart;
    }

    @Override
    public float getVStart(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_CHARS].vStart;
    }

    @Override
    public float getXAdvance(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_CHARS].xAdvance * FontConfig.customFontScale;
    }

    @Override
    public float getGlyphW(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_CHARS].glyphW * FontConfig.customFontScale;
    }

    @Override
    public float getUSize(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_CHARS].uSize;
    }

    @Override
    public float getVSize(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_CHARS].uSize;
    }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset;
    }

    @Override
    public float getYScaleMultiplier() {
        return FontConfig.customFontScale;
    }
}

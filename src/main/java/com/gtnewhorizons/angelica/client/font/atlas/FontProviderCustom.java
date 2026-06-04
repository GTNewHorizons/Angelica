package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizons.angelica.client.font.GlyphData;
import com.gtnewhorizons.angelica.config.FontConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class FontProviderCustom extends FontTextureArray {
    private static final int ATLAS_CHARS = 256;
    private static final int CHAR_SEPARATOR = 2;

    private final Font[] fonts;

    private FontProviderCustom(int size, int layers, int[] layersLookupArray, int filter, Font[] fonts) {
        super(size, layers, layersLookupArray, filter);
        this.fonts = fonts;
    }

    public static FontProviderCustom createTextureArray(final Font[] fonts) {
        final int fontAmount = fonts.length;
        if (fontAmount == 0) return null;
        final FontMetrics[] fontMetrics = new FontMetrics[fontAmount];
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < fontAmount; i++) {
            Graphics2D g2d = image.createGraphics();
            g2d.setFont(fonts[i]);
            fontMetrics[i] = g2d.getFontMetrics();
            g2d.dispose();
        }


        int[] layerLookup = new int[256];
        int layer = 0;
        loop:
        for (int atlasId = 0; atlasId < 256; atlasId++) {
            for (int i = 0; i < 256; i++) {
                final char ch = (char) (i + atlasId * 256);
                for (Font font : fonts) {
                    if (font.canDisplay(ch)) {
                        layerLookup[atlasId] = layer;
                        layer++;
                        continue loop;
                    }
                }
                // Can't display any chars, skip.
                layerLookup[atlasId] = -1;
            }
        }

        final int size = getAtlasSize(fonts, fontMetrics, 2);

        return new FontProviderCustom(
            size,
            layer,
            layerLookup,
            GL11.GL_LINEAR,
            fonts
        );
    }


    private static int getAtlasSize(Font[] fonts, FontMetrics[] fontMetrics, int separator) {
        int size = 512;

        int maxCharHeight = 0;
        for (FontMetrics fm : fontMetrics) {
            if (fm.getHeight() > maxCharHeight) maxCharHeight = fm.getHeight();
        }

        loop:
        while (size <= 2048) {
            for (int atlasId = 0; atlasId < 256; atlasId++) {
                int imgX = 0;
                int imgY = 0; //TODO idk
                for (int atlasChar = 0; atlasChar < 256; atlasChar++) {
                    final char ch = (char) (atlasChar + 256 * atlasId);
                    for (int i = 0; i < fonts.length; i++) {
                        if (!fonts[i].canDisplay(ch)) continue;

                        final int lineHeight = fontMetrics[i].getHeight();
                        final int charHeight = (lineHeight + separator);

                        final int charWidth = fontMetrics[i].charWidth(ch);

                        if (imgX + charWidth >= size) {
                            imgX = 0;
                            imgY += charHeight;
                        }
                        imgX += (charWidth + separator);
                    }

                }
                //if (imgX == 0 && imgY == 0) return 0; TODO integrate this somehow
                // Total used height includes final row
                int usedHeight = imgY + maxCharHeight;

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

    @Override
    public ByteBuffer generateGlyphData(int atlasId, GlyphData[] glyphs) {
        int atlasChars = 0;
        for (int i = 0; i < ATLAS_CHARS; i++) {
            final char ch = (char) (i + ATLAS_CHARS * atlasId);
            for (Font font : fonts) {
                if (font.canDisplay(ch)) {
                    atlasChars++;
                    break;
                }
            }
        }
        if (atlasChars == 0) {
            return null;
        }

        final int charSeparator = CHAR_SEPARATOR; //TODO idk

        final int imageWidth = textureSize;
        final int imageHeight = textureSize;

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        final FontMetrics[] fontMetrics = new FontMetrics[fonts.length];
        for (int i = 0; i < fontMetrics.length; i++) {
            g2d.setFont(fonts[i]);
            fontMetrics[i] = g2d.getFontMetrics();
        }

        int maxCharHeight = 0;
        for (FontMetrics fm : fontMetrics) {
            if (fm.getHeight() > maxCharHeight) maxCharHeight = fm.getHeight();
        }

        int imgX = 1; // position in pixels
        int imgY = maxCharHeight;


        Font lastFont = null; // Store last font to save on repeated setFont() calls

        for (int atlasChar = 0; atlasChar < ATLAS_CHARS; atlasChar++) {
            final char ch = (char) (atlasChar + ATLAS_CHARS * atlasId);
            for (int i = 0; i < fonts.length; i++) {
                if (fonts[i].canDisplay(ch)) {
                    final FontMetrics fm = fontMetrics[i];

                    final int lineHeight = fm.getHeight();
                    final float desc = fm.getDescent();

                    final int charHeight = (lineHeight + charSeparator);

                    final int charWidth = fm.charWidth(ch);

                    if (imgX + charWidth >= imageWidth) {
                        imgX = 1;
                        imgY += charHeight;
                    }

                    final float charAspectRatio = ((float) charWidth) / lineHeight;
                    final float inset = 0;
                    g2d.setFont(fonts[i]);
                    g2d.drawString(Character.toString(ch), imgX, imgY - desc);
                    final float uStart = (imgX - inset * charAspectRatio) / imageWidth;
                    final float vStart = (imgY - lineHeight - inset + 1) / imageHeight;
                    final float xAdvance = (charAspectRatio * 8 * charWidth / (charWidth + 2 * inset * charAspectRatio)) * FontConfig.customFontScale;
                    final float glyphW = (charAspectRatio * 8 + 1) * FontConfig.customFontScale;
                    final float uSz = (charWidth + 2 * inset * charAspectRatio) / imageWidth;
                    final float vSz = (lineHeight + 2 * inset) / imageHeight;
                    glyphs[atlasChar] = new GlyphData(uStart, vStart, xAdvance, glyphW, uSz, vSz);
                    imgX += (charWidth + charSeparator);
                    break;
                }
            }

        }
        g2d.dispose();

        if (FontTextureArray.DUMP_ATLASES) {
            try {
                File dir = new File(Minecraft.getMinecraft().mcDataDir, "debug/fonts");
                if (!dir.exists()) dir.mkdirs();
                ImageIO.write(image, "png", new File(dir,  "custom_font_" + fonts[0].getFontName() + "_" + atlasId + ".png"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return getPixelBuffer(image);
    }
}

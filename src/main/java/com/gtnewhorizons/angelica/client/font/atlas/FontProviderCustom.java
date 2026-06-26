package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizon.gtnhlib.util.font.GlyphReplacements;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.GlyphData;
import com.gtnewhorizons.angelica.config.FontConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class FontProviderCustom extends FontTextureArray {
    private static final int ATLAS_CHARS = 256;

    private final Font[] fonts;

    private FontProviderCustom(int size, int layers, short[] layersLookupArray, int filter, Font[] fonts) {
        super(size, layers, layersLookupArray, filter);
        this.fonts = fonts;
    }

    public static FontProviderCustom createTextureArray(final Font[] fonts) {
        final int fontAmount = fonts.length;
        if (fontAmount == 0) return null;
        final FontMetrics[] fontMetrics = new FontMetrics[fontAmount];
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        for (int i = 0; i < fontAmount; i++) {
            g2d.setFont(fonts[i]);
            fontMetrics[i] = g2d.getFontMetrics();
        }
        g2d.dispose();


        short[] layerLookup = new short[256];
        short layer = 0;
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

    private boolean canDisplayAtlas(int atlasId) {
        for (int i = 0; i < ATLAS_CHARS; i++) {
            final char ch = (char) (i + ATLAS_CHARS * atlasId);
            for (Font font : fonts) {
                if (font.canDisplay(ch)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ByteBuffer generateGlyphData(int atlasId, GlyphData[] glyphs) {
        if (!canDisplayAtlas(atlasId)) return null;

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

        int maxCharHeight = 0;
        final FontMetrics[] fontMetrics = new FontMetrics[fonts.length];
        for (int i = 0; i < fontMetrics.length; i++) {
            g2d.setFont(fonts[i]);
            final FontMetrics fm = g2d.getFontMetrics();
            fontMetrics[i] = fm;
            if (fm.getHeight() > maxCharHeight) maxCharHeight = fm.getHeight();
        }


        final int padding = 2;


        int imgX = padding; // position in pixels
        int imgY = maxCharHeight + padding;


        Font lastFont = null; // Store last font to save on repeated setFont() calls

        final float height = BatchingFontRenderer.getStringHeight();


        final int atlasBegin = ATLAS_CHARS * atlasId;
        for (int atlasChar = 0; atlasChar < ATLAS_CHARS; atlasChar++) {
            char ch = (char) (atlasChar + atlasBegin);

            final char replacement = GlyphReplacements.getReplacementGlyph(ch);
            if (replacement != 0) {
                if (FontConfig.enableGlyphReplacements) {
                    for (Font font : fonts) {
                        if (font.canDisplay(replacement)) {
                            ch = replacement;
                            break;
                        }
                    }
                }
            }

            for (int i = 0; i < fonts.length; i++) {
                final Font font = fonts[i];
                if (font.canDisplay(ch)) {
                    if (lastFont != font) {
                        g2d.setFont(font);
                        lastFont = font;
                    }
                    final FontMetrics fm = fontMetrics[i];

                    final int lineHeight = fm.getHeight();
                    final float desc = fm.getDescent();

                    final int advanceWidth = fm.charWidth(ch);

                    final GlyphVector gv = font.createGlyphVector(
                        g2d.getFontRenderContext(),
                        Character.toString(ch));

                    if (imgX + advanceWidth >= imageWidth) {
                        imgX = padding;
                        imgY += (maxCharHeight + padding);
                    }

                    final Rectangle bounds = gv.getPixelBounds(
                        g2d.getFontRenderContext(),
                        0, 0);

                    g2d.drawGlyphVector(
                        gv,
                        imgX - bounds.x,
                        imgY - desc
                    );
                    final float scale = (height / lineHeight) * getGlyphScaleX();
                    final float glyphW = advanceWidth * scale;
                    final float xStart = bounds.x * scale;
                    final float xWidth = bounds.width * scale;

                    final float uStart = (float) (imgX) / imageWidth;
                    final float vStart = (float) (imgY - lineHeight) / imageHeight;

                    final float uSz = bounds.width / (float) imageWidth;
                    final float vSz = lineHeight / (float) imageHeight;
                    glyphs[atlasChar] = new GlyphData(uStart, vStart, xStart, xWidth, glyphW, uSz, vSz);
                    imgX += bounds.width + padding;

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

    static float getGlyphScaleX() {
        return (FontConfig.glyphScale * (float) Math.pow(2, FontConfig.glyphAspect));
    }
}

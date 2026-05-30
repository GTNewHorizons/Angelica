package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizons.angelica.client.font.GlyphData;
import com.gtnewhorizons.angelica.config.FontConfig;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class AtlasProviderCustom implements AtlasProvider {

    private static final int ATLAS_CHARS = 256;
    private static final int CHAR_SEPARATOR = 4;
    private static final boolean DUMP_ATLASES = true;

    private final Font font;


    public AtlasProviderCustom(Font font) {
        this.font = font;
    }

    @Override
    public ByteBuffer generateGlyphData(int atlasId, int textureSize, GlyphData[] glyphs) {
        int atlasChars = 0;
        for (int i = 0; i < ATLAS_CHARS; i++) {
            final char ch = (char) (i + ATLAS_CHARS * atlasId);
            if (font.canDisplay(ch)) {
                atlasChars++;
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
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();

        final int lineHeight = fm.getHeight();
        final float desc = fm.getDescent();

        //int tileY = 0; // position in atlas tiles

        final int charHeight = (lineHeight + charSeparator);

        int imgX = 1; // position in pixels
        int imgY = charHeight;

        for (int i = 0; i < ATLAS_CHARS; i++) {
            final char ch = (char) (i + ATLAS_CHARS * atlasId);
            if (!font.canDisplay(ch)) {
                continue;
            }

            final int charWidth = fm.charWidth(ch);

            if (imgX + charWidth >= imageWidth) {
                imgX = 1;
                imgY += charHeight;
            }

            final float charAspectRatio = ((float) charWidth) / lineHeight;
            final float inset = 0;
            g2d.drawString(Character.toString(ch), imgX, imgY - desc);
            final float uStart = (imgX - inset * charAspectRatio) / imageWidth;
            final float vStart = (imgY - lineHeight - inset + 1) / imageHeight;
            final float xAdvance = (charAspectRatio * 8 * charWidth / (charWidth + 2 * inset * charAspectRatio)) * FontConfig.customFontScale;
            final float glyphW = (charAspectRatio * 8 + 1) * FontConfig.customFontScale;
            final float uSz = (charWidth + 2 * inset * charAspectRatio) / imageWidth;
            final float vSz = (lineHeight + 2 * inset) / imageHeight;
            glyphs[i] = new GlyphData(uStart, vStart, xAdvance, glyphW, uSz, vSz);
            imgX += (charWidth + charSeparator);
        }
        g2d.dispose();

        if (DUMP_ATLASES) {
            try {
                File dir = new File(Minecraft.getMinecraft().mcDataDir, "debug/fonts");
                if (!dir.exists()) dir.mkdirs();
                ImageIO.write(image, "png", new File(dir,  "custom_font_" + font.getFontName() + "_" + atlasId + ".png"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return AtlasProvider.getPixelBuffer(image);
    }
}

package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.ResourceAccessor;
import lombok.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public final class FontProviderCustom implements FontProvider {

    private FontProviderCustom() {
        Font[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        if (availableFonts.length == 0) {
            LOGGER.fatal("There seem to be no fonts available on this system! Disabling custom font and throwing an exception in an attempt to restore the session.");
            FontConfig.enableCustomFont = false;
            ConfigurationManager.save(FontConfig.class);
            throw new RuntimeException();
        }
        int fontPos = -1;
        for (int i = 0; i < availableFonts.length; i++) {
            if (Objects.equals(FontConfig.customFontName, availableFonts[i].getFontName())) {
                fontPos = i;
                break;
            }
        }
        if (fontPos == -1) {
            LOGGER.info("Could not find previously set font \"{}\". Selecting the first available font.", FontConfig.customFontName);
            fontPos = 0;
        }
        font = availableFonts[fontPos].deriveFont(currentFontQuality);

        HashMap<String, File> packMap = new HashMap<>();
        atlasGroupID = 0;
        for (int j = 0; j < MAX_ATLAS_GROUPS; j++) {
            for (int i = 0; i < ATLAS_COUNT; i++) {
                packMap.put(getAtlasResourceName(i), new File(getAtlasFullPath(i)));
            }
            atlasGroupID++;
        }
        atlasGroupID = 0;

        fontResourcePack = new DefaultResourcePack(packMap);
        List defaultResourcePacks = ((ResourceAccessor) Minecraft.getMinecraft()).angelica$getDefaultResourcePacks();
        defaultResourcePacks.add(fontResourcePack);
        Minecraft.getMinecraft().refreshResources();
    }
    private static class InstLoader { static final FontProviderCustom instance = new FontProviderCustom(); }
    public static FontProviderCustom get() { return InstLoader.instance; }


    public static Logger LOGGER = LogManager.getLogger("Angelica");

    private final DefaultResourcePack fontResourcePack;
    public static final String FONT_DIR = "fonts/custom/";
    private FontAtlas[] fontAtlases = new FontAtlas[ATLAS_COUNT];
    private Font font;
    private float currentFontQuality = FontConfig.customFontQuality;
    private int atlasGroupID = 0;
    private static final int MAX_ATLAS_GROUPS = 64;
    private static final int ATLAS_COUNT = 64;
    private static final int ATLAS_SIZE = 1024;

    public void reloadFont(int fontID, boolean finalReload) {
        currentFontQuality = FontConfig.customFontQuality;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        font = ge.getAllFonts()[fontID].deriveFont(currentFontQuality);

        File[] files = new File(FONT_DIR).listFiles();
        if (files != null) {
            for (File f : files) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    f.delete();
                }
            }
        }
        atlasGroupID++;
        if (finalReload || atlasGroupID == MAX_ATLAS_GROUPS) {
            atlasGroupID = 0;
            Minecraft.getMinecraft().refreshResources();
        }
        fontAtlases = new FontAtlas[ATLAS_COUNT];
    }

    private String getAtlasFilename(int atlasId) {
        return "p" + atlasId + "-" + atlasGroupID;
    }

    private String getAtlasResourceName(int atlasId) {
        return "minecraft:custom_font" + getAtlasFilename(atlasId);
    }

    private String getAtlasFullPath(int atlasId) {
        return FONT_DIR + getAtlasFilename(atlasId) + ".png";
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
            float separator = currentFontQuality / 1.5f;

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
            final int atlasTilesX = (int) Math.ceil(Math.sqrt(actualChars) * 1.25f);
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
            int imgX = 0; // position in pixels

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
                g2d.drawString(Character.toString(ch), imgX, (lineHeight + separator) * (tileY + 1) - separator);
                final float uStart = (float) imgX / imageWidth;
                final float vStart = ((lineHeight + separator) * (tileY + 0.5f) - separator) / imageHeight;
                final float xAdvance = charAspectRatio * 8;
                final float glyphW = charAspectRatio * 8 + 1;
                final float uSz = (float) charWidth / imageWidth;
                final float vSz = (float) lineHeight / imageHeight;
                glyphData[i] = new GlyphData(uStart, vStart, xAdvance, glyphW, uSz, vSz);
                imgX += (int) (charWidth + separator);
                tileX++;
            }
            g2d.dispose();
            try {
                LOGGER.info("writing custom font atlas texture ({}x{} px) to {}", imageWidth, imageHeight, getAtlasFullPath(this.id));
                Files.createDirectories(Paths.get(FONT_DIR));
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
        return font.canDisplay(chr);
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
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].xAdvance;
    }

    @Override
    public float getGlyphW(char chr) {
        return getAtlas(chr).glyphData[chr % ATLAS_SIZE].glyphW;
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
}

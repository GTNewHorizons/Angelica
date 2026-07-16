package com.gtnewhorizons.angelica.client.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizons.angelica.config.FontConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A backported modern hand-drawn bitmap font provider.
 * Et Futurum: Requium provides the resources.
 */
public final class FontProviderBitmap implements SupplementaryGlyphSource {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    private static final ResourceLocation HANDDRAWN_DEFINITION = new ResourceLocation("etfuturum", "font/handdrawn.json");

    private static final float ASCII_CELL = 8.0f;
    private static final float ASCII_ASCENT = 7.0f;

    private final Int2ObjectOpenHashMap<BitmapGlyph> glyphs = new Int2ObjectOpenHashMap<>();
    private final float yScale;
    private final float baselineShift;

    private FontProviderBitmap(int renderHeight, int ascent) {
        this.yScale = renderHeight / ASCII_CELL;
        this.baselineShift = (ASCII_ASCENT - 4.0f) + 4.0f * this.yScale - ascent;
    }

    public static List<FontProviderBitmap> loadProviders() {
        final List<FontProviderBitmap> providers = new ArrayList<>();
        final IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
        try (InputStreamReader reader = new InputStreamReader(
            rm.getResource(HANDDRAWN_DEFINITION).getInputStream(), StandardCharsets.UTF_8)) {
            final JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            final JsonArray defs = root.getAsJsonArray("providers");
            int glyphCount = 0;
            for (int i = 0; i < defs.size(); i++) {
                final FontProviderBitmap p = fromDefinition(defs.get(i).getAsJsonObject(), rm);
                if (p != null && !p.glyphs.isEmpty()) {
                    providers.add(p);
                    glyphCount += p.glyphs.size();
                }
            }
            if (!providers.isEmpty()) {
                LOGGER.info("Loaded {} modern hand-drawn font glyph(s) across {} provider(s)", glyphCount, providers.size());
            }
        } catch (Exception e) {
            LOGGER.debug("Modern hand-drawn font sheets not loaded ({})", e.toString());
        }
        return providers;
    }

    private static FontProviderBitmap fromDefinition(JsonObject def, IResourceManager rm) {
        try {
            final String file = def.get("file").getAsString();
            final int height = def.has("height") ? def.get("height").getAsInt() : (int) ASCII_CELL;
            final int ascent = def.get("ascent").getAsInt();
            final JsonArray charRows = def.getAsJsonArray("chars");

            // "namespace:font/x.png" -> "namespace:textures/font/x.png"
            final int colon = file.indexOf(':');
            final String namespace = colon >= 0 ? file.substring(0, colon) : "minecraft";
            final String path = colon >= 0 ? file.substring(colon + 1) : file;
            final ResourceLocation texture = new ResourceLocation(namespace, "textures/" + path);

            final BufferedImage image = ImageIO.read(rm.getResource(texture).getInputStream());
            final int texW = image.getWidth();
            final int texH = image.getHeight();
            final int rows = charRows.size();
            final String firstRow = charRows.get(0).getAsString();
            final int cols = firstRow.codePointCount(0, firstRow.length());
            final int cellW = texW / cols;
            final int cellH = texH / rows;
            final float scale = (float) height / cellH;

            final FontProviderBitmap provider = new FontProviderBitmap(height, ascent);
            for (int row = 0; row < rows; row++) {
                final String line = charRows.get(row).getAsString();
                int col = 0;
                for (int offset = 0; offset < line.length(); ) {
                    final int cp = line.codePointAt(offset);
                    offset += Character.charCount(cp);
                    if (cp != 0 && !provider.glyphs.containsKey(cp)) {
                        final int cellX = col * cellW;
                        final int cellY = row * cellH;
                        final int ink = inkWidth(image, cellX, cellY, cellW, cellH);
                        final float advance = (int) (0.5F + ink * scale) + 1.0f;
                        final float glyphW = cellW * scale + 1.0f;
                        provider.glyphs.put(cp, new BitmapGlyph(texture,
                            (float) cellX / texW, (float) cellY / texH, (float) cellW / texW, (float) cellH / texH,
                            glyphW, advance));
                    }
                    col++;
                }
            }
            return provider;
        } catch (Exception e) {
            LOGGER.debug("Skipping a hand-drawn font provider ({})", e.toString());
            return null;
        }
    }

    /** Rightmost inked column + 1 within a cell. */
    private static int inkWidth(BufferedImage image, int cellX, int cellY, int cellW, int cellH) {
        for (int x = cellW - 1; x >= 0; x--) {
            for (int y = 0; y < cellH; y++) {
                if ((image.getRGB(cellX + x, cellY + y) >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }

    private int lastCp = -1;
    private BitmapGlyph lastGlyph;

    private BitmapGlyph glyph(int cp) {
        if (cp == this.lastCp && this.lastGlyph != null) {
            return this.lastGlyph;
        }
        final BitmapGlyph g = this.glyphs.get(cp);
        this.lastCp = cp;
        this.lastGlyph = g;
        return g;
    }

    public boolean hasGlyph(int cp) { return this.glyphs.containsKey(cp); }

    @Override
    public float getUStartCp(int cp) { return glyph(cp).uStart(); }

    @Override
    public float getVStartCp(int cp) { return glyph(cp).vStart(); }

    @Override
    public float getXAdvanceCp(int cp) { return glyph(cp).advance(); }

    @Override
    public float getGlyphWCp(int cp) { return glyph(cp).glyphW(); }

    @Override
    public float getUSizeCp(int cp) { return glyph(cp).uSize(); }

    @Override
    public float getVSizeCp(int cp) { return glyph(cp).vSize(); }

    @Override
    public ResourceLocation getTextureCp(int cp) { return glyph(cp).texture(); }

    @Override
    public boolean isUnifont() { return false; }

    @Override
    public boolean isGlyphAvailable(char chr) { return hasGlyph(chr); }

    @Override
    public char getRandomReplacement(char chr) {
        return chr;
    }

    @Override
    public float getUStart(char chr) { return getUStartCp(chr); }

    @Override
    public float getVStart(char chr) { return getVStartCp(chr); }

    @Override
    public float getXAdvance(char chr) { return getXAdvanceCp(chr); }

    @Override
    public float getGlyphW(char chr) { return getGlyphWCp(chr); }

    @Override
    public float getUSize(char chr) { return getUSizeCp(chr); }

    @Override
    public float getVSize(char chr) { return getVSizeCp(chr); }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset;
    }

    @Override
    public ResourceLocation getTexture(char chr) { return getTextureCp(chr); }

    @Override
    public float getYScaleMultiplier() {
        return this.yScale;
    }

    @Override
    public float getBaselineShift() {
        return this.baselineShift;
    }
}

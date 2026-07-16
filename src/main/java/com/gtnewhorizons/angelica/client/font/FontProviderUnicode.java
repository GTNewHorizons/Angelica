package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

public final class FontProviderUnicode implements SupplementaryGlyphSource {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    private FontProviderUnicode() {
    }

    private void loadBmpWidths() {
        try (InputStream in = Minecraft.getMinecraft().getResourceManager()
            .getResource(new ResourceLocation("font/glyph_sizes.bin")).getInputStream()) {
            readWidths(in, this.glyphWidth);
        } catch (IOException e) {
            LOGGER.warn("Failed to load font/glyph_sizes.bin; BMP glyph widths default to 0", e);
        }
    }

    public static void readWidths(InputStream in, byte[] dest) throws IOException {
        int off = 0, n;
        while (off < dest.length && (n = in.read(dest, off, dest.length - off)) != -1) {
            off += n;
        }
    }

    private static class InstLoader { static final FontProviderUnicode instance = new FontProviderUnicode(); }
    public static FontProviderUnicode get() { return FontProviderUnicode.InstLoader.instance; }

    private static final int SUPPLEMENTARY_BASE = 0x10000;

    private static final ResourceLocation[] unicodePageLocations = new ResourceLocation[0x1100];
    private final byte[] glyphWidth = new byte[65536];
    private boolean bmpLoaded;

    private byte[] hiGlyphWidth;
    private boolean hiLoaded;

    private ResourceLocation getUnicodePageLocation(int page) {
        if (page >= 0 && page < unicodePageLocations.length) {
            final ResourceLocation lookup = unicodePageLocations[page];
            if (lookup != null) return lookup;
            // %02x is a minimum width, so supplementary pages still format correctly
            final ResourceLocation rl = new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", page));
            unicodePageLocations[page] = rl;
            return rl;
        }
        return new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", page));
    }

    private int widthByte(int codepoint) {
        if (codepoint < SUPPLEMENTARY_BASE) {
            ensureBmpWidths();
            return this.glyphWidth[codepoint];
        }
        ensureHiWidths();
        final int idx = codepoint - SUPPLEMENTARY_BASE;
        return idx < this.hiGlyphWidth.length ? this.hiGlyphWidth[idx] : 0;
    }

    private void ensureBmpWidths() {
        if (this.bmpLoaded) return;
        this.bmpLoaded = true;
        loadBmpWidths();
    }

    private void ensureHiWidths() {
        if (this.hiLoaded) return;
        this.hiLoaded = true;
        this.hiGlyphWidth = new byte[0];
        try (InputStream in = Minecraft.getMinecraft().getResourceManager()
            .getResource(new ResourceLocation("font/glyph_sizes_hi.bin")).getInputStream()) {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            this.hiGlyphWidth = buf.toByteArray();
        } catch (IOException e) {
            LOGGER.debug("font/glyph_sizes_hi.bin not loaded ({}); supplementary glyph widths default to 0", e.toString());
        }
    }

    public void reload() {
        this.bmpLoaded = false;
        this.hiLoaded = false;
        this.hiGlyphWidth = null;
    }

    public boolean hasCodepoint(int codepoint) {
        return widthByte(codepoint) != 0;
    }

    @Override
    public boolean isUnifont() { return true; }

    @Override
    public boolean isGlyphAvailable(char chr) {
        return true;
    }

    @Override
    public char getRandomReplacement(char chr) {
        return chr;
    }

    public float getUStartCp(int cp) {
        final float startColumnF = (float) ((widthByte(cp) >>> 4) & 15);
        return ((float) ((cp & 15) * 16) + startColumnF + 0.21f) / 256.0f;
    }

    public float getVStartCp(int cp) {
        return ((float) (((cp & 255) >> 4) * 16) + 0.21f) / 256.0f;
    }

    public float getXAdvanceCp(int cp) {
        final int w = widthByte(cp);
        final int startColumn = (w >>> 4) & 15;
        final int endColumn = w & 15;
        return ((endColumn + 1) - startColumn) / 2.0F + 1.0F;
    }

    public float getGlyphWCp(int cp) {
        final int w = widthByte(cp);
        final int startColumn = (w >>> 4) & 15;
        final int endColumn = w & 15;
        final float chrWidth = (endColumn + 1) - startColumn - 0.02F;
        return chrWidth / 2.0f + 1.0f;
    }

    public float getUSizeCp(int cp) {
        final int w = widthByte(cp);
        final int startColumn = (w >>> 4) & 15;
        final int endColumn = w & 15;
        final float chrWidth = (endColumn + 1) - startColumn - 0.02F;
        return (chrWidth - 0.42f) / 256.0f;
    }

    public float getVSizeCp(int cp) {
        return getVSize((char) 0);
    }

    public ResourceLocation getTextureCp(int cp) {
        return getUnicodePageLocation(cp >>> 8);
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
    public float getVSize(char chr) {
        return (16.0f - 0.42f) / 256.0f;
    }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset * FontConfig.fontShadowOffsetUC;
    }

    @Override
    public ResourceLocation getTexture(char chr) {
        return getTextureCp(chr);
    }

    @Override
    public float getYScaleMultiplier() {
        return 1;
    }
}

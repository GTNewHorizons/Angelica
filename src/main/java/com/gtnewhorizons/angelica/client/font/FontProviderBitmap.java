package com.gtnewhorizons.angelica.client.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizons.angelica.config.FontConfig;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import jss.util.RandomXoshiro256StarStar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * For modern Minecraft's hand-drawn font sheets.
 */
public final class FontProviderBitmap implements FontProvider {

    private static final ResourceLocation DEFINITION = new ResourceLocation("angelica", "font/modern.json");
    private static final int DEFAULT_HEIGHT = 8;
    private static final FontProviderBitmap[] NONE = new FontProviderBitmap[0];

    private static volatile FontProviderBitmap[] loaded;

    static {
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(manager -> loaded = null);
    }

    private final ResourceLocation sheet;
    private final int columns;
    private final float uSize;
    private final float vSize;
    private final float glyphW;
    private final float yScale;
    private final float baselineShift;
    private final char[][] slotPages = new char[256][];
    private final int[] advances;
    private final Int2ObjectOpenHashMap<char[]> charsByAdvance = new Int2ObjectOpenHashMap<>();
    private final RandomXoshiro256StarStar fontRandom = new RandomXoshiro256StarStar();

    public static FontProviderBitmap find(char chr) {
        for (FontProviderBitmap provider : providers()) {
            if (provider.slot(chr) >= 0) {
                return provider;
            }
        }
        return null;
    }

    private static FontProviderBitmap[] providers() {
        FontProviderBitmap[] local = loaded;
        if (local == null) {
            synchronized (FontProviderBitmap.class) {
                local = loaded;
                if (local == null) {
                    local = load(Minecraft.getMinecraft().getResourceManager());
                    loaded = local;
                }
            }
        }
        return local;
    }

    private static FontProviderBitmap[] load(IResourceManager resources) {
        final List<FontProviderBitmap> providers = new ArrayList<>();
        try (Reader reader = new InputStreamReader(resources.getResource(DEFINITION).getInputStream(), StandardCharsets.UTF_8)) {
            final JsonArray definitions = new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("providers");
            for (JsonElement element : definitions) {
                final JsonObject definition = element.getAsJsonObject();
                if (!"bitmap".equals(definition.get("type").getAsString())) {
                    continue;
                }
                try {
                    providers.add(new FontProviderBitmap(definition, resources));
                } catch (IOException | RuntimeException e) {
                    FontStrategist.LOGGER.warn("Skipping modern font sheet {}", definition.get("file"), e);
                }
            }
        } catch (IOException | RuntimeException e) {
            FontStrategist.LOGGER.error("Could not read modern font definition {}", DEFINITION, e);
        }
        return providers.toArray(NONE);
    }

    private static ResourceLocation resolveSheet(IResourceManager resources, ResourceLocation sheet) {
        final ResourceLocation bundled = new ResourceLocation("angelica", sheet.getResourcePath());
        try {
            final int vanillaCopies = Minecraft.getMinecraft().mcDefaultResourcePack.resourceExists(sheet) ? 1 : 0;
            final List<IResource> copies = resources.getAllResources(sheet);
            for (IResource copy : copies) {
                copy.getInputStream().close();
            }
            return copies.size() > vanillaCopies ? sheet : bundled;
        } catch (IOException e) {
            return bundled;
        }
    }

    private FontProviderBitmap(JsonObject definition, IResourceManager resources) throws IOException {
        final ResourceLocation file = new ResourceLocation(definition.get("file").getAsString());
        this.sheet = resolveSheet(resources, new ResourceLocation(file.getResourceDomain(), "textures/" + file.getResourcePath()));
        final int height = definition.has("height") ? definition.get("height").getAsInt() : DEFAULT_HEIGHT;
        final int ascent = definition.get("ascent").getAsInt();
        final JsonArray rows = definition.getAsJsonArray("chars");

        final BufferedImage image;
        try (InputStream in = resources.getResource(this.sheet).getInputStream()) {
            image = ImageIO.read(in);
        }
        final String firstRow = rows.get(0).getAsString();
        this.columns = firstRow.codePointCount(0, firstRow.length());
        final int cellWidth = image.getWidth() / this.columns;
        final int cellHeight = image.getHeight() / rows.size();
        final float scale = (float) height / cellHeight;

        this.uSize = (float) cellWidth / image.getWidth();
        this.vSize = (float) cellHeight / image.getHeight();
        this.glyphW = cellWidth * scale + 1.0f;
        this.yScale = (float) height / DEFAULT_HEIGHT;
        this.baselineShift = 3.0f + height / 2.0f - ascent;
        this.advances = new int[this.columns * rows.size()];

        final Int2ObjectOpenHashMap<CharArrayList> byAdvance = new Int2ObjectOpenHashMap<>();
        for (int row = 0; row < rows.size(); row++) {
            final int[] codepoints = rows.get(row).getAsString().codePoints().toArray();
            for (int column = 0; column < codepoints.length; column++) {
                final int codepoint = codepoints[column];
                if (codepoint == 0 || codepoint > Character.MAX_VALUE) {
                    continue;
                }
                final char chr = (char) codepoint;
                final int slot = row * this.columns + column;
                final int ink = inkWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                this.advances[slot] = (int) (0.5f + ink * scale) + 1;

                char[] page = this.slotPages[chr >>> 8];
                if (page == null) {
                    page = this.slotPages[chr >>> 8] = new char[256];
                }
                page[chr & 0xFF] = (char) (slot + 1);
                byAdvance.computeIfAbsent(this.advances[slot], k -> new CharArrayList()).add(chr);
            }
        }
        byAdvance.int2ObjectEntrySet().forEach(e -> this.charsByAdvance.put(e.getIntKey(), e.getValue().toCharArray()));
    }

    private static int inkWidth(BufferedImage image, int cellX, int cellY, int cellWidth, int cellHeight) {
        for (int x = cellWidth - 1; x >= 0; x--) {
            for (int y = 0; y < cellHeight; y++) {
                if ((image.getRGB(cellX + x, cellY + y) >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }

    private int slot(char chr) {
        final char[] page = this.slotPages[chr >>> 8];
        return page == null ? -1 : page[chr & 0xFF] - 1;
    }

    @Override
    public boolean isGlyphAvailable(char chr) {
        return slot(chr) >= 0;
    }

    @Override
    public char getRandomReplacement(char chr) {
        final int slot = slot(chr);
        if (slot < 0) {
            return chr;
        }
        final char[] sameWidth = this.charsByAdvance.get(this.advances[slot]);
        return sameWidth[this.fontRandom.nextInt(sameWidth.length)];
    }

    @Override
    public float getUStart(char chr) {
        return (slot(chr) % this.columns) * this.uSize;
    }

    @Override
    public float getVStart(char chr) {
        return (float) (slot(chr) / this.columns) * this.vSize;
    }

    @Override
    public float getXAdvance(char chr) {
        return this.advances[slot(chr)];
    }

    @Override
    public float getGlyphW(char chr) {
        return this.glyphW;
    }

    @Override
    public float getUSize(char chr) {
        return this.uSize;
    }

    @Override
    public float getVSize(char chr) {
        return this.vSize;
    }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset;
    }

    @Override
    public int getTexture(char chr) {
        return FontStrategist.getIntFromResourceLocation(this.sheet);
    }

    @Override
    public float getYScaleMultiplier() {
        return this.yScale;
    }

    @Override
    public float getBaselineShift() {
        return this.baselineShift;
    }
}

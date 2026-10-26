package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;

final class FontProviderCache {

    private static final byte UNRESOLVED = 0;
    private static final byte MC = 1;
    private static final byte UNICODE = 2;
    private static final byte CUSTOM_PRIMARY = 3;
    private static final byte CUSTOM_FALLBACK = 4;
    private static final byte BITMAP = 5;
    private static final int MAX_BITMAPS = Byte.MAX_VALUE - BITMAP;
    private static final int VARIANT_COUNT = 8;

    private static FontProviderCache[] variants = new FontProviderCache[VARIANT_COUNT];
    private static int generation;

    private final boolean custom;
    private final boolean unicode;
    private final FontProviderBitmap[] bitmaps;
    private final byte[][] pages = new byte[256][];

    private FontProviderCache(boolean custom, boolean unicode, boolean modern) {
        this.custom = custom;
        this.unicode = unicode;
        this.bitmaps = modern && !unicode ? FontProviderBitmap.providers() : FontProviderBitmap.NONE;
    }

    static FontProvider lookup(char chr, boolean custom, boolean unicode) {
        final int variant = (custom ? 4 : 0) | (unicode ? 2 : 0) | (FontConfig.enableModernFont ? 1 : 0);
        final FontProviderCache[] tables = variants;
        FontProviderCache table = tables[variant];
        if (table == null) {
            table = new FontProviderCache(custom, unicode, (variant & 1) != 0);
            tables[variant] = table;
        }
        return table.bitmaps.length > MAX_BITMAPS ? null : table.get(chr);
    }

    static void invalidate() {
        generation++;
        variants = new FontProviderCache[VARIANT_COUNT];
    }

    static int generation() {
        return generation;
    }

    private FontProvider get(char chr) {
        final int hi = chr >>> 8;
        byte[] page = pages[hi];
        if (page == null) {
            page = new byte[256];
            pages[hi] = page;
        }
        final int lo = chr & 0xFF;
        byte id = page[lo];
        if (id == UNRESOLVED) {
            id = resolve(chr);
            page[lo] = id;
        }
        return switch (id) {
            case MC -> FontProviderMC.get(false);
            case UNICODE -> FontProviderUnicode.get();
            case CUSTOM_PRIMARY -> FontProviderCustom.getPrimary();
            case CUSTOM_FALLBACK -> FontProviderCustom.getFallback();
            default -> bitmaps[id - BITMAP];
        };
    }

    private byte resolve(char chr) {
        if (custom) {
            if (FontProviderCustom.getPrimary().isGlyphAvailable(chr)) {
                return CUSTOM_PRIMARY;
            }
            if (FontProviderCustom.getFallback().isGlyphAvailable(chr)) {
                return CUSTOM_FALLBACK;
            }
            final int bitmap = bitmapIndex(chr);
            return bitmap >= 0 ? (byte) (BITMAP + bitmap) : UNICODE;
        }
        final int bitmap = bitmapIndex(chr);
        if (bitmap >= 0) {
            return (byte) (BITMAP + bitmap);
        }
        return !unicode && FontProviderMC.get(false).isGlyphAvailable(chr) ? MC : UNICODE;
    }

    private int bitmapIndex(char chr) {
        for (int i = 0; i < bitmaps.length; i++) {
            if (bitmaps[i].isGlyphAvailable(chr)) {
                return i;
            }
        }
        return -1;
    }
}

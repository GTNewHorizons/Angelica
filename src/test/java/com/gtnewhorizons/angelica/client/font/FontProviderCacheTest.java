package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Font;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@GLCoreTest
class FontProviderCacheTest {

    private static FontTestFixture fixture;
    private static BatchingFontRenderer renderer;
    private static Font primaryBefore;
    private static Font fallbackBefore;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUp() throws Exception {
        fixture = new FontTestFixture(tempDir);
        renderer = mock(BatchingFontRenderer.class);
        primaryBefore = Reflect.get(FontProviderCustom.getPrimary(), "font");
        fallbackBefore = Reflect.get(FontProviderCustom.getFallback(), "font");
        FontProviderCustom.getPrimary().setFont(new Font(Font.SERIF, Font.PLAIN, 16) {
            @Override
            public boolean canDisplay(char c) {
                return (c < '0' || c > '9') && super.canDisplay(c);
            }
        });
        FontProviderCustom.getFallback().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
    }

    @AfterAll
    static void tearDown() {
        FontProviderCustom.getPrimary().setFont(primaryBefore);
        FontProviderCustom.getFallback().setFont(fallbackBefore);
        FontProviderCache.invalidate();
        fixture.close();
    }

    @ParameterizedTest(name = "custom={0} unicode={1} modern={2}")
    @CsvSource({
        "false, false, true", "false, true, true", "false, false, false", "false, true, false",
        "true, false, true", "true, true, true", "true, false, false", "true, true, false" })
    void cacheMatchesTheUncachedSearchAcrossTheBmp(boolean custom, boolean unicode, boolean modern) {
        FontConfig.enableModernFont = modern;
        FontProviderCache.invalidate();
        int primary = 0;
        int fallback = 0;
        for (int c = 0; c <= Character.MAX_VALUE; c++) {
            final char chr = (char) c;
            final FontProvider expected = FontStrategist.searchFontProvider(renderer, chr, custom, unicode);
            if (expected == FontProviderCustom.getPrimary()) primary++;
            if (expected == FontProviderCustom.getFallback()) fallback++;
            assertSame(expected, FontProviderCache.lookup(chr, custom, unicode), "U+" + Integer.toHexString(c));
            assertSame(expected, FontStrategist.getFontProvider(renderer, chr, custom, unicode), "resolved U+" + Integer.toHexString(c));
        }
        assertTrue(!custom || primary > 0, "no glyph resolved to the primary custom font");
        assertTrue(!custom || fallback > 0, "no glyph resolved to the fallback custom font");
    }

    @Test
    void offMainThreadLookupsNeverReturnTheModernFont() throws Exception {
        FontConfig.enableModernFont = true;
        FontProviderCache.invalidate();
        char bitmapChar = 0;
        for (int c = 1; c <= Character.MAX_VALUE && bitmapChar == 0; c++) {
            if (FontStrategist.searchFontProvider(renderer, (char) c, false, false) instanceof FontProviderBitmap) bitmapChar = (char) c;
        }
        assertNotEquals(0, bitmapChar, "no glyph resolves to a modern font sheet");
        final char chr = bitmapChar;
        final FontProvider offThread = CompletableFuture.supplyAsync(() -> FontStrategist.getFontProvider(renderer, chr, false, false)).get();
        assertNotNull(offThread);
        assertFalse(offThread instanceof FontProviderBitmap, "U+" + Integer.toHexString(chr) + " resolved to " + offThread);
    }
}

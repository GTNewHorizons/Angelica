package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GlyphSettingsTest {

    private static boolean cacheOverride;
    private static FontTestFixture fixture;

    @TempDir
    static Path dir;

    @BeforeAll
    static void setUp() throws Exception {
        cacheOverride = HUDCaching.renderingCacheOverride;
        fixture = new FontTestFixture(dir);
    }

    @AfterAll
    static void tearDown() {
        fixture.close();
        HUDCaching.renderingCacheOverride = cacheOverride;
    }

    private static Arguments input(String name, boolean metric, Runnable change) {
        return Arguments.of(Named.of(name, change), metric);
    }

    static Stream<Arguments> inputs() {
        return Stream.of(
            input("generation", true, FontProviderCache::invalidate),
            input("fontAAMode", false, () -> FontConfig.fontAAMode++),
            input("fontAAStrength", false, () -> FontConfig.fontAAStrength++),
            input("shadowCopies", false, () -> FontConfig.shadowCopies++),
            input("boldCopies", false, () -> FontConfig.boldCopies++),
            input("fontShadowOffset", false, () -> FontConfig.fontShadowOffset += 0.5f),
            input("fontShadowOffsetUC", false, () -> FontConfig.fontShadowOffsetUC += 0.5f),
            input("glyphScale", true, () -> FontConfig.glyphScale += 0.5f),
            input("glyphAspect", true, () -> FontConfig.glyphAspect += 0.5f),
            input("glyphSpacing", true, () -> FontConfig.glyphSpacing += 0.5f),
            input("whitespaceScale", true, () -> FontConfig.whitespaceScale += 0.5f),
            input("customFontScale", true, () -> FontConfig.customFontScale += 0.5f),
            input("waveAmplitude", false, () -> AngelicaConfig.waveAmplitude += 0.5f),
            input("enableCustomFont", true, () -> FontConfig.enableCustomFont ^= true),
            input("enableModernFont", true, () -> FontConfig.enableModernFont ^= true),
            input("enableGlyphReplacements", true, () -> FontConfig.enableGlyphReplacements ^= true),
            input("enableRGBColors", true, () -> AngelicaConfig.enableRGBColors ^= true),
            input("enableGradients", true, () -> AngelicaConfig.enableGradients ^= true),
            input("enableRainbow", false, () -> AngelicaConfig.enableRainbow ^= true),
            input("enableWaveText", false, () -> AngelicaConfig.enableWaveText ^= true),
            input("enableDinnerboneText", false, () -> AngelicaConfig.enableDinnerboneText ^= true),
            input("enableDropShadow", true, () -> AngelicaConfig.enableDropShadow ^= true),
            input("hexColorResetsStyles", true, () -> FontRendering.setTextPreprocessor(new FontTestFixture.HexResets())),
            input("renderingCacheOverride", false, () -> HUDCaching.renderingCacheOverride ^= true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inputs")
    void everyInputIsCaptured(Runnable change, boolean metric) {
        final GlyphSettings before = GlyphSettings.current();
        assertSame(before, GlyphSettings.current());
        change.run();
        final GlyphSettings after = GlyphSettings.current();
        assertNotEquals(before, after, "not captured");
        assertEquals(metric, !before.sameMetricsAs(after), "metric relevance");
    }

    @Test
    void eachCacheOverrideStateKeepsItsInstance() {
        final GlyphSettings outside = GlyphSettings.current();
        HUDCaching.renderingCacheOverride ^= true;
        final GlyphSettings inside = GlyphSettings.current();
        HUDCaching.renderingCacheOverride ^= true;
        assertSame(outside, GlyphSettings.current());
        HUDCaching.renderingCacheOverride ^= true;
        assertSame(inside, GlyphSettings.current());
        HUDCaching.renderingCacheOverride ^= true;
    }
}

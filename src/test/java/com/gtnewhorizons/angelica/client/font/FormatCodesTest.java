package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class FormatCodesTest {

    private static final String HEX1 = "&x&1&2&3&4&5&6";
    private static final String HEX2 = "&x&a&b&c&d&e&f";
    private static final String BAD_HEX = "&x&g&g&1&1&1&1";
    private static final char CUSTOM_CODE = 'h';
    private static final int[] SCRAMBLED_WIDTHS = new int[256];

    static {
        Arrays.fill(SCRAMBLED_WIDTHS, 9);
    }

    private static FontTestFixture fixture;
    private static BatchingFontRenderer batcher;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUp() throws Exception {
        fixture = new FontTestFixture(tempDir);
        FontConfig.glyphSpacing = 0.5f;
        FontConfig.whitespaceScale = 1.25f;
        batcher = fixture.batcher();
        if (!FontEffectRegistry.isRegistered(CUSTOM_CODE)) assertTrue(FontEffectRegistry.register(CUSTOM_CODE, (argb, shadowPass, glyphIndex) -> argb));
    }

    @AfterAll
    static void tearDown() {
        fixture.close();
    }

    @AfterEach
    void reset() {
        AngelicaConfig.enableRGBColors = true;
        AngelicaConfig.enableRainbow = true;
        AngelicaConfig.enableWaveText = true;
        AngelicaConfig.enableDinnerboneText = true;
        AngelicaConfig.enableDropShadow = true;
        AngelicaConfig.enableGradients = true;
        FontRendering.setTextPreprocessor(null);
    }

    private static String codes(String s) {
        return s.replace('&', '\u00a7');
    }

    private static long value(long code) {
        return switch (FormatCodes.kind(code)) {
            case FormatCodes.COLOR -> FormatCodes.colorIndex(code);
            case FormatCodes.HEX, FormatCodes.SHADOW_COLOR -> FormatCodes.rgb(code);
            case FormatCodes.GRADIENT -> (long) FormatCodes.gradientFrom(code) << 24 | FormatCodes.gradientTo(code);
            case FormatCodes.CUSTOM -> FormatCodes.customBit(code);
            default -> 0L;
        };
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        HEX1 + "      | HEX           | 12 | 0x123456     | rgb",
        "&X&A&B&C&D&E&F | HEX           | 12 | 0xABCDEF     | rgb",
        BAD_HEX + "   | NONE          | 0  | 0            |",
        "&x&1&2&3&4&5   | NONE          | 0  | 0            |",
        "&x&h&1&2&3&4&5 | NONE          | 0  | 0            |",
        "&h             | CUSTOM        | 0  | 0x80         |",
        "&H             | CUSTOM        | 0  | 0x80         |",
        "&y             | NONE          | 0  | 0            |",
        "&9             | COLOR         | 0  | 9            |",
        "&a             | COLOR         | 0  | 10           |",
        "&F             | COLOR         | 0  | 15           |",
        "&k             | OBFUSCATED    | 0  | 0            |",
        "&L             | BOLD          | 0  | 0            |",
        "&m             | STRIKETHROUGH | 0  | 0            |",
        "&n             | UNDERLINE     | 0  | 0            |",
        "&o             | ITALIC        | 0  | 0            |",
        "&r             | RESET         | 0  | 0            |",
        "&&             | NONE          | 0  | 0            |",
        "&\u4e2d        | NONE          | 0  | 0            |",
        "&q             | RAINBOW       | 0  | 0            | rainbow",
        "&z             | WAVE          | 0  | 0            | wave",
        "&v             | DINNERBONE    | 0  | 0            | dinnerbone",
        "&uabc          | SHADOW_TOGGLE | 0  | 0            | shadow",
        "&u" + HEX1 + " | SHADOW_COLOR  | 14 | 0x123456     | shadow",
        "&u" + BAD_HEX + "| SHADOW_TOGGLE | 0 | 0           |",
        "&g" + HEX1 + HEX2 + "x | GRADIENT | 28 | 0x123456ABCDEF | gradient",
        "&g" + HEX1 + BAD_HEX + " | NONE | 0 | 0               |",
        "&g" + BAD_HEX + HEX2 + " | NONE | 0 | 0               |",
        "&g             | NONE          | 0  | 0            |" })
    void parse(String input, String kind, int extra, String value, String disabledBy) throws ReflectiveOperationException {
        final String s = codes(input);
        final long code = FormatCodes.parse(s, 1, s.length());
        assertEquals(FormatCodes.class.getField(kind).getInt(null), FormatCodes.kind(code), "kind");
        assertEquals(extra, FormatCodes.extraChars(code), "extra chars");
        assertEquals(Long.decode(value), value(code), "payload");
        if (disabledBy == null) return;
        switch (disabledBy) {
            case "rgb" -> AngelicaConfig.enableRGBColors = false;
            case "rainbow" -> AngelicaConfig.enableRainbow = false;
            case "wave" -> AngelicaConfig.enableWaveText = false;
            case "dinnerbone" -> AngelicaConfig.enableDinnerboneText = false;
            case "shadow" -> AngelicaConfig.enableDropShadow = false;
            default -> AngelicaConfig.enableGradients = false;
        }
        assertEquals(FormatCodes.NONE, FormatCodes.kind(FormatCodes.parse(s, 1, s.length())), "kind with " + disabledBy + " off");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "ab&u" + HEX1 + "cd | true  | 4",
        "ab&qcd            | false | 4",
        "ab&qcd            | true  | 2",
        "ab&ncd            | true  | 4",
        "ab" + HEX1 + "cd  | true  | 2",
        "ab&acd            | true  | 2",
        "ab&rcd            | true  | 2" })
    void countVisibleCharsStopsOnlyOnColorTerminators(String input, boolean rainbow, int expected) {
        AngelicaConfig.enableRainbow = rainbow;
        final String s = codes(input);
        final int count = Reflect.invokeStatic(BatchingFontRenderer.class, "countVisibleChars", new Class<?>[] { CharSequence.class, int.class, int.class }, s, 0, s.length());
        assertEquals(expected, count);
    }

    private static final String[] PIECES = {
        "abc", "XYZ", "019", " ", "  ", "\u00a0", "\u202f", "\u4e2d\u6587", "\u00c4", String.valueOf(ColorCodeUtils.ESCAPED_AMPERSAND),
        "&l", "&L", "&r", "&a", "&F", "&k", "&m", "&n", "&o", "&q", "&z", "&v", "&u", "&?", "&" + CUSTOM_CODE, "&y",
        HEX1, HEX2, BAD_HEX, "&u" + HEX1, "&g" + HEX1 + HEX2, "&g" + HEX1 + BAD_HEX, "&x&1", "&" };

    private static float drawn(String s, int start, int end, boolean unicode) {
        batcher.beginBatch();
        try {
            return batcher.drawString(0.0f, 0.0f, 0xFFFFFFFF, false, unicode, s, start, end - start);
        } finally {
            batcher.resetAfterCrash();
        }
    }

    private static void scramble() {
        FontProviderMC.get(false).charWidth = SCRAMBLED_WIDTHS;
    }

    private static float measured(String s, int start, int end, boolean unicode) {
        scramble();
        return batcher.measure(s, start, end, unicode);
    }

    private static float advanced(char c, boolean bold, boolean unicode) {
        scramble();
        return batcher.advance(c, bold, unicode);
    }

    private static float provided(char c, boolean unicode) {
        scramble();
        return batcher.glyphAdvance(batcher.glyphProvider(c, unicode), c, false, batcher.getGlyphScaleX());
    }

    @ParameterizedTest(name = "unicode={0} hexResets={1}")
    @CsvSource({ "false, false", "true, false", "false, true", "true, true" })
    void measureAndAdvanceMatchTheDrawnPen(boolean unicode, boolean hexResets) {
        if (hexResets) FontRendering.setTextPreprocessor(new FontTestFixture.HexResets());
        final Random random = new Random(0x5EED);
        int positive = 0;
        for (int i = 0; i < 200; i++) {
            final StringBuilder sb = new StringBuilder();
            for (int p = 1 + random.nextInt(12); p > 0; p--) sb.append(PIECES[random.nextInt(PIECES.length)]);
            final String s = codes(sb.toString());
            final float pen = drawn(s, 0, s.length(), unicode);
            if (pen > 0.0f) positive++;
            assertEquals(pen, measured(s, 0, s.length(), unicode), 0.0f, s);
            final int start = random.nextInt(s.length());
            final int end = start + 1 + random.nextInt(s.length() - start);
            assertEquals(drawn(s, start, end, unicode), measured(s, start, end, unicode), 0.0f, s + " [" + start + ", " + end + ")");
        }
        assertTrue(positive > 150, "corpus is mostly zero-width: " + positive);
        for (char c : "aZ0 \u00a0\u4e2d\u00a7".toCharArray()) {
            assertEquals(drawn(String.valueOf(c), 0, 1, unicode), advanced(c, false, unicode), 0.0f, "'" + c + "'");
            assertEquals(drawn("\u00a7l" + c, 0, 3, unicode), advanced(c, true, unicode), 0.0f, "bold '" + c + "'");
        }
        for (char c : "aZ0\u4e2d".toCharArray()) {
            assertEquals(drawn(String.valueOf(c), 0, 1, unicode), provided(c, unicode), 0.0f, "provider '" + c + "'");
        }
    }
}

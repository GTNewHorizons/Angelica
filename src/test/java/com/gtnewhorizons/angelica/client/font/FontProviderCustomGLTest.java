package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.Font;
import java.nio.IntBuffer;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class FontProviderCustomGLTest {

    private static final int QUALITY = 30;
    private static final int THREADS = 8;
    private static final char SURROGATE = '\uD800';

    private final Font font = new Font(Font.DIALOG, Font.PLAIN, QUALITY);
    private FontProviderCustom provider;
    private float scaleBefore;

    @BeforeEach
    void setUp() {
        scaleBefore = FontConfig.customFontScale;
        FontConfig.customFontScale = 1.0f;
        provider = new FontProviderCustom(font);
    }

    @AfterEach
    void tearDown() {
        FontConfig.customFontScale = scaleBefore;
        provider.setFont(null);
    }

    private int[] textures() {
        return Reflect.get(provider, "atlasTextures");
    }

    private Object atlas(char chr) {
        return Reflect.<Object[]>get(provider, "fontAtlases")[chr / FontProviderCustom.ATLAS_SIZE];
    }

    @Test
    void metricsFromThreadWithoutContext() throws InterruptedException {
        TestThreads.run("font-no-context", () -> {
            assertTrue(provider.isGlyphAvailable('A'), "glyph not reported available off-thread");
            assertTrue(provider.getXAdvance('A') > 0.0f, "no advance measured off-thread");
        });
        assertArrayEquals(new int[FontProviderCustom.ATLAS_COUNT], textures(), "measuring created a texture");
    }

    @Test
    void getTextureWithoutContextThrows() throws InterruptedException {
        assertTrue(provider.isGlyphAvailable('A'));
        TestThreads.run("font-no-context", () -> assertThrows(IllegalStateException.class, () -> provider.getTexture('A')));
    }

    @Test
    void textureCreatedOnceOnFirstDraw() {
        assertTrue(provider.isGlyphAvailable('A'));
        final int texture = provider.getTexture('A');
        assertNotEquals(0, texture, "no texture created on first draw-path lookup");
        assertTrue(GL11.glIsTexture(texture), "atlas id is not a texture");
        assertEquals(texture, provider.getTexture('A'), "atlas texture recreated on second lookup");
        assertEquals(texture, provider.getTexture('B'), "same block resolved to another texture");
    }

    @Test
    void glyphInkLandsInsideMeasuredRects() {
        assertTrue(provider.isGlyphAvailable('A'));
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, provider.getTexture('A'));
        final int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        final int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        final IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);

        final int margin = QUALITY / 6;
        final boolean[] covered = new boolean[width * height];
        for (int i = 0; i < FontProviderCustom.ATLAS_SIZE; i++) {
            final char chr = (char) i;
            if (!provider.isGlyphAvailable(chr)) continue;
            final int x0 = (int) Math.floor(provider.getUStart(chr) * width);
            final int x1 = (int) Math.ceil((provider.getUStart(chr) + provider.getUSize(chr)) * width);
            final int y0 = (int) Math.floor(provider.getVStart(chr) * height);
            final int y1 = (int) Math.ceil((provider.getVStart(chr) + provider.getVSize(chr)) * height);
            int ink = 0;
            for (int y = Math.max(0, y0 - margin); y < Math.min(height, y1 + margin); y++) {
                for (int x = Math.max(0, x0 - margin); x < Math.min(width, x1 + margin); x++) {
                    covered[y * width + x] = true;
                    if (x >= x0 && x < x1 && y >= y0 && y < y1 && (pixels.get(y * width + x) >>> 24) != 0) ink++;
                }
            }
            if (Character.isLetterOrDigit(chr)) assertTrue(ink > 0, "no ink inside the measured rect of '" + chr + "'");
        }
        int stray = 0;
        for (int i = 0; i < covered.length; i++) {
            if (!covered[i] && (pixels.get(i) >>> 24) != 0) stray++;
        }
        assertEquals(0, stray, "ink outside every measured glyph rect");
    }

    @Test
    void setFontDropsAtlasesAndTextures() {
        assertTrue(provider.isGlyphAvailable('A'));
        final Object atlasBefore = atlas('A');
        assertNotEquals(0, provider.getTexture('A'));

        provider.setFont(font.deriveFont(20.0f));
        assertArrayEquals(new int[FontProviderCustom.ATLAS_COUNT], textures(), "font change kept a texture");
        assertNull(atlas('A'), "font change kept an atlas");
        assertTrue(provider.isGlyphAvailable('A'));
        assertNotSame(atlasBefore, atlas('A'), "atlas not rebuilt for the new font");
        assertTrue(GL11.glIsTexture(provider.getTexture('A')), "no texture for the new font");

        provider.setFont(null);
        assertFalse(provider.isGlyphAvailable('A'), "glyph available with no font set");
        assertArrayEquals(new int[FontProviderCustom.ATLAS_COUNT], textures(), "clearing the font kept a texture");
    }

    @Test
    void concurrentFirstTouchBuildsOneAtlas() throws Exception {
        final CyclicBarrier start = new CyclicBarrier(THREADS);
        final Object[] seen = new Object[THREADS];
        final TestThreads.Worker[] workers = new TestThreads.Worker[THREADS];
        for (int i = 0; i < THREADS; i++) {
            final int slot = i;
            workers[i] = TestThreads.start("font-metrics-race-" + i, () -> {
                start.await();
                assertTrue(provider.isGlyphAvailable('A'));
                seen[slot] = atlas('A');
            });
        }
        for (TestThreads.Worker worker : workers) worker.join();
        assertNotNull(seen[0], "atlas was not published");
        for (int i = 1; i < THREADS; i++) assertSame(seen[0], seen[i], "threads observed different atlases");
    }

    @Test
    void availabilityMatchesFont() {
        for (int i = 0; i < FontProviderCustom.ATLAS_SIZE; i++) {
            assertEquals(font.canDisplay((char) i), provider.isGlyphAvailable((char) i), "availability differs from the font for slot " + i);
            assertEquals(font.canDisplay((char) (SURROGATE + i)), provider.isGlyphAvailable((char) (SURROGATE + i)), "availability differs from the font for surrogate slot " + i);
        }
    }

    @Test
    void randomReplacementKeepsWidth() {
        final float advance = provider.getXAdvance('A');
        for (int i = 0; i < 200; i++) {
            final char replacement = provider.getRandomReplacement('A');
            assertTrue(replacement < FontProviderCustom.ATLAS_SIZE, "replacement left the atlas");
            assertTrue(provider.isGlyphAvailable(replacement), "replacement has no glyph");
            assertEquals(advance, provider.getXAdvance(replacement), 0.0f, "replacement has another width");
        }
    }
}

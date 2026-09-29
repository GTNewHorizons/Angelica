package com.gtnewhorizons.angelica.textures.atlas;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.texture.TextureStaging;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.Random;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasAssemblerStripeTest {

    private static final byte SENTINEL = (byte) 0xCD;

    private static final Class<?> BAND_CLASS = resolveNested("Band");
    private static final Class<?> PLACEMENT_CLASS = resolveNested("Placement");
    private static final Class<?> PLACEMENT_ARRAY_CLASS = Array.newInstance(PLACEMENT_CLASS, 0).getClass();

    private static Class<?> resolveNested(String name) {
        try {
            return Class.forName("com.gtnewhorizons.angelica.textures.atlas.AtlasAssembler$" + name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newBand(int y0, int y1, int width, int levels) {
        try {
            final Constructor<?> ctor = BAND_CLASS.getDeclaredConstructor(int.class, int.class, int.class, int.class);
            ctor.setAccessible(true);
            return ctor.newInstance(y0, y1, width, levels);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newPlacement(int[][] frame, int width, int height, int gutter, int x, int y,
        int paddedWidth, int paddedHeight) {
        try {
            final Constructor<?> ctor = PLACEMENT_CLASS.getDeclaredConstructor(TextureAtlasSprite.class,
                int[][].class, int.class, int.class, int.class, int.class, int.class, int.class, int.class);
            ctor.setAccessible(true);
            return ctor.newInstance(null, frame, width, height, gutter, x, y, paddedWidth, paddedHeight);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object placementArray(Object... placements) {
        final Object array = Array.newInstance(PLACEMENT_CLASS, placements.length);
        for (int i = 0; i < placements.length; i++) {
            Array.set(array, i, placements[i]);
        }
        return array;
    }

    private static void fillStripe(Object band, Object placements, int lo, int hi, int stripeY0, int stripeY1) {
        Reflect.invokeStatic(AtlasAssembler.class, "fillStripe",
            new Class[] { BAND_CLASS, PLACEMENT_ARRAY_CLASS, int.class, int.class, int.class, int.class }, band,
            placements, lo, hi, stripeY0, stripeY1);
    }

    private static int lowerBound(Object placements, int y) {
        return Reflect.invokeStatic(AtlasAssembler.class, "lowerBound",
            new Class[] { PLACEMENT_ARRAY_CLASS, int.class }, placements, y);
    }

    private static boolean covered(boolean[] bandOk, int bandRows, int y0, int y1) {
        return Reflect.invokeStatic(AtlasAssembler.class, "covered",
            new Class[] { boolean[].class, int.class, int.class, int.class }, bandOk, bandRows, y0, y1);
    }

    private static final class FakeStaging implements TextureStaging {

        private final ByteBuffer buffer;
        private final boolean bgra;
        private final int level;

        FakeStaging(ByteBuffer buffer, boolean bgra, int level) {
            this.buffer = buffer;
            this.bgra = bgra;
            this.level = level;
        }

        @Override
        public ByteBuffer buffer() {
            return buffer;
        }

        @Override
        public boolean bgra() {
            return bgra;
        }

        @Override
        public int level() {
            return level;
        }
    }

    private static Object buildBand(int y0, int y1, int width, int levels, boolean bgra) {
        final Object band = newBand(y0, y1, width, levels);
        final TextureStaging[] stagings = Reflect.get(band, "stagings");
        for (int l = 0; l < levels; l++) {
            final int rows = (y1 >> l) - (y0 >> l);
            final int lw = width >> l;
            if (rows <= 0 || lw <= 0) {
                continue;
            }
            final ByteBuffer buffer = ByteBuffer.allocateDirect(lw * rows * 4);
            for (int i = 0; i < buffer.capacity(); i++) {
                buffer.put(i, SENTINEL);
            }
            stagings[l] = new FakeStaging(buffer, bgra, l);
        }
        return band;
    }

    private static byte[] snapshot(Object band, int level) {
        final TextureStaging[] stagings = Reflect.get(band, "stagings");
        final ByteBuffer buffer = stagings[level].buffer();
        final byte[] out = new byte[buffer.capacity()];
        for (int i = 0; i < out.length; i++) {
            out[i] = buffer.get(i);
        }
        return out;
    }

    private static void runStripes(Object band, Object placements, int maxPaddedHeight, int atlasHeight,
        int stripeRows) {
        for (int sy0 = 0; sy0 < atlasHeight; sy0 += stripeRows) {
            final int sy1 = Math.min(atlasHeight, sy0 + stripeRows);
            final int lo = lowerBound(placements, sy0 - maxPaddedHeight + 1);
            final int hi = lowerBound(placements, sy1);
            fillStripe(band, placements, lo, hi, sy0, sy1);
        }
    }

    private static int[][] randomFrame(Random rnd, int width, int height, int levels) {
        final int[][] frame = new int[levels][];
        for (int l = 0; l < levels; l++) {
            final int lw = width >> l;
            final int lh = height >> l;
            final int[] data = new int[lw * lh];
            for (int i = 0; i < data.length; i++) {
                data[i] = rnd.nextInt();
            }
            frame[l] = data;
        }
        return frame;
    }

    private static int swapRedBlue(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
    }

    @Test
    void oneStripeMatchesSplitStripes() {
        final int mipmapLevels = 2;
        final int levels = mipmapLevels + 1;
        final int align = 1 << mipmapLevels;
        final int atlasWidth = 32;
        final int atlasHeight = 32;
        final int maxPaddedHeight = 16;

        final Random rnd = new Random(42);
        final int[][] frameA = randomFrame(rnd, 8, 8, levels);
        final int[][] frameB = randomFrame(rnd, 8, 8, levels);
        final int[][] frameC = randomFrame(rnd, 4, 4, levels);

        final Object placements = placementArray(newPlacement(frameA, 8, 8, 0, 0, 0, 8, 8),
            newPlacement(frameB, 8, 8, 4, 16, 8, 16, 16), newPlacement(frameC, 4, 4, 0, 0, 28, 4, 4));

        for (boolean bgra : new boolean[] { true, false }) {
            final Object whole = buildBand(0, atlasHeight, atlasWidth, levels, bgra);
            fillStripe(whole, placements, lowerBound(placements, 0 - maxPaddedHeight + 1), lowerBound(placements, atlasHeight), 0, atlasHeight);

            final Object twoStripes = buildBand(0, atlasHeight, atlasWidth, levels, bgra);
            runStripes(twoStripes, placements, maxPaddedHeight, atlasHeight, 16);

            final Object alignStripes = buildBand(0, atlasHeight, atlasWidth, levels, bgra);
            runStripes(alignStripes, placements, maxPaddedHeight, atlasHeight, align);

            for (int l = 0; l < levels; l++) {
                final byte[] expected = snapshot(whole, l);
                final int fl = l;
                assertArrayEquals(expected, snapshot(twoStripes, l), () -> "level " + fl + " mismatch, stripeRows=16 bgra=" + bgra);
                assertArrayEquals(expected, snapshot(alignStripes, l), () -> "level " + fl + " mismatch, stripeRows=" + align + " bgra=" + bgra);
            }

            final long addrLevel0 = memAddress(((TextureStaging[]) Reflect.get(whole, "stagings"))[0].buffer());
            final int strideLevel0 = atlasWidth * 4;

            final int pixelA = memGetInt(addrLevel0 + 0L * strideLevel0 + 0L * 4);
            assertEquals(bgra ? frameA[0][0] : swapRedBlue(frameA[0][0]), pixelA, "sprite A texel, bgra=" + bgra);

            final int pixelC = memGetInt(addrLevel0 + 28L * strideLevel0 + 0L * 4);
            assertEquals(bgra ? frameC[0][0] : swapRedBlue(frameC[0][0]), pixelC, "sprite C texel, bgra=" + bgra);

            final int pixelB = memGetInt(addrLevel0 + 12L * strideLevel0 + 20L * 4);
            assertEquals(bgra ? frameB[0][0] : swapRedBlue(frameB[0][0]), pixelB, "sprite B texel, bgra=" + bgra);
        }
    }

    @Test
    void coveredRequiresAllOverlappingBandsOk() {
        final boolean[] bandOk = { true, true, false, true };
        final int bandRows = 8;

        assertTrue(covered(bandOk, bandRows, 0, 8));
        assertTrue(covered(bandOk, bandRows, 8, 16));
        assertFalse(covered(bandOk, bandRows, 0, 24));
        assertFalse(covered(bandOk, bandRows, 15, 20));
        assertTrue(covered(bandOk, bandRows, 24, 32));
    }

    @Test
    void lowerBoundFindsInsertionPoint() {
        final Object placements = placementArray(newPlacement(null, 0, 0, 0, 0, 0, 0, 0),
            newPlacement(null, 0, 0, 0, 0, 4, 0, 0), newPlacement(null, 0, 0, 0, 0, 4, 0, 0),
            newPlacement(null, 0, 0, 0, 0, 9, 0, 0));

        assertEquals(0, lowerBound(placements, -5));
        assertEquals(0, lowerBound(placements, 0));
        assertEquals(1, lowerBound(placements, 1));
        assertEquals(1, lowerBound(placements, 4));
        assertEquals(3, lowerBound(placements, 5));
        assertEquals(3, lowerBound(placements, 9));
        assertEquals(4, lowerBound(placements, 10));
    }
}

package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import me.jellysquid.mods.sodium.client.gui.options.named.BiomeBlendMode;
import org.embeddedt.embeddium.api.util.ColorMixer;

import java.nio.ByteOrder;

public final class BiomeVertexBlender {
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private SmoothBiomeColorCache cache;
    private SmoothBiomeColorCache.ColorType type;
    private int blockX, blockY, blockZ;
    private boolean replacedBlockTint;
    private final int[] cornerColors = new int[8];
    private int populatedCorners;

    public void setup(SmoothBiomeColorCache cache, SmoothBiomeColorCache.ColorType type, int x, int y, int z) {
        this.cache = cache;
        this.type = type;
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
        this.replacedBlockTint = false;
        this.populatedCorners = 0;
    }

    public void clear() {
        cache = null;
        type = null;
        replacedBlockTint = false;
    }

    public boolean isGrass() {
        return type == SmoothBiomeColorCache.ColorType.GRASS;
    }

    public boolean isActive() {
        return replacedBlockTint;
    }

    public static boolean isRendering() {
        if (SmoothBiomeColorCache.getActiveCache() == null || ClientProxy.options().quality.biomeBlendMode != BiomeBlendMode.FANCY) return false;
        final BiomeVertexBlender blender = ((BiomeBlendTessellator) TessellatorManager.get()).angelica$getBiomeBlender();
        return blender != null && blender.isActive();
    }

    public static int blockColor(SmoothBiomeColorCache cache, SmoothBiomeColorCache.ColorType type, int x, int y, int z) {
        if (ClientProxy.options().quality.biomeBlendMode != BiomeBlendMode.FANCY) {
            return cache.getColor(type, x, y, z);
        }
        final BiomeVertexBlender blender = ((BiomeBlendTessellator) TessellatorManager.get()).angelica$getBiomeBlender();
        return blender == null ? cache.getColor(type, x, y, z) : blender.replaceBlockColor(cache, type, x, y, z);
    }

    int replaceBlockColor(SmoothBiomeColorCache cache, SmoothBiomeColorCache.ColorType type, int x, int y, int z) {
        if (this.cache == cache && this.type == type && blockX == x && blockY == y && blockZ == z) {
            replacedBlockTint = true;
            return 0xFFFFFF;
        }
        return cache.getColor(type, x, y, z);
    }

    public int tint(int nativeColor, double x, double y, double z) {
        if (!replacedBlockTint) return nativeColor;
        final int rgb = sampleVertex(x, y, z);
        final int abgr = LITTLE_ENDIAN ? nativeColor : Integer.reverseBytes(nativeColor);
        final int tinted = multiply(abgr, rgb);
        return LITTLE_ENDIAN ? tinted : Integer.reverseBytes(tinted);
    }

    private int sampleVertex(double x, double y, double z) {
        final double dx = x - blockX;
        final double dy = y - blockY;
        final double dz = z - blockZ;
        if ((dx != 0 && dx != 1) || (dy != 0 && dy != 1) || (dz != 0 && dz != 1)) {
            return cache.getVertexColor(type, x, y, z);
        }
        final int corner = (int) dx | ((int) dy << 1) | ((int) dz << 2);
        final int bit = 1 << corner;
        if ((populatedCorners & bit) == 0) {
            cornerColors[corner] = cache.getVertexColor(type, x, y, z);
            populatedCorners |= bit;
        }
        return cornerColors[corner];
    }

    static int vertexColor(SmoothBiomeColorCache cache, SmoothBiomeColorCache.ColorType type, double x, double y, double z) {
        x -= 0.5;
        y -= 0.5;
        z -= 0.5;
        final int ix = (int) Math.floor(x);
        final int iy = (int) Math.floor(y);
        final int iz = (int) Math.floor(z);
        final int c00 = cache.getColor(type, ix, iy, iz);
        final int c01 = cache.getColor(type, ix, iy, iz + 1);
        final int c10 = cache.getColor(type, ix + 1, iy, iz);
        final int c11 = cache.getColor(type, ix + 1, iy, iz + 1);
        final int z0 = c00 == c01 ? c00 : ColorMixer.mix(c01, c00, (float) (z - iz));
        final int z1 = c10 == c11 ? c10 : ColorMixer.mix(c11, c10, (float) (z - iz));
        return z0 == z1 ? z0 : ColorMixer.mix(z1, z0, (float) (x - ix));
    }

    static int multiply(int abgr, int rgb) {
        final int r = (abgr & 255) * ((rgb >> 16) & 255) / 255;
        final int g = ((abgr >> 8) & 255) * ((rgb >> 8) & 255) / 255;
        final int b = ((abgr >> 16) & 255) * (rgb & 255) / 255;
        return (abgr & 0xFF000000) | (b << 16) | (g << 8) | r;
    }
}

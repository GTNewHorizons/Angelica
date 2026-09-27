package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.proxy.ClientProxy;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.NoiseGeneratorPerlin;
import org.embeddedt.embeddium.api.util.ColorMixer;
import org.embeddedt.embeddium.impl.biome.BiomeColorCache;
import org.embeddedt.embeddium.impl.util.color.BoxBlur;
import org.embeddedt.embeddium.impl.util.position.SectionPos;

import java.util.Arrays;

public class SmoothBiomeColorCache extends BiomeColorCache<BiomeGenBase, SmoothBiomeColorCache.ColorType> {
    private static final int NEIGHBOR_RADIUS = 2;
    private static final int SIZE_Y = 16 + NEIGHBOR_RADIUS * 2;
    private static final int COLOR_TYPES = ColorType.values().length;
    private final int radius;
    private final IBlockAccess blockAccess;
    private final BiomeGenBase[] biomes;
    private final Slice[][] slices = new Slice[COLOR_TYPES][SIZE_Y];
    private final BoxBlur.ColorBuffer blurBuffer;
    private boolean biomesPopulated;
    private long generation;
    private int minY;

    private final int sizeXZ;
    private final double[] temperatureNoise;
    private NoiseGeneratorPerlin noiseSource;
    private int minX, minZ, waterY;
    private static final ThreadLocal<SmoothBiomeColorCache> activeCache = new ThreadLocal<>();

    private static final class Slice extends BoxBlur.ColorBuffer {
        private long generation = -1;
        private final boolean[] populated;
        private boolean uniform;
        private int uniformColor;

        private Slice(int size, boolean unblended) {
            super(size, size);
            populated = unblended ? new boolean[size * size] : null;
        }

        private int[] colors() { return data; }
    }

    public static void setActiveCache(SmoothBiomeColorCache cache) {
        activeCache.set(cache);
    }

    public static void clearActiveCache() {
        activeCache.remove();
    }

    public static SmoothBiomeColorCache getActiveCache() {
        return activeCache.get();
    }

    public SmoothBiomeColorCache(IBlockAccess blockAccess) {
        this(blockAccess, configuredRadius());
    }

    SmoothBiomeColorCache(IBlockAccess blockAccess, int radius) {
        super((x, y, z) -> blockAccess.getBiomeGenForCoords(x, z), radius);
        this.radius = radius;
        this.blockAccess = blockAccess;
        this.sizeXZ = SIZE_Y + radius * 2;
        this.temperatureNoise = new double[sizeXZ * sizeXZ];
        this.biomes = radius == 0 ? null : new BiomeGenBase[sizeXZ * sizeXZ];
        this.blurBuffer = radius == 0 ? null : new BoxBlur.ColorBuffer(sizeXZ, sizeXZ);
        Arrays.fill(temperatureNoise, Double.NaN);
    }

    @Override
    public void update(SectionPos origin) {
        super.update(origin);
        minX = origin.minX() - NEIGHBOR_RADIUS - radius;
        minZ = origin.minZ() - NEIGHBOR_RADIUS - radius;
        waterY = origin.minY();
        minY = origin.minY() - NEIGHBOR_RADIUS;
        generation++;
        biomesPopulated = false;
        Arrays.fill(temperatureNoise, Double.NaN);
    }

    @Override
    public int getColor(ColorType type, int x, int y, int z) {
        // Keep the inherited behavior for world-cache callers before the first section update.
        if (generation == 0) return super.getColor(type, x, y, z);
        final int rx = Math.max(minX, Math.min(minX + sizeXZ - 1, x)) - minX;
        final int rz = Math.max(minZ, Math.min(minZ + sizeXZ - 1, z)) - minZ;
        final int ry = type == ColorType.WATER ? 0 : Math.max(minY, Math.min(minY + SIZE_Y - 1, y)) - minY;
        final int index = rz * sizeXZ + rx;
        final Slice slice = slice(type, ry);
        if (radius == 0) {
            if (slice.generation != generation) {
                Arrays.fill(slice.populated, false);
                slice.generation = generation;
            }
            if (!slice.populated[index]) {
                final int wx = minX + rx, wz = minZ + rz;
                slice.colors()[index] = resolveColor(type, blockAccess.getBiomeGenForCoords(wx, wz), wx, type == ColorType.WATER ? waterY : minY + ry, wz);
                slice.populated[index] = true;
            }
        }
        return slice.colors()[index];
    }

    private Slice slice(ColorType type, int ry) {
        final Slice[] typeSlices = slices[type.ordinal()];
        Slice slice = typeSlices[ry];
        if (slice == null) typeSlices[ry] = slice = new Slice(sizeXZ, radius == 0);
        if (radius > 0 && slice.generation != generation) populate(type, ry, slice);
        return slice;
    }

    public int getVertexColor(ColorType type, double x, double y, double z) {
        if (radius == 0 || generation == 0) return BiomeVertexBlender.vertexColor(this, type, x, y, z);
        final int iy = (int) Math.floor(y - 0.5);
        final int ry = type == ColorType.WATER ? 0 : Math.max(minY, Math.min(minY + SIZE_Y - 1, iy)) - minY;
        final Slice slice = slice(type, ry);
        return slice.uniform ? slice.uniformColor : interpolate(slice, x, z);
    }

    private int interpolate(Slice slice, double x, double z) {
        x -= 0.5;
        z -= 0.5;
        final int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        final int x0 = Math.max(minX, Math.min(minX + sizeXZ - 1, ix)) - minX;
        final int x1 = Math.max(minX, Math.min(minX + sizeXZ - 1, ix + 1)) - minX;
        final int z0 = (Math.max(minZ, Math.min(minZ + sizeXZ - 1, iz)) - minZ) * sizeXZ;
        final int z1 = (Math.max(minZ, Math.min(minZ + sizeXZ - 1, iz + 1)) - minZ) * sizeXZ;
        final int[] colors = slice.colors();
        final int c00 = colors[z0 + x0], c01 = colors[z1 + x0];
        final int c10 = colors[z0 + x1], c11 = colors[z1 + x1];
        final int a = c00 == c01 ? c00 : ColorMixer.mix(c01, c00, (float) (z - iz));
        final int b = c10 == c11 ? c10 : ColorMixer.mix(c11, c10, (float) (z - iz));
        return a == b ? a : ColorMixer.mix(b, a, (float) (x - ix));
    }

    private void populate(ColorType type, int ry, Slice slice) {
        if (!biomesPopulated) {
            for (int rz = 0; rz < sizeXZ; rz++) {
                for (int rx = 0; rx < sizeXZ; rx++) {
                    biomes[rz * sizeXZ + rx] = blockAccess.getBiomeGenForCoords(minX + rx, minZ + rz);
                }
            }
            biomesPopulated = true;
        }
        final int worldY = type == ColorType.WATER ? waterY : minY + ry;
        int firstSeenColor = 0;
        boolean uniqueColor = true;
        final int[] colors = slice.colors();
        for (int rz = 0; rz < sizeXZ; rz++) {
            for (int rx = 0; rx < sizeXZ; rx++) {
                final int index = rz * sizeXZ + rx;
                final int color = resolveColor(type, biomes[index], minX + rx, worldY, minZ + rz);
                if (firstSeenColor == 0) firstSeenColor = color;
                else if (firstSeenColor != color) uniqueColor = false;
                colors[index] = color;
            }
        }
        slice.uniform = uniqueColor && colors[0] == firstSeenColor;
        slice.uniformColor = firstSeenColor;
        if (!uniqueColor) BoxBlur.blur(slice, blurBuffer, radius);
        slice.generation = generation;
    }

    public int temperatureNoiseIndex(NoiseGeneratorPerlin source, double x, double z) {
        final double blockX = x * 8.0;
        final double blockZ = z * 8.0;
        final int ix = (int) blockX;
        final int iz = (int) blockZ;
        final int rx = ix - minX;
        final int rz = iz - minZ;
        if (ix != blockX || iz != blockZ || rx < 0 || rz < 0 || rx >= sizeXZ || rz >= sizeXZ) return -1;
        if (noiseSource != source) {
            noiseSource = source;
            Arrays.fill(temperatureNoise, Double.NaN);
        }
        return rz * sizeXZ + rx;
    }

    public double getTemperatureNoise(int index) {
        return temperatureNoise[index];
    }

    public void setTemperatureNoise(int index, double value) {
        temperatureNoise[index] = value;
    }

    public static int configuredRadius() {
        return Math.max(0, Math.min(7, ClientProxy.options().quality.biomeBlendRadius));
    }

    public boolean matchesConfiguredRadius() {
        return radius == configuredRadius();
    }

    @Override
    protected int resolveColor(ColorType type, BiomeGenBase biome, int x, int y, int z) {
        return switch (type) {
            case GRASS -> biome.getBiomeGrassColor(x, y, z);
            case FOLIAGE -> biome.getBiomeFoliageColor(x, y, z);
            case WATER -> biome.getWaterColorMultiplier();
        };
    }

    public enum ColorType {
        GRASS,
        FOLIAGE,
        WATER
    }
}

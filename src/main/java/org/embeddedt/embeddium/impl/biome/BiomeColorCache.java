package org.embeddedt.embeddium.impl.biome;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.util.color.BoxBlur;
import org.embeddedt.embeddium.impl.util.color.BoxBlur.ColorBuffer;
import org.embeddedt.embeddium.impl.util.position.PositionalSupplier;
import org.embeddedt.embeddium.impl.util.position.SectionPos;

public abstract class BiomeColorCache<BIOME, RESOLVER> {
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;
    private final PositionalSupplier<BIOME> biomeData;

    private final Reference2ReferenceOpenHashMap<RESOLVER, Slice[]> slices;
    private long populateStamp;

    private final int blendRadius;

    private final ColorBuffer tempColorBuffer;

    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;

    private final int sizeXZ, sizeY;

    public BiomeColorCache(PositionalSupplier<BIOME> biomeData, int blendRadius) {
        this.biomeData = biomeData;
        this.blendRadius = Math.min(7, blendRadius);

        this.sizeXZ = 16 + ((NEIGHBOR_BLOCK_RADIUS + this.blendRadius) * 2);
        this.sizeY = 16 + (NEIGHBOR_BLOCK_RADIUS * 2);

        this.slices = new Reference2ReferenceOpenHashMap<>();
        this.populateStamp = 1;

        this.tempColorBuffer = new ColorBuffer(sizeXZ, sizeXZ);
    }

    public void update(SectionPos origin) {
        this.minX = (origin.minX() - NEIGHBOR_BLOCK_RADIUS) - this.blendRadius;
        this.minY = (origin.minY() - NEIGHBOR_BLOCK_RADIUS);
        this.minZ = (origin.minZ() - NEIGHBOR_BLOCK_RADIUS) - this.blendRadius;

        this.maxX = (origin.maxX() + NEIGHBOR_BLOCK_RADIUS) + this.blendRadius;
        this.maxY = (origin.maxY() + NEIGHBOR_BLOCK_RADIUS);
        this.maxZ = (origin.maxZ() + NEIGHBOR_BLOCK_RADIUS) + this.blendRadius;

        this.populateStamp++;
    }

    public int getColor(RESOLVER resolver, int blockX, int blockY, int blockZ) {
        var relX = MathUtil.clamp(blockX, this.minX, this.maxX) - this.minX;
        var relY = MathUtil.clamp(blockY, this.minY, this.maxY) - this.minY;
        var relZ = MathUtil.clamp(blockZ, this.minZ, this.maxZ) - this.minZ;

        var sliceArray = this.slices.get(resolver);

        if (sliceArray == null) {
            sliceArray = this.initializeSlices();
            this.slices.put(resolver, sliceArray);
        }

        var slice = sliceArray[relY];

        if (slice.lastPopulateStamp < this.populateStamp) {
            this.updateColorBuffers(relY, resolver, slice);
        }

        var buffer = slice.getBuffer();

        return buffer.get(relX, relZ);
    }

    private Slice[] initializeSlices() {
        var slice = new Slice[this.sizeY];

        for (int y = 0; y < this.sizeY; y++) {
            slice[y] = new Slice(this.sizeXZ);
        }

        return slice;
    }

    protected abstract int resolveColor(RESOLVER resolver, BIOME biome, int relativeX, int relativeY, int relativeZ);

    private void updateColorBuffers(int relY, RESOLVER resolver, Slice slice) {
        int worldY = this.minY + relY;

        //? if <1.15
        /*BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();*/

        int firstSeenColor = 0;

        boolean uniqueColor = true;

        for (int worldZ = this.minZ; worldZ <= this.maxZ; worldZ++) {
            for (int worldX = this.minX; worldX <= this.maxX; worldX++) {
                BIOME biome = this.biomeData.getAt(worldX, worldY, worldZ);

                int relativeX = worldX - this.minX;
                int relativeZ = worldZ - this.minZ;

                int color = this.resolveColor(resolver, biome, worldX, worldY, worldZ);

                if (firstSeenColor == 0) {
                    firstSeenColor = color;
                } else if (firstSeenColor != color) {
                    uniqueColor = false;
                }

                slice.buffer.set(relativeX, relativeZ, color);
            }
        }

        // Skip blurring if all the values are the same anyway
        if (!uniqueColor && this.blendRadius > 0) {
            BoxBlur.blur(slice.buffer, this.tempColorBuffer, this.blendRadius);
        }

        slice.lastPopulateStamp = this.populateStamp;
    }

    private static class Slice {
        private final ColorBuffer buffer;
        private long lastPopulateStamp;

        private Slice(int size) {
            this.buffer = new ColorBuffer(size, size);
        }

        public ColorBuffer getBuffer() {
            return this.buffer;
        }
    }
}

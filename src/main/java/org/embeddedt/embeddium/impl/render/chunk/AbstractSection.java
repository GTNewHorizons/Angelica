package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.util.PositionUtil;

public abstract class AbstractSection {
    // Chunk Section State
    private final int chunkX, chunkY, chunkZ;

    private final int sectionIndex;

    public AbstractSection(int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        int rX = this.getChunkX() & (RenderRegion.REGION_WIDTH - 1);
        int rY = this.getChunkY() & (RenderRegion.REGION_HEIGHT - 1);
        int rZ = this.getChunkZ() & (RenderRegion.REGION_LENGTH - 1);

        this.sectionIndex = LocalSectionIndex.pack(rX, rY, rZ);
    }


    /**
     * @return The x-coordinate of the origin position of this chunk render
     */
    public final int getOriginX() {
        return this.chunkX << 4;
    }

    /**
     * @return The y-coordinate of the origin position of this chunk render
     */
    public final int getOriginY() {
        return this.chunkY << 4;
    }

    /**
     * @return The z-coordinate of the origin position of this chunk render
     */
    public final int getOriginZ() {
        return this.chunkZ << 4;
    }


    /**
     * @return The x-coordinate of the center position of this chunk render
     */
    public final int getCenterX() {
        return this.getOriginX() + 8;
    }

    /**
     * @return The y-coordinate of the center position of this chunk render
     */
    public final int getCenterY() {
        return this.getOriginY() + 8;
    }

    /**
     * @return The z-coordinate of the center position of this chunk render
     */
    public final int getCenterZ() {
        return this.getOriginZ() + 8;
    }

    public final int getChunkX() {
        return this.chunkX;
    }

    public final int getChunkY() {
        return this.chunkY;
    }

    public final int getChunkZ() {
        return this.chunkZ;
    }

    /**
     * @return The squared distance from the center of this chunk in the world to the given position
     */
    public final float getSquaredDistance(float x, float y, float z) {
        float xDist = x - this.getCenterX();
        float yDist = y - this.getCenterY();
        float zDist = z - this.getCenterZ();

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    public final boolean isAlignedWithSectionOnGrid(int otherX, int otherY, int otherZ) {
        return this.chunkX == otherX || this.chunkY == otherY || this.chunkZ == otherZ;
    }

    public final int getSectionIndex() {
        return this.sectionIndex;
    }

    /**
     * @return The squared distance from the center of this chunk in the world to the center of the block position
     * given by {@param pos}
     */
    public final float getSquaredDistanceFromBlockCenter(int x, int y, int z) {
        return this.getSquaredDistance(x + 0.5f, y + 0.5f, z + 0.5f);
    }

    public final long positionAsLong() {
        return PositionUtil.packSection(this.chunkX, this.chunkY, this.chunkZ);
    }

    @Override
    public String toString() {
        return String.format("%s at chunk (%d, %d, %d) from (%d, %d, %d) to (%d, %d, %d)",
                this.getClass().getSimpleName(),
                this.chunkX, this.chunkY, this.chunkZ,
                this.getOriginX(), this.getOriginY(), this.getOriginZ(),
                this.getOriginX() + 15, this.getOriginY() + 15, this.getOriginZ() + 15);
    }
}

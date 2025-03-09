package org.embeddedt.embeddium.impl.render.chunk.lists;

import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.util.iterator.ByteArrayIterator;
import org.embeddedt.embeddium.impl.util.iterator.ByteIterator;
import org.embeddedt.embeddium.impl.util.iterator.ReversibleByteArrayIterator;
import org.jetbrains.annotations.Nullable;

public class ChunkRenderList {
    private final RenderRegion region;

    private final byte[] sectionsWithGeometry = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithGeometryCount = 0;

    private final byte[] sectionsWithSprites = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithSpritesCount = 0;

    private final byte[] sectionsWithEntities = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithEntitiesCount = 0;

    private int size;

    public ChunkRenderList(RenderRegion region) {
        this.region = region;
    }

    public void add(RenderSection render) {
        if (this.size >= RenderRegion.REGION_SIZE) {
            throw new ArrayIndexOutOfBoundsException("Render list is full");
        }

        this.size++;

        int index = render.getSectionIndex();
        int flags = render.getVisualsServiceFlags();

        this.sectionsWithGeometry[this.sectionsWithGeometryCount] = (byte) index;
        this.sectionsWithGeometryCount += (flags >>> RenderVisualsService.HAS_BLOCK_GEOMETRY) & 1;

        this.sectionsWithSprites[this.sectionsWithSpritesCount] = (byte) index;
        this.sectionsWithSpritesCount += (flags >>> RenderVisualsService.HAS_SPRITES) & 1;

        this.sectionsWithEntities[this.sectionsWithEntitiesCount] = (byte) index;
        this.sectionsWithEntitiesCount += (flags >>> RenderVisualsService.HAS_BLOCK_ENTITIES) & 1;
    }

    public @Nullable ByteIterator sectionsWithGeometryIterator(boolean reverse) {
        if (this.sectionsWithGeometryCount == 0) {
            return null;
        }

        return new ReversibleByteArrayIterator(this.sectionsWithGeometry, this.sectionsWithGeometryCount, reverse);
    }

    public @Nullable ByteIterator sectionsWithSpritesIterator() {
        if (this.sectionsWithSpritesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithSprites, this.sectionsWithSpritesCount);
    }

    public @Nullable ByteIterator sectionsWithEntitiesIterator() {
        if (this.sectionsWithEntitiesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithEntities, this.sectionsWithEntitiesCount);
    }

    public int getSectionsWithGeometryCount() {
        return this.sectionsWithGeometryCount;
    }

    public int getSectionsWithSpritesCount() {
        return this.sectionsWithSpritesCount;
    }

    public int getSectionsWithEntitiesCount() {
        return this.sectionsWithEntitiesCount;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public int size() {
        return this.size;
    }

    @Override
    public String toString() {
        var iterator = this.sectionsWithGeometryIterator(false);
        if (iterator == null) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        int originX = this.region.getChunkX();
        int originY = this.region.getChunkY();
        int originZ = this.region.getChunkZ();
        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();
            int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
            int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
            int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);
            sb.append("(").append(chunkX).append(", ").append(chunkY).append(", ").append(chunkZ).append(")");
            if (iterator.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}

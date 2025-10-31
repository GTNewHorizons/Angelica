package me.jellysquid.mods.sodium.client.render.chunk.data;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import com.gtnewhorizons.angelica.compat.mojang.ChunkOcclusionData;
import com.gtnewhorizons.angelica.mixins.interfaces.ISpriteExt;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.gl.util.BufferSlice;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The render data for a chunk render container containing all the information about which meshes are attached, the
 * block entities contained by it, and any data used for occlusion testing.
 */
public class ChunkRenderData {
    public static final ChunkRenderData ABSENT = new Builder()
            .build();
    public static final ChunkRenderData EMPTY = createEmptyData();

    private Set<TileEntity> globalTileEntities;
    private List<TileEntity> tileEntities;

    private EnumMap<BlockRenderPass, ChunkMeshData> meshes;

    @Getter
    private ChunkOcclusionData occlusionData;
    @Getter
    private ChunkRenderBounds bounds;

    @Getter
    private List<TextureAtlasSprite> animatedSprites;

    private boolean isEmpty;
    private int meshByteSize;
    @Getter
    private int facesWithData;

    /**
     * @return True if the chunk has no renderables, otherwise false
     */
    public boolean isEmpty() {
        return this.isEmpty;
    }

    /**
     * The collection of block entities contained by this rendered chunk.
     */
    public Collection<TileEntity> getTileEntities() {
        return this.tileEntities;
    }

    /**
     * The collection of block entities contained by this rendered chunk section which are not part of its culling
     * volume. These entities should always be rendered regardless of the render being visible in the frustum.
     */
    public Collection<TileEntity> getGlobalTileEntities() {
        return this.globalTileEntities;
    }

    /**
     * The collection of chunk meshes belonging to this render.
     */
    public ChunkMeshData getMesh(BlockRenderPass pass) {
        return this.meshes.get(pass);
    }

    public void setMesh(BlockRenderPass pass, ChunkMeshData data) {
        if (this.meshes.get(pass) == null)
            throw new IllegalStateException("No mesh found");
        this.meshes.put(pass, data);
    }

    public int getMeshSize() {
        return this.meshByteSize;
    }

    public ChunkRenderData copyAndReplaceMesh(Map<BlockRenderPass, ChunkMeshData> replacements) {
        ChunkRenderData data = new ChunkRenderData();
        data.globalTileEntities = this.globalTileEntities;
        data.tileEntities = this.tileEntities;
        data.occlusionData = this.occlusionData;
        data.meshes = new EnumMap<>(this.meshes);
        data.bounds = this.bounds;
        data.animatedSprites = new ObjectArrayList<>(this.animatedSprites);
        data.meshes.putAll(replacements);

        int facesWithData = 0;
        int size = 0;

        for (ChunkMeshData meshData : this.meshes.values()) {
            size += meshData.getVertexDataSize();

            for (Map.Entry<ModelQuadFacing, BufferSlice> entry : meshData.getSlices()) {
                facesWithData |= 1 << entry.getKey().ordinal();
            }
        }

        data.isEmpty = this.globalTileEntities.isEmpty() && this.tileEntities.isEmpty() && facesWithData == 0;
        data.meshByteSize = size;
        data.facesWithData = facesWithData;
        return data;
    }

    public static class Builder {
        private final List<TileEntity> globalTileEntities = new ArrayList<>();
        private final List<TileEntity> tileEntities = new ArrayList<>();
        private final Set<TextureAtlasSprite> animatedSprites = new ObjectOpenHashSet<>();

        private final EnumMap<BlockRenderPass, ChunkMeshData> meshes = new EnumMap<>(BlockRenderPass.class);

        private ChunkOcclusionData occlusionData;
        private ChunkRenderBounds bounds = ChunkRenderBounds.ALWAYS_FALSE;

        public Builder() {
            for (BlockRenderPass pass : BlockRenderPass.VALUES) {
                this.setMesh(pass, ChunkMeshData.EMPTY);
            }
        }

        public void setBounds(ChunkRenderBounds bounds) {
            this.bounds = bounds;
        }

        public void setOcclusionData(ChunkOcclusionData data) {
            this.occlusionData = data;
        }

        /**
         * Adds a sprite to this data container for tracking. If the sprite is tickable, it will be ticked every frame
         * before rendering as necessary.
         * @param sprite The sprite
         */
        public void addSprite(TextureAtlasSprite sprite) {
            if (((ISpriteExt) sprite).isAnimation()) {
                this.animatedSprites.add(sprite);
            }
        }

        public void setMesh(BlockRenderPass pass, ChunkMeshData data) {
            this.meshes.put(pass, data);
        }

        /**
         * Adds a block entity to the data container.
         * @param entity The block entity itself
         * @param cull True if the block entity can be culled to this chunk render's volume, otherwise false
         */
        public void addTileEntity(TileEntity entity, boolean cull) {
            (cull ? this.tileEntities : this.globalTileEntities).add(entity);
        }

        public ChunkRenderData build() {
            ChunkRenderData data = new ChunkRenderData();
            data.globalTileEntities = new ObjectOpenHashSet<>(this.globalTileEntities);
            data.tileEntities = this.tileEntities;
            data.occlusionData = this.occlusionData;
            data.meshes = this.meshes;
            data.bounds = this.bounds;
            data.animatedSprites = new ObjectArrayList<>(this.animatedSprites);

            int facesWithData = 0;
            int size = 0;

            for (ChunkMeshData meshData : this.meshes.values()) {
                size += meshData.getVertexDataSize();

                for (Map.Entry<ModelQuadFacing, BufferSlice> entry : meshData.getSlices()) {
                    facesWithData |= 1 << entry.getKey().ordinal();
                }
            }

            data.isEmpty = this.globalTileEntities.isEmpty() && this.tileEntities.isEmpty() && facesWithData == 0;
            data.meshByteSize = size;
            data.facesWithData = facesWithData;

            return data;
        }
    }

    private static ChunkRenderData createEmptyData() {
        ChunkOcclusionData occlusionData = new ChunkOcclusionData();
        occlusionData.addOpenEdgeFaces(EnumSet.allOf(ForgeDirection.class));

        Builder meshInfo = new Builder();
        meshInfo.setOcclusionData(occlusionData);

        return meshInfo.build();
    }
}

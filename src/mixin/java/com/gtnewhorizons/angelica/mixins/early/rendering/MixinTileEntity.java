package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.gtnewhorizons.angelica.mixins.interfaces.ITileEntityBoundingBoxCache;
import com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TileEntity.class)
public abstract class MixinTileEntity implements ITileEntityBoundingBoxCache {
    @Shadow private Block blockType;
    @Shadow private int blockMetadata;
    @Shadow World worldObj;
    @Shadow public int xCoord;
    @Shadow public int yCoord;
    @Shadow public int zCoord;

    @Shadow public abstract AxisAlignedBB getRenderBoundingBox();

    @Unique private int angelica$cachedBBX = Integer.MIN_VALUE;
    @Unique private int angelica$cachedBBY = Integer.MIN_VALUE;
    @Unique private int angelica$cachedBBZ = Integer.MIN_VALUE;
    @Unique private int angelica$cachedBBMetadata = -1;
    @Unique private Block angelica$cachedBBBlockType = null;
    @Unique private AxisAlignedBB angelica$cachedRenderBB = null;
    @Unique private byte angelica$infiniteExtentCached = 0;     // 0 = unchecked, 1 = finite, 2 = infinite

    /**
     * @author mitchej123
     * @reason Make it atomic
     */
    @Overwrite
    public Block getBlockType() {
        Block block = this.blockType;
        if (block == null && this.worldObj != null) {
            this.blockType = block = this.worldObj.getBlock(this.xCoord, this.yCoord, this.zCoord);
        }

        return block;
    }

    /**
     * @author mitchej123
     * @reason Make it atomic
     */
    @Overwrite
    public int getBlockMetadata() {
        int metadata = this.blockMetadata;
        if (metadata == -1 && this.worldObj != null) {
            this.blockMetadata = metadata = this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);
        }

        return metadata;
    }

    @Override
    public AxisAlignedBB angelica$getCachedRenderBoundingBox() {
        if (angelica$cachedRenderBB != null && angelica$cachedBBX == this.xCoord && angelica$cachedBBY == this.yCoord && angelica$cachedBBZ == this.zCoord
                && angelica$cachedBBMetadata == this.blockMetadata && angelica$cachedBBBlockType == this.blockType)
        {
            return angelica$cachedRenderBB;
        }

        final AxisAlignedBB computed = this.getRenderBoundingBox();

        this.angelica$cachedBBX = this.xCoord;
        this.angelica$cachedBBY = this.yCoord;
        this.angelica$cachedBBZ = this.zCoord;
        this.angelica$cachedBBMetadata = this.blockMetadata;
        this.angelica$cachedBBBlockType = this.blockType;

        if (computed == TileEntity.INFINITE_EXTENT_AABB) {
            this.angelica$cachedRenderBB = computed;
        } else if (computed != null) {
            if (this.angelica$cachedRenderBB != null && this.angelica$cachedRenderBB != TileEntity.INFINITE_EXTENT_AABB) {
                this.angelica$cachedRenderBB.setBounds(computed.minX, computed.minY, computed.minZ, computed.maxX, computed.maxY, computed.maxZ);
            } else {
                this.angelica$cachedRenderBB = AxisAlignedBB.getBoundingBox(computed.minX, computed.minY, computed.minZ, computed.maxX, computed.maxY, computed.maxZ);
            }
        } else {
            this.angelica$cachedRenderBB = null;
        }

        return this.angelica$cachedRenderBB;
    }

    @Override
    public boolean angelica$isInfiniteExtent() {
        if (angelica$infiniteExtentCached == 0) {
            final boolean isInfinite = TileEntityRenderBoundsRegistry.isAlwaysInfiniteExtent((TileEntity) (Object) this);
            angelica$infiniteExtentCached = isInfinite ? (byte) 2 : (byte) 1;
        }
        return angelica$infiniteExtentCached == 2;
    }
}

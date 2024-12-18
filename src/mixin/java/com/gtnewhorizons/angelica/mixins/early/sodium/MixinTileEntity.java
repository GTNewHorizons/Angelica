package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.TileEntityExt;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TileEntity.class)
public abstract class MixinTileEntity implements TileEntityExt {

    @Shadow
    public Block blockType;
    @Shadow
    public int blockMetadata;
    @Shadow
    protected World worldObj;
    @Shadow
    public int xCoord;
    @Shadow
    public int yCoord;
    @Shadow
    public int zCoord;
    @Unique
    private int sodium$renderxCoord = -1;
    @Unique
    private int sodium$renderyCoord = -1;
    @Unique
    private int sodium$renderzCoord = -1;
    @Unique
    private AxisAlignedBB sodium$cachedRenderAABB;

    @Shadow
    public abstract AxisAlignedBB getRenderBoundingBox();

    /**
     * @author mitchej123
     * @reason Make it atomic
     */
    @Overwrite
    public Block getBlockType() {
        Block block = this.blockType;
        if (block == null) {
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
        if (metadata == -1) {
            this.blockMetadata = metadata = this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);
        }
        return metadata;
    }

    // cache the render bounding box, otherwise the vanilla code
    // creates a new AxisAlignedBB object everytime and that
    // spams allocations
    @Override
    public AxisAlignedBB sodium$getCachedRenderBoundingBox() {
        if (sodium$cachedRenderAABB == null || xCoord != sodium$renderxCoord || yCoord != sodium$renderyCoord || zCoord != sodium$renderzCoord) {
            sodium$renderxCoord = xCoord;
            sodium$renderyCoord = yCoord;
            sodium$renderzCoord = zCoord;
            sodium$cachedRenderAABB = this.getRenderBoundingBox();
        }
        return sodium$cachedRenderAABB;
    }
}

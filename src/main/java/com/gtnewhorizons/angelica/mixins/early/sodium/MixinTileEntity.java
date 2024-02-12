package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TileEntity.class)
public class MixinTileEntity {
    @Shadow private Block blockType;
    @Shadow private int blockMetadata;
    @Shadow World worldObj;
    @Shadow int xCoord;
    @Shadow int yCoord;
    @Shadow int zCoord;

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
}

package com.gtnewhorizons.angelica.mixins.early.rendering;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityItem.class)
public abstract class MixinEntityItem_RenderDist extends Entity {

    public MixinEntityItem_RenderDist(World worldIn) {
        super(worldIn);
    }

    @Override
    public boolean isInRangeToRender3d(double x, double y, double z) {
        double d3 = this.posX - x;
        double d4 = this.posY - y;
        double d5 = this.posZ - z;
        double d6 = d3 * d3 + d4 * d4 + d5 * d5;
        // set render distance to 32 blocks
        return this.isInRangeToRenderDist(d6 / 4.0D);
    }

}

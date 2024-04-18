package com.gtnewhorizons.angelica.mixins.early.notfine.optimization;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.tileentity.RenderItemFrame;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderItemFrame.class)
public abstract class MixinRenderItemFrame extends Render {

    private EntityItem cachedEntityItem;

    @Redirect(
        method = "func_82402_b(Lnet/minecraft/entity/item/EntityItemFrame;)V",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)Lnet/minecraft/entity/item/EntityItem;",
            ordinal = 0
        )
    )
    public EntityItem cacheEntityItem(World world, double x, double y, double z, ItemStack itemstack) {
        if(cachedEntityItem == null) {
            cachedEntityItem = new EntityItem(world, 0.0D, 0.0D, 0.0D, itemstack);
        } else {
            cachedEntityItem.setEntityItemStack(itemstack);
        }
        return cachedEntityItem;
    }

}

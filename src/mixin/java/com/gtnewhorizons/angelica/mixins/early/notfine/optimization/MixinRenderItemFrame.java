package com.gtnewhorizons.angelica.mixins.early.notfine.optimization;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.tileentity.RenderItemFrame;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItemFrame.class)
public class MixinRenderItemFrame {

    @Unique
    private EntityItem notfine$cachedEntityItem;

    @Redirect(
        method = "func_82402_b(Lnet/minecraft/entity/item/EntityItemFrame;)V",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)Lnet/minecraft/entity/item/EntityItem;",
            ordinal = 0))
    private EntityItem cacheEntityItem(World world, double x, double y, double z, ItemStack itemstack) {
        if (notfine$cachedEntityItem == null) {
            notfine$cachedEntityItem = new EntityItem(world, x, y, z, itemstack);
        } else {
            notfine$cachedEntityItem.setWorld(world);
            notfine$cachedEntityItem.setEntityItemStack(itemstack);
        }
        return notfine$cachedEntityItem;
    }

    @Inject(
        method = "func_82402_b(Lnet/minecraft/entity/item/EntityItemFrame;)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V",
            remap = false,
            shift = At.Shift.AFTER))
    private void clearWorldRef(CallbackInfo ci, @Local(name = "entityitem") EntityItem entityitem) {
        entityitem.setWorld(null);
        entityitem.setEntityItemStack(null);
    }
}

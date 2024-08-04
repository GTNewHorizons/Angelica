package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.client.renderer.entity;

import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.cit.CITUtils;

@Mixin(RenderSnowball.class)
public abstract class MixinRenderSnowball {

    @Redirect(
        method = "doRender(Lnet/minecraft/entity/Entity;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/Item;getIconFromDamage(I)Lnet/minecraft/util/IIcon;"))
    private IIcon modifyDoRender(Item item, int damage, Entity entity) {
        return CITUtils.getEntityIcon(item.getIconFromDamage(damage), entity);
    }
}

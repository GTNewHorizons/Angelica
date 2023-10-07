package com.gtnewhorizons.angelica.mixins.early.accessors;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Invoker
    float invokeGetNightVisionBrightness(EntityPlayer entityPlayer, float partialTicks);

}

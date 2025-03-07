package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import com.gtnewhorizons.angelica.mixins.interfaces.EntityRendererAccessor;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer implements EntityRendererAccessor  {
    @Invoker
    public abstract float invokeGetNightVisionBrightness(EntityPlayer entityPlayer, float partialTicks);

    @Accessor("lightmapTexture")
    public abstract DynamicTexture getLightmapTexture();

}

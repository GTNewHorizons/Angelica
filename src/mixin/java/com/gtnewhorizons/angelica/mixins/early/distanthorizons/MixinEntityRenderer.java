package com.gtnewhorizons.angelica.mixins.early.distanthorizons;

import com.seibel.distanthorizons.interfaces.IMixinEntityRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer implements IMixinEntityRenderer {
    @Override
    @Accessor("lightmapTexture")
    public abstract DynamicTexture getLightmapTexture();
}

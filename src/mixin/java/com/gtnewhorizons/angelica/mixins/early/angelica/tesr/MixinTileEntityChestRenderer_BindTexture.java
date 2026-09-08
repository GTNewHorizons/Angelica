package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import net.minecraft.client.renderer.tileentity.TileEntityChestRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityEnderChestRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = { TileEntityChestRenderer.class, TileEntityEnderChestRenderer.class }, priority = 100)
public abstract class MixinTileEntityChestRenderer_BindTexture extends TileEntitySpecialRenderer {
    // this just provides a default override of bindTexture that other mixins can mixin into
    // in a way that makes it compatible, even if a mixin like this is applied multiple times
    @Unique
    @Override
    protected void bindTexture(ResourceLocation resourceLocation) {
        super.bindTexture(resourceLocation);
    }
}

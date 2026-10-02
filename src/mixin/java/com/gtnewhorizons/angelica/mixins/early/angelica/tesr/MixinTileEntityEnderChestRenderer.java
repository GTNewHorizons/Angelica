package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.tesr.VanillaModelMeshes;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.tileentity.TileEntityEnderChestRenderer;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityEnderChestRenderer.class)
public abstract class MixinTileEntityEnderChestRenderer {
    @Unique
    private ResourceLocation angelica$lastBoundTexture = null;

    @Dynamic(mixin = MixinTileEntityChestRenderer_BindTexture.class)
    @Inject(method = "bindTexture(Lnet/minecraft/util/ResourceLocation;)V", at = @At("HEAD"), require = 1)
    private void angelica$storeBoundTexture(ResourceLocation resourceLocation, CallbackInfo ci) {
        this.angelica$lastBoundTexture = resourceLocation;
    }

    @Surround(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityEnderChest;DDDF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelChest;renderAll()V"))
    private void angelica$cachedRenderAll(ModelChest model, @Surround.Local(argsOnly = true) TileEntityEnderChest chest) {
        @Surround.Skip
        boolean skip = AngelicaConfig.enableTESRChestCache && chest.getWorldObj() != null;
        if (skip && angelica$lastBoundTexture != null) {
            VanillaModelMeshes.renderChest(model, angelica$lastBoundTexture, false);
        }
    }
}

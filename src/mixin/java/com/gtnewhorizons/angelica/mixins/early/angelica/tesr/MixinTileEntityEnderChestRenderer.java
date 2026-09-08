package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.tesr.VanillaModelMeshes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.tileentity.TileEntityEnderChestRenderer;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TileEntityEnderChestRenderer.class)
public abstract class MixinTileEntityEnderChestRenderer {
    @Unique
    private ResourceLocation angelica$lastBoundTexture = null;

    @Dynamic(mixin = MixinTileEntityChestRenderer_BindTexture.class)
    @WrapMethod(method = "bindTexture(Lnet/minecraft/util/ResourceLocation;)V", require = 1)
    private void angelica$storeBoundTexture(ResourceLocation resourceLocation, Operation<Void> original) {
        this.angelica$lastBoundTexture = resourceLocation;
        original.call(resourceLocation);
    }

    @WrapOperation(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityEnderChest;DDDF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelChest;renderAll()V"))
    private void angelica$cachedRenderAll(ModelChest model, Operation<Void> original, @Local(argsOnly = true) TileEntityEnderChest chest) {
        if (!AngelicaConfig.enableTESRChestCache || chest.getWorldObj() == null) {
            original.call(model);
            return;
        }
        if (angelica$lastBoundTexture != null) {
            VanillaModelMeshes.renderChest(model, angelica$lastBoundTexture, false);
        }
    }
}

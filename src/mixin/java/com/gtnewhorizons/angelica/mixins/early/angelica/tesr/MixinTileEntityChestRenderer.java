package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.tesr.VanillaModelMeshes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelLargeChest;
import net.minecraft.client.renderer.tileentity.TileEntityChestRenderer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TileEntityChestRenderer.class)
public abstract class MixinTileEntityChestRenderer {
    @Unique
    private ResourceLocation angelica$lastBoundTexture = null;

    @Dynamic(mixin = MixinTileEntityChestRenderer_BindTexture.class)
    @WrapMethod(method = "bindTexture(Lnet/minecraft/util/ResourceLocation;)V", require = 1)
    private void angelica$storeBoundTexture(ResourceLocation resourceLocation, Operation<Void> original) {
        this.angelica$lastBoundTexture = resourceLocation;
        original.call(resourceLocation);
    }

    @WrapOperation(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityChest;DDDF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelChest;renderAll()V"))
    private void angelica$cachedRenderAll(ModelChest model, Operation<Void> original, @Local(argsOnly = true) TileEntityChest chest) {
        if (!AngelicaConfig.enableTESRChestCache || chest.getWorldObj() == null) {
            original.call(model);
            return;
        }
        final boolean isDouble = model instanceof ModelLargeChest;
        if (angelica$lastBoundTexture != null) {
            VanillaModelMeshes.renderChest(model, angelica$lastBoundTexture, isDouble);
        }
    }
}

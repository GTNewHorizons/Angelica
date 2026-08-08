package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.tesr.VanillaModelMeshes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelLargeChest;
import net.minecraft.client.renderer.tileentity.TileEntityChestRenderer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TileEntityChestRenderer.class)
public abstract class MixinTileEntityChestRenderer {

    @Shadow @Final private static ResourceLocation field_147507_b; // trapped_double
    @Shadow @Final private static ResourceLocation field_147508_c; // christmas_double
    @Shadow @Final private static ResourceLocation field_147505_d; // normal_double
    @Shadow @Final private static ResourceLocation field_147506_e; // trapped
    @Shadow @Final private static ResourceLocation field_147503_f; // christmas
    @Shadow @Final private static ResourceLocation field_147504_g; // normal
    @Shadow private boolean field_147509_j; // Christmas season flag

    @WrapOperation(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityChest;DDDF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelChest;renderAll()V"))
    private void angelica$cachedRenderAll(ModelChest model, Operation<Void> original, @Local(argsOnly = true) TileEntityChest chest) {
        if (!AngelicaConfig.enableTESRChestCache || chest.getWorldObj() == null) {
            original.call(model);
            return;
        }
        final boolean isDouble = model instanceof ModelLargeChest;
        final boolean trapped = chest.func_145980_j() == 1;
        final ResourceLocation texture = isDouble
            ? (trapped ? field_147507_b : field_147509_j ? field_147508_c : field_147505_d)
            : (trapped ? field_147506_e : field_147509_j ? field_147503_f : field_147504_g);
        VanillaModelMeshes.renderChest(model, texture, isDouble);
    }
}

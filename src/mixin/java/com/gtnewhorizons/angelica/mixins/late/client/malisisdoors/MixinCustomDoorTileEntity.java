package com.gtnewhorizons.angelica.mixins.late.client.malisisdoors;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.irisshaders.iris.api.v0.IrisApi;
import net.malisis.doors.door.tileentity.CustomDoorTileEntity;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CustomDoorTileEntity.class, remap = false)
public abstract class MixinCustomDoorTileEntity {

    @ModifyReturnValue(method = "getMaterialRenderPass", at = @At("RETURN"), require = 0)
    private int angelica$applyShaderMaterialPass(int original, Block material) {
        if (material == null || !IrisApi.getInstance().isShaderPackInUse()) return original;
        final var overrides = BlockRenderingSettings.INSTANCE.getBlockTypeIds();
        final var layer = overrides == null ? null : overrides.get(material);
        return layer == null ? original : layer.toVanillaPass();
    }
}

package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntity.class)
public class MixinTileEntityNbtCache {

    @Shadow public int xCoord;
    @Shadow public int yCoord;
    @Shadow public int zCoord;

    @Inject(method = "markDirty", at = @At("HEAD"))
    private void angelica$onMarkDirty(CallbackInfo ci) {
        BlockRenderingSettings.invalidateTeNbtCache(xCoord, yCoord, zCoord);
    }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void angelica$onInvalidate(CallbackInfo ci) {
        BlockRenderingSettings.invalidateTeNbtCache(xCoord, yCoord, zCoord);
    }
}

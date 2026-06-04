package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public class MixinWorldNbtCache {

    @Inject(method = "markBlockForUpdate", at = @At("HEAD"))
    private void angelica$onMarkBlockForUpdate(int x, int y, int z, CallbackInfo ci) {
        BlockRenderingSettings.invalidateTeNbtCache(x, y, z);
    }
}

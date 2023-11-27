package com.gtnewhorizons.angelica.mixins.early.sodium;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldClient.class)
public class MixinWorldClient {
    @Inject(method = "doPreChunk", at = @At("TAIL"))
    private void sodium$loadChunk(int x, int z, boolean load, CallbackInfo ci) {
        if(load)
            SodiumWorldRenderer.getInstance().onChunkAdded(x, z);
    }
}

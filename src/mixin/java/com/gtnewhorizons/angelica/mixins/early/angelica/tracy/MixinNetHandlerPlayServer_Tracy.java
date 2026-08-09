package com.gtnewhorizons.angelica.mixins.early.angelica.tracy;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla onNetworkTick opens startSection("keepAlive") and never ends it.
@Mixin(NetHandlerPlayServer.class)
public class MixinNetHandlerPlayServer_Tracy {
    @Shadow @Final private MinecraftServer serverController;

    @Inject(method = "onNetworkTick", at = @At("RETURN"))
    private void angelica$tracyBalanceKeepAlive(CallbackInfo ci) {
        this.serverController.theProfiler.endSection();
    }
}

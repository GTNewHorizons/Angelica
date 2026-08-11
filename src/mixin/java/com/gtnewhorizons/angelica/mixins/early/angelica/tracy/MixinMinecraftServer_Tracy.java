package com.gtnewhorizons.angelica.mixins.early.angelica.tracy;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer_Tracy {
    private static final long P_MSPT = Tracy.plotHandle("mspt");
    private static final long P_ALLOC_RATE_SV = Tracy.plotHandle("allocRateSv", TracyBackend.PLOT_FORMAT_MEMORY);

    @Shadow private int tickCounter;

    @Inject(method = "tick", at = @At("RETURN"))
    private void angelica$tracyServerTick(CallbackInfo ci) {
        Tracy.frameMark("server tick");
        Tracy.plot(P_MSPT, ((MinecraftServer) (Object) this).tickTimeArray[tickCounter % 100] / 1.0e6);
        Tracy.plotAllocRate(P_ALLOC_RATE_SV);
    }
}

package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.network.NetHandlerPlayServer;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NetHandlerPlayServer.class)
public class MixinNetHandlerPlayServer {
    @ModifyArg(method = "processPlayerBlockPlacement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayerMP;getDistanceSq(DDD)D", ordinal = 0), index = 1)
    private double adjustForEyeHeight(double y) {
        if(ArchaicConfig.fixPlacementFlicker)
            return y - 1.5;
        else
            return y;
    }
}

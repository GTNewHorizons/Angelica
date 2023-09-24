package com.gtnewhorizons.angelica.mixins.early.archaic.client.core;

import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.NetworkManager;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerLoginClient.class)
public class MixinNetHandlerLoginClient {
    @Shadow @Final private NetworkManager field_147393_d;

    @Inject(method = "handleLoginSuccess", at=@At("RETURN"))
    public void archaic_raceConditionWorkAround(CallbackInfo cb) {
        if(ArchaicConfig.fixLoginRaceCondition)
            field_147393_d.channel().config().setAutoRead(true);
    }
}

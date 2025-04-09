package com.gtnewhorizons.angelica.mixins.early.angelica.settings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.server.management.PlayerManager;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {

    @ModifyArgs(method = "func_152622_a(I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;clamp_int(III)I"))
    public void clamp_int(Args args) {
        args.set(2, 128);
    }

}

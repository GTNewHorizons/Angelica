package com.gtnewhorizons.angelica.mixins.early.archaic.client.core;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.server.integrated.IntegratedServer;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
public class MixinIntegratedServer {
    /**
     * Force the integrated server to have a minimum view distance of 8, so mob spawning works correctly.
     */
    @Redirect(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"))
    private int getRealRenderDistance(GameSettings settings) {
        if(ArchaicConfig.fixMobSpawnsAtLowRenderDist)
            return Math.max(settings.renderDistanceChunks, 8);
        else
            return settings.renderDistanceChunks;
    }
}

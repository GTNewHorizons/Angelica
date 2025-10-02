package com.embeddedt.chunkbert.mixin;

import com.embeddedt.chunkbert.ChunkbertConfig;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.server.integrated.IntegratedServer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
    @Redirect(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"))
    private int getOverrideViewDistance(GameSettings instance) {
        int overwrite = ChunkbertConfig.viewDistanceOverwrite;
        if (overwrite != 0) {
            return overwrite;
        }
        return instance.renderDistanceChunks;
    }
}

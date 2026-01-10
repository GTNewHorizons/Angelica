package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasDebug;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_ChunkUpdates {
    @Redirect(method = "runGameLoop", at = @At(opcode = Opcodes.GETSTATIC, value = "FIELD",
            target = "Lnet/minecraft/client/renderer/WorldRenderer;chunksUpdated:I", ordinal = 0))
    private int celeritas$properChunkUpdateCounter() {
        return CeleritasDebug.readAndResetChunkUpdateCounter();
    }
}

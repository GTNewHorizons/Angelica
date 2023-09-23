package org.embeddedt.archaicfix.mixins.common.lighting.fastcraft;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkProviderServer.class)
public class MixinChunkProviderServer {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lfastcraft/H;a(Lnet/minecraft/world/chunk/Chunk;)V", remap = false), require = 0)
    private void usePhosphorLightPopulation(Chunk chunk) {
        chunk.func_150809_p();
    }
}

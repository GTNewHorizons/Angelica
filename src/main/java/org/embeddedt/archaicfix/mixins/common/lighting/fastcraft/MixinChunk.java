package org.embeddedt.archaicfix.mixins.common.lighting.fastcraft;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Chunk.class)
public abstract class MixinChunk {
    @Shadow protected abstract void relightBlock(int p_76615_1_, int p_76615_2_, int p_76615_3_);

    @Shadow protected abstract void recheckGaps(boolean p_150803_1_);

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lfastcraft/H;c(Lnet/minecraft/world/chunk/Chunk;III)V", remap = false), require = 0)
    private void doBlockLight(Chunk chunk, int x, int y, int z) {
        this.relightBlock(x, y, z);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lfastcraft/H;a(Lnet/minecraft/world/chunk/Chunk;)V", remap = false), require = 0)
    private void usePhosphorLightPopulation(Chunk chunk) {
        chunk.func_150809_p();
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lfastcraft/H;b(Lnet/minecraft/world/chunk/Chunk;Z)V", remap = false), require = 0)
    private void usePhosphorRecheckGaps(Chunk chunk, boolean isRemote) {
        this.recheckGaps(isRemote);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lfastcraft/H;d(Lnet/minecraft/world/World;III)Z", remap = false), require = 0)
    private boolean updateLightUsingPhosphor(World world, int x, int y, int z) {
        return world.func_147451_t(x, y, z);
    }
}

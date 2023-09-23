package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderHell;
import net.minecraft.world.gen.feature.WorldGenHellLava;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

@Mixin(ChunkProviderHell.class)
public class MixinChunkProviderHell {
    @Redirect(method = "populate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/feature/WorldGenHellLava;generate(Lnet/minecraft/world/World;Ljava/util/Random;III)Z", ordinal = 1))
    private boolean applyOffsetIfConfigured(WorldGenHellLava gen, World world, Random random, int x, int y, int z) {
        int offset = ArchaicConfig.fixVanillaCascadingWorldgen ? 8 : 0;
        return gen.generate(world, random, x + offset, y, z + offset);
    }
}

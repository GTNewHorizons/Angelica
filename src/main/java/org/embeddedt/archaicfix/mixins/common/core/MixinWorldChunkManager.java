package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeCache;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.layer.GenLayer;
import org.embeddedt.archaicfix.ArchaicLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunkManager.class)
public class MixinWorldChunkManager {
    @Shadow private BiomeCache biomeCache;

    @Shadow private GenLayer genBiomes;

    @Shadow private GenLayer biomeIndexLayer;

    private World clientWorld = null;

    @Inject(method = "<init>(Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void noCacheOnClient(World world, CallbackInfo ci) {
        if(world.isRemote) {
            /* Make sure the client NEVER uses these */
            this.biomeCache = null;
            this.genBiomes = null;
            this.biomeIndexLayer = null;
            clientWorld = world;
        }
    }

    @Inject(method = "getBiomeGenAt(II)Lnet/minecraft/world/biome/BiomeGenBase;", at = @At("HEAD"), cancellable = true)
    private void safelyGetClientBiome(int x, int z, CallbackInfoReturnable<BiomeGenBase> cir) {
        if(this.biomeCache == null) {
            if(clientWorld != null)
                cir.setReturnValue(clientWorld.getBiomeGenForCoords(x, z));
            else {
                ArchaicLogger.LOGGER.warn("A mod attempted to retrieve a biome client-side via a WorldChunkManager without a client world reference. This is not safe. ArchaicFix has prevented a crash but some things may not work properly.");
                cir.setReturnValue(BiomeGenBase.ocean);
            }
        }
    }
}

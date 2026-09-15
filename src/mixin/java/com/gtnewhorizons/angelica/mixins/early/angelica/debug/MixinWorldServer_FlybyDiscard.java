package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldServer.class)
public abstract class MixinWorldServer_FlybyDiscard {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunkProvider;unloadQueuedChunks()Z"))
    private boolean angelica$holdChunksWhileDiscarding(IChunkProvider provider) {
        return !FlybyRunner.worldChangesDiscarded() && provider.unloadQueuedChunks();
    }
}

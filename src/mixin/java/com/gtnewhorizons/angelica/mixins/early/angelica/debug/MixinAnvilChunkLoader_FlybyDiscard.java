package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader_FlybyDiscard {

    @Inject(method = "saveChunk", at = @At("HEAD"), cancellable = true)
    private void angelica$flybyDiscardChunk(World world, Chunk chunk, CallbackInfo ci) {
        if (FlybyRunner.worldChangesDiscarded()) ci.cancel();
    }
}

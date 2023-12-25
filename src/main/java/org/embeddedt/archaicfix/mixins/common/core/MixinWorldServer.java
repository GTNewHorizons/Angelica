package org.embeddedt.archaicfix.mixins.common.core;

import com.google.common.collect.Iterators;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ISaveHandler;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.Set;

@Mixin(WorldServer.class)
public abstract class MixinWorldServer extends World {
    @Shadow public ChunkProviderServer theChunkProviderServer;

    public MixinWorldServer(ISaveHandler p_i45368_1_, String p_i45368_2_, WorldProvider p_i45368_3_, WorldSettings p_i45368_4_, Profiler p_i45368_5_) {
        super(p_i45368_1_, p_i45368_2_, p_i45368_3_, p_i45368_4_, p_i45368_5_);
    }

    @ModifyConstant(method = "tickUpdates", constant = @Constant(intValue = 1000), expect = 2, require = 0)
    private int increaseUpdateLimit(int old) {
        return ArchaicConfig.increaseBlockUpdateLimit ? 65000 : old;
    }

    @Inject(method = "func_152379_p", at = @At("RETURN"), cancellable = true)
    private void shortenBlockUpdateDistance(CallbackInfoReturnable<Integer> cir) {
        if(ArchaicConfig.optimizeBlockTickingDistance > 0) {
            cir.setReturnValue(Math.min(cir.getReturnValue(), ArchaicConfig.optimizeBlockTickingDistance));
        }
    }
}

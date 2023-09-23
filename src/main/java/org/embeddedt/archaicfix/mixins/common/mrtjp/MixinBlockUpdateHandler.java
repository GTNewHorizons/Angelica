package org.embeddedt.archaicfix.mixins.common.mrtjp;

import mrtjp.core.world.BlockUpdateHandler$;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;

@Mixin(BlockUpdateHandler$.class)
public class MixinBlockUpdateHandler {
    @Redirect(method = "getActiveChunkSet", at = @At(value = "INVOKE", target = "Ljava/util/HashSet;add(Ljava/lang/Object;)Z"), remap = false)
    private boolean addChunkIfLoaded(HashSet chunkSet, Object o, World world) {
        ChunkCoordIntPair pair = (ChunkCoordIntPair)o;
        if(world.getChunkProvider().chunkExists(pair.chunkXPos, pair.chunkZPos))
            return chunkSet.add(o);
        return false;
    }
}

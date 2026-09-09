package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stop blocks from ticking.
 */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer_FlybyFreeze {

    @Redirect(
        method = "func_147456_g",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;getNeedsRandomTick()Z"))
    private boolean angelica$skipRandomBlockTicks(ExtendedBlockStorage storage) {
        return !FlybyRunner.blockTicksFrozen() && storage.getNeedsRandomTick();
    }
}

package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freeze anything but the player for better, more reproducible runs.
 */
@Mixin(World.class)
public abstract class MixinWorld_FlybyFreeze {

    @Inject(method = "updateEntityWithOptionalForce", at = @At("HEAD"), cancellable = true)
    private void angelica$flybyFreeze(Entity entity, boolean force, CallbackInfo ci) {
        if (FlybyRunner.entitiesFrozen() && !(entity instanceof EntityPlayer)) {
            ci.cancel();
        }
    }
}

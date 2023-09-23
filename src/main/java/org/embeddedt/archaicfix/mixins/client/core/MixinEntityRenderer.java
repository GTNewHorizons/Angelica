package org.embeddedt.archaicfix.mixins.client.core;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityRenderer.class, priority = 1500)
public abstract class MixinEntityRenderer {
    /** @reason Makes the third-person view camera pass through non-solid blocks (fixes https://bugs.mojang.com/browse/MC-30845) */
    @Redirect(method = "orientCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;rayTraceBlocks(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;"))
    private MovingObjectPosition rayTraceBlocks(WorldClient world, Vec3 from, Vec3 to) {
        return world.func_147447_a(from, to, false, true, true);
    }
}

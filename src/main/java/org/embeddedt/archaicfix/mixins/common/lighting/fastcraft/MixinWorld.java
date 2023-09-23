package org.embeddedt.archaicfix.mixins.common.lighting.fastcraft;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
public class MixinWorld {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lfastcraft/H;d(Lnet/minecraft/world/World;III)Z", remap = false), require = 0)
    private boolean updateLightUsingPhosphor(World world, int x, int y, int z) {
        return world.func_147451_t(x, y, z);
    }
}

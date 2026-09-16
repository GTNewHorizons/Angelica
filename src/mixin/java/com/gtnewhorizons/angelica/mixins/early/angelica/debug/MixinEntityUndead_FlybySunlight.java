package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.debug.flyby.FlybyRunner;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = { EntityZombie.class, EntitySkeleton.class })
public abstract class MixinEntityUndead_FlybySunlight {

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isDaytime()Z"))
    private boolean angelica$suppressSunlightBurn(World world) {
        return !FlybyRunner.sceneGuarded() && world.isDaytime();
    }
}

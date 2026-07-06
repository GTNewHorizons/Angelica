package com.gtnewhorizons.angelica.mixins.early.notfine.particles;

import java.util.Random;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jss.notfine.core.Settings;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import jss.util.RandomXoshiro256StarStar;
import net.minecraft.client.multiplayer.WorldClient;

@Mixin(value = WorldClient.class)
public abstract class MixinWorldClient {

	/**
	 * @author jss2a98aj
	 * @reason Xoshiro256** is faster than Random.
	 */
	@Redirect(
        method = "doVoidFogParticles(III)V",
        at = @At(
            value = "NEW",
            target = "()Ljava/util/Random;",
            ordinal = 0
        )
    )
	private Random notFine$redirectDoVoidFogParticlesRandom() {
		return new RandomXoshiro256StarStar();
	}

    /**
     * @author jss2a98aj
     * @reason Toggle void particles.
     */
    @WrapOperation(
        method = "doVoidFogParticles(III)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/WorldProvider;getWorldHasVoidParticles()Z"
        )
    )
    private boolean notFine$toggleVoidParticles(WorldProvider provider, Operation<Boolean> original){
        return ((boolean) Settings.PARTICLES_VOID.option.getStore()) ? original.call(provider) : false;
    }

}

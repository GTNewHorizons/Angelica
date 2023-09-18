package jss.notfine.mixins.early.minecraft.particles;

import java.util.Random;

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
	@Redirect(method = "doVoidFogParticles", at = @At(value = "NEW", target = "java/util/Random", ordinal = 0))
	private Random notFine$redirectDoVoidFogParticlesRandom() {
		return new RandomXoshiro256StarStar();
	}

}

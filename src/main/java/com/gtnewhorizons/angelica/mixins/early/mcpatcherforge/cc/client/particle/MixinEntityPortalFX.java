package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.particle;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityPortalFX;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.cc.ColorizeEntity;

@Mixin(EntityPortalFX.class)
public abstract class MixinEntityPortalFX extends EntityFX {

    @Shadow
    private float portalParticleScale;

    protected MixinEntityPortalFX(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDDDD)V", at = @At("RETURN"))
    private void modifyConstructor(World world, double x, double y, double z, double motionX, double motionY,
        double motionZ, CallbackInfo ci) {
        // green & red get multiplied in constructor, blue doesn't
        this.particleGreen = this.portalParticleScale / 0.3f;
        this.particleGreen *= ColorizeEntity.portalColor[1];
        this.particleRed = this.portalParticleScale / 0.9f;
        this.particleRed *= ColorizeEntity.portalColor[0];
        this.particleBlue = ColorizeEntity.portalColor[2];
    }
}

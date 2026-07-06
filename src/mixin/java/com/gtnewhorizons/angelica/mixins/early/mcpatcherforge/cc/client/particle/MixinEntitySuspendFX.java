package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.particle;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntitySuspendFX;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.cc.ColorizeEntity;
import com.prupe.mcpatcher.cc.Colorizer;

@Mixin(EntitySuspendFX.class)
public abstract class MixinEntitySuspendFX extends EntityFX {

    protected MixinEntitySuspendFX(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDDDD)V", at = @At("RETURN"))
    private void modifyConstructor(World world, double x, double y, double z, double motionX, double motionY,
        double motionZ, CallbackInfo ci) {
        ColorizeEntity.computeSuspendColor(6710962, (int) x, (int) y, (int) z);
        this.particleRed = Colorizer.setColor[0];
        this.particleGreen = Colorizer.setColor[1];
        this.particleBlue = Colorizer.setColor[2];
    }
}

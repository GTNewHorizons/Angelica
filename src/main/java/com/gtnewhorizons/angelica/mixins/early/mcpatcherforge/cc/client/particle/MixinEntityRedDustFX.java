package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.particle;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityReddustFX;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.cc.ColorizeBlock;
import com.prupe.mcpatcher.cc.Colorizer;

@Mixin(EntityReddustFX.class)
public abstract class MixinEntityRedDustFX extends EntityFX {

    @Shadow
    float reddustParticleScale;

    protected MixinEntityRedDustFX(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDFFFF)V", at = @At("RETURN"))
    private void modifyConstructor(World world, double x, double y, double z, float p_i1224_8_, float red, float green,
        float blue, CallbackInfo ci) {
        // == 1.0f is needed as this runs after the rest of the constructor
        if (red == 0.0F || red == 1.0f) {
            red = 1.0F;
            // Injected block
            if (ColorizeBlock.computeRedstoneWireColor(15)) {
                red = Colorizer.setColor[0];
                green = Colorizer.setColor[1];
                blue = Colorizer.setColor[2];
            }
        }

        float f4 = (float) Math.random() * 0.4F + 0.6F;
        this.particleRed = ((float) (Math.random() * 0.20000000298023224D) + 0.8F) * red * f4;
        this.particleGreen = ((float) (Math.random() * 0.20000000298023224D) + 0.8F) * green * f4;
        this.particleBlue = ((float) (Math.random() * 0.20000000298023224D) + 0.8F) * blue * f4;
        this.particleScale *= 0.75F;
        this.particleScale *= p_i1224_8_;
        this.reddustParticleScale = this.particleScale;
        this.particleMaxAge = (int) (8.0D / (Math.random() * 0.8D + 0.2D));
        this.particleMaxAge = (int) ((float) this.particleMaxAge * p_i1224_8_);
        this.noClip = false;
    }
}

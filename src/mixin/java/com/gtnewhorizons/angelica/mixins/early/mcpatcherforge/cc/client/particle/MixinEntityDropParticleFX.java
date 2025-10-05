package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.particle;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.particle.EntityDropParticleFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.cc.ColorizeBlock;
import com.prupe.mcpatcher.cc.ColorizeEntity;
import com.prupe.mcpatcher.cc.Colorizer;

@Mixin(EntityDropParticleFX.class)
public abstract class MixinEntityDropParticleFX extends EntityFX {

    @Shadow
    private Material materialType;

    @Shadow
    private int bobTimer;

    protected MixinEntityDropParticleFX(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(
        method = "<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/block/material/Material;)V",
        at = @At("RETURN"))
    private void modifyConstructor(World worldIn, double x, double y, double z, Material material, CallbackInfo ci) {
        if (material == Material.water) {
            if (ColorizeBlock.computeWaterColor(false, (int) this.posX, (int) this.posY, (int) this.posZ)) {
                this.particleRed = Colorizer.setColor[0];
                this.particleGreen = Colorizer.setColor[1];
                this.particleBlue = Colorizer.setColor[2];
            } else {
                this.particleRed = 0.2f;
                this.particleGreen = 0.3f;
                this.particleBlue = 1.0f;
            }
        }
    }

    /**
     * @author Mist475 (adapted from Paul Rupe)
     * @reason Inject would be too inefficient
     */
    @Override
    @SuppressWarnings("DuplicatedCode")
    @Overwrite
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        // Patch start
        if (this.materialType != Material.water) {
            if (ColorizeEntity.computeLavaDropColor(40 - this.bobTimer)) {
                this.particleRed = Colorizer.setColor[0];
                this.particleGreen = Colorizer.setColor[1];
                this.particleBlue = Colorizer.setColor[2];
            } else {
                this.particleRed = 1.0f;
                this.particleGreen = 16.0f / (40 - this.bobTimer + 16);
                this.particleBlue = 4.0f / (40 - this.bobTimer + 8);
            }
        }
        // Patch end

        this.motionY -= this.particleGravity;

        if (this.bobTimer-- > 0) {
            this.motionX *= 0.02D;
            this.motionY *= 0.02D;
            this.motionZ *= 0.02D;
            this.setParticleTextureIndex(113);
        } else {
            this.setParticleTextureIndex(112);
        }

        this.moveEntity(this.motionX, this.motionY, this.motionZ);
        this.motionX *= 0.9800000190734863D;
        this.motionY *= 0.9800000190734863D;
        this.motionZ *= 0.9800000190734863D;

        if (this.particleMaxAge-- <= 0) {
            this.setDead();
        }

        if (this.onGround) {
            if (this.materialType == Material.water) {
                this.setDead();
                this.worldObj.spawnParticle("splash", this.posX, this.posY, this.posZ, 0.0D, 0.0D, 0.0D);
            } else {
                this.setParticleTextureIndex(114);
            }

            this.motionX *= 0.699999988079071D;
            this.motionZ *= 0.699999988079071D;
        }

        Material material = this.worldObj
            .getBlock(
                MathHelper.floor_double(this.posX),
                MathHelper.floor_double(this.posY),
                MathHelper.floor_double(this.posZ))
            .getMaterial();

        if (material.isLiquid() || material.isSolid()) {
            double d0 = (float) (MathHelper.floor_double(this.posY) + 1) - BlockLiquid.getLiquidHeightPercent(
                this.worldObj.getBlockMetadata(
                    MathHelper.floor_double(this.posX),
                    MathHelper.floor_double(this.posY),
                    MathHelper.floor_double(this.posZ)));

            if (this.posY < d0) {
                this.setDead();
            }
        }
    }
}

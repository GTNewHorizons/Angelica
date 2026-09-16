package com.gtnewhorizons.angelica.rendering.particles;

import net.minecraft.client.particle.EntityBlockDustFX;
import net.minecraft.client.particle.EntityBreakingFX;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.client.particle.EntityCrit2FX;
import net.minecraft.client.particle.EntityCritFX;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFireworkOverlayFX;
import net.minecraft.client.particle.EntityFireworkSparkFX;
import net.minecraft.client.particle.EntityFlameFX;
import net.minecraft.client.particle.EntityHeartFX;
import net.minecraft.client.particle.EntityHugeExplodeFX;
import net.minecraft.client.particle.EntityLavaFX;
import net.minecraft.client.particle.EntityNoteFX;
import net.minecraft.client.particle.EntityPortalFX;
import net.minecraft.client.particle.EntityReddustFX;
import net.minecraft.client.particle.EntitySmokeFX;
import net.minecraft.client.particle.EntitySnowShovelFX;
import net.minecraft.client.particle.EntitySpellParticleFX;
import net.minecraft.util.MathHelper;

public final class VanillaParticleDescriptors {

    private interface BaseScale {

        float of(EntityFX fx);
    }

    private interface ScaleFactor {

        float factor(int age, int maxAge, float partialTicks);
    }

    private static final ScaleFactor RAMP = ParticleQuads::ramp;

    private static final ScaleFactor FLAME = (age, maxAge, partialTicks) -> {
        final float f = (age + partialTicks) / maxAge;
        return 1.0F - f * f * 0.5F;
    };

    private static final ScaleFactor LAVA = (age, maxAge, partialTicks) -> {
        final float f = (age + partialTicks) / maxAge;
        return 1.0F - f * f;
    };

    private static final ScaleFactor PORTAL = (age, maxAge, partialTicks) -> {
        float f = (age + partialTicks) / maxAge;
        f = 1.0F - f;
        f *= f;
        return 1.0F - f;
    };

    private static final ParticleDescriptor EMPTY = (fx, partialTicks, out, state) -> false;

    private static final ParticleDescriptor DIGGING = (fx, partialTicks, out, state) -> {
        out.half = 0.1F * fx.particleScale;
        ParticleQuads.center(fx, partialTicks, out);
        out.colorABGR = ParticleQuads.packColor(fx.particleRed, fx.particleGreen, fx.particleBlue, 1.0F);
        final float jitterU = fx.particleTextureJitterX;
        final float jitterV = fx.particleTextureJitterY;
        if (fx.particleIcon != null) {
            out.u0 = fx.particleIcon.getInterpolatedU(jitterU / 4.0F * 16.0F);
            out.u1 = fx.particleIcon.getInterpolatedU((jitterU + 1.0F) / 4.0F * 16.0F);
            out.v1 = fx.particleIcon.getInterpolatedV(jitterV / 4.0F * 16.0F);
            out.v0 = fx.particleIcon.getInterpolatedV((jitterV + 1.0F) / 4.0F * 16.0F);
        } else {
            out.u0 = (fx.particleTextureIndexX + jitterU / 4.0F) / 16.0F;
            out.u1 = out.u0 + 0.015609375F;
            out.v1 = (fx.particleTextureIndexY + jitterV / 4.0F) / 16.0F;
            out.v0 = out.v1 + 0.015609375F;
        }
        return true;
    };

    private static final ParticleDescriptor FIREWORK_OVERLAY = (fx, partialTicks, out, state) -> {
        final float t = fx.particleAge + partialTicks - 1.0F;
        fx.particleAlpha = 0.6F - t * 0.25F * 0.5F;
        ParticleQuads.center(fx, partialTicks, out);
        out.half = 7.1F * MathHelper.sin(t * 0.25F * (float) Math.PI);
        out.u0 = 0.25F + 0.25F;
        out.v0 = 0.125F + 0.25F;
        out.u1 = 0.25F;
        out.v1 = 0.125F;
        out.colorABGR = ParticleQuads.packColor(fx.particleRed, fx.particleGreen, fx.particleBlue, fx.particleAlpha);
        return true;
    };

    private record BaseScaled(BaseScale base, ScaleFactor factor) implements ParticleDescriptor {

        @Override
        public boolean describe(EntityFX fx, float partialTicks, ParticleParams out, ParticleRenderState state) {
            fx.particleScale = base.of(fx) * factor.factor(fx.particleAge, fx.particleMaxAge, partialTicks);
            ParticleQuads.vanillaQuad(fx, partialTicks, fx.particleScale, out);
            return true;
        }
    }

    private static final ParticleDescriptor FIREWORK_SPARK = (fx, partialTicks, out, state) -> {
        final int age = fx.particleAge;
        final int maxAge = fx.particleMaxAge;
        if (((EntityFireworkSparkFX) fx).field_92048_ay && age >= maxAge / 3 && (age + maxAge) / 3 % 2 != 0) return false;
        ParticleQuads.vanillaQuad(fx, partialTicks, fx.particleScale, out);
        return true;
    };

    private VanillaParticleDescriptors() {}

    public static void registerAll() {
        registerScaled(EntityFlameFX.class, fx -> ((EntityFlameFX) fx).flameScale, FLAME);
        registerScaled(EntityLavaFX.class, fx -> ((EntityLavaFX) fx).lavaParticleScale, LAVA);
        registerScaled(EntityPortalFX.class, fx -> ((EntityPortalFX) fx).portalParticleScale, PORTAL);
        registerScaled(EntitySmokeFX.class, fx -> ((EntitySmokeFX) fx).smokeParticleScale, RAMP);
        registerScaled(EntityCloudFX.class, fx -> ((EntityCloudFX) fx).field_70569_a, RAMP);
        registerScaled(EntityCritFX.class, fx -> ((EntityCritFX) fx).initialParticleScale, RAMP);
        registerScaled(EntityHeartFX.class, fx -> ((EntityHeartFX) fx).particleScaleOverTime, RAMP);
        registerScaled(EntityNoteFX.class, fx -> ((EntityNoteFX) fx).noteParticleScale, RAMP);
        registerScaled(EntityReddustFX.class, fx -> ((EntityReddustFX) fx).reddustParticleScale, RAMP);
        registerScaled(EntitySnowShovelFX.class, fx -> ((EntitySnowShovelFX) fx).snowDigParticleScale, RAMP);

        ParticleDescriptorRegistry.register(EntitySpellParticleFX.class.getName(), ParticleDescriptorRegistry.VANILLA);
        ParticleDescriptorRegistry.register(EntityDiggingFX.class.getName(), DIGGING);
        ParticleDescriptorRegistry.register(EntityBlockDustFX.class.getName(), DIGGING);
        ParticleDescriptorRegistry.register(EntityBreakingFX.class.getName(), DIGGING);
        ParticleDescriptorRegistry.register(EntityFireworkOverlayFX.class.getName(), FIREWORK_OVERLAY);
        ParticleDescriptorRegistry.register(EntityCrit2FX.class.getName(), EMPTY);
        ParticleDescriptorRegistry.register(EntityHugeExplodeFX.class.getName(), EMPTY);
        ParticleDescriptorRegistry.register(EntityFireworkSparkFX.class.getName(), FIREWORK_SPARK);
    }

    private static void registerScaled(Class<? extends EntityFX> cls, BaseScale base, ScaleFactor factor) {
        ParticleDescriptorRegistry.register(cls.getName(), new BaseScaled(base, factor));
    }
}

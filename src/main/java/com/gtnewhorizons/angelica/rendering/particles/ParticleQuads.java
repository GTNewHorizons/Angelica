package com.gtnewhorizons.angelica.rendering.particles;

import net.minecraft.client.particle.EntityFX;

public final class ParticleQuads {

    private ParticleQuads() {}

    public static void vanillaQuad(EntityFX fx, float partialTicks, float scale, ParticleParams out) {
        out.half = 0.1F * scale;
        center(fx, partialTicks, out);
        iconUv(fx, out);
        out.colorABGR = packColor(fx.particleRed, fx.particleGreen, fx.particleBlue, fx.particleAlpha);
    }

    static void center(EntityFX fx, float partialTicks, ParticleParams out) {
        out.centerX = (float) (fx.prevPosX + (fx.posX - fx.prevPosX) * partialTicks - EntityFX.interpPosX);
        out.centerY = (float) (fx.prevPosY + (fx.posY - fx.prevPosY) * partialTicks - EntityFX.interpPosY);
        out.centerZ = (float) (fx.prevPosZ + (fx.posZ - fx.prevPosZ) * partialTicks - EntityFX.interpPosZ);
    }

    static void iconUv(EntityFX fx, ParticleParams out) {
        float minU = fx.particleTextureIndexX / 16.0F;
        float maxU = minU + 0.0624375F;
        float minV = fx.particleTextureIndexY / 16.0F;
        float maxV = minV + 0.0624375F;
        if (fx.particleIcon != null) {
            minU = fx.particleIcon.getMinU();
            maxU = fx.particleIcon.getMaxU();
            minV = fx.particleIcon.getMinV();
            maxV = fx.particleIcon.getMaxV();
        }
        out.u0 = maxU;
        out.v0 = maxV;
        out.u1 = minU;
        out.v1 = minV;
    }

    static float ramp(int age, int maxAge, float partialTicks) {
        return Math.clamp((age + partialTicks) / maxAge * 32.0F, 0.0F, 1.0F);
    }

    public static int packColor(float r, float g, float b, float a) {
        return (Math.clamp((int) (a * 255.0F), 0, 255) << 24) | (Math.clamp((int) (b * 255.0F), 0, 255) << 16) | (Math.clamp((int) (g * 255.0F), 0, 255) << 8) | Math.clamp((int) (r * 255.0F), 0, 255);
    }
}

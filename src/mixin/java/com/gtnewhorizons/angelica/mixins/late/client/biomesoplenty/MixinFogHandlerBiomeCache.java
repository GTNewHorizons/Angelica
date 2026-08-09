package com.gtnewhorizons.angelica.mixins.late.client.biomesoplenty;

import biomesoplenty.client.fog.FogHandler;
import com.gtnewhorizons.angelica.compat.bop.BopFogBlend;
import com.gtnewhorizons.angelica.compat.bop.BopFogColorSource;
import com.gtnewhorizons.angelica.compat.bop.FogBiomeCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.ForgeModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FogHandler.class, remap = false)
public class MixinFogHandlerBiomeCache {

    @Unique private static final float[] angelica$sums = new float[4];

    @Shadow
    private static Vec3 postProcessColor(World world, EntityLivingBase player, float r, float g, float b, double renderPartialTicks) {
        throw new AssertionError();
    }

    @Redirect(
        method = "getFogBlendColorWater",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBiomeGenForCoords(II)Lnet/minecraft/world/biome/BiomeGenBase;", remap = true))
    private static BiomeGenBase angelica$cachedBiomeLookup(World world, int x, int z) {
        return FogBiomeCache.get(world, x, z);
    }

    /**
     * @author Angelica
     * @reason Cache the interior sum and walk only the border strips
     */
    @Overwrite
    private static Vec3 getFogBlendColour(World world, EntityLivingBase playerEntity, int playerX, int playerY, int playerZ, float defR, float defG, float defB, double renderPartialTicks) {
        final GameSettings settings = Minecraft.getMinecraft().gameSettings;
        final int[] ranges = ForgeModContainer.blendRanges;
        int distance = 6;
        if (settings.fancyGraphics && settings.renderDistanceChunks >= 0 && settings.renderDistanceChunks < ranges.length) {
            distance = ranges[settings.renderDistanceChunks];
        }

        final float[] sums = angelica$sums;
        BopFogBlend.accumulate(world, FogBiomeCache.generation(), BopFogColorSource.INSTANCE.forWorld(world),
            playerEntity.posX, playerEntity.posZ, playerX, playerY, playerZ, distance, sums);

        float rBiomeFog = sums[0];
        float gBiomeFog = sums[1];
        float bBiomeFog = sums[2];
        final float weightBiomeFog = sums[3];

        if (weightBiomeFog == 0 || distance == 0) {
            return Vec3.createVectorHelper(defR, defG, defB);
        }

        rBiomeFog /= 255f;
        gBiomeFog /= 255f;
        bBiomeFog /= 255f;

        final float celestialAngle = world.getCelestialAngle((float) renderPartialTicks);
        final float baseScale = MathHelper.clamp_float(MathHelper.cos(celestialAngle * (float) Math.PI * 2.0F) * 2.0F + 0.5F, 0, 1);

        float rScale = baseScale * 0.94F + 0.06F;
        float gScale = baseScale * 0.94F + 0.06F;
        float bScale = baseScale * 0.91F + 0.09F;

        final float rainStrength = world.getRainStrength((float) renderPartialTicks);
        if (rainStrength > 0) {
            rScale *= 1 - rainStrength * 0.5f;
            gScale *= 1 - rainStrength * 0.5f;
            bScale *= 1 - rainStrength * 0.4f;
        }

        final float thunderStrength = world.getWeightedThunderStrength((float) renderPartialTicks);
        if (thunderStrength > 0) {
            rScale *= 1 - thunderStrength * 0.5f;
            gScale *= 1 - thunderStrength * 0.5f;
            bScale *= 1 - thunderStrength * 0.5f;
        }

        rBiomeFog *= rScale / weightBiomeFog;
        gBiomeFog *= gScale / weightBiomeFog;
        bBiomeFog *= bScale / weightBiomeFog;

        final Vec3 processedColor = postProcessColor(world, playerEntity, rBiomeFog, gBiomeFog, bBiomeFog, renderPartialTicks);
        rBiomeFog = (float) processedColor.xCoord;
        gBiomeFog = (float) processedColor.yCoord;
        bBiomeFog = (float) processedColor.zCoord;

        final float weightMixed = (distance * 2) * (distance * 2);
        final float weightDefault = weightMixed - weightBiomeFog;

        processedColor.xCoord = (rBiomeFog * weightBiomeFog + defR * weightDefault) / weightMixed;
        processedColor.yCoord = (gBiomeFog * weightBiomeFog + defG * weightDefault) / weightMixed;
        processedColor.zCoord = (bBiomeFog * weightBiomeFog + defB * weightDefault) / weightMixed;

        return processedColor;
    }
}

package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.gtnewhorizons.angelica.compat.iris.BiomeCategoryCache;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import com.gtnewhorizons.angelica.utils.EventUtils;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.NoiseGeneratorPerlin;
import net.minecraftforge.event.terraingen.BiomeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BiomeGenBase.class, priority = 1100)
public class MixinBiomeGenBase implements BiomeCategoryCache {
    @Unique
    private int cachedBiomeCategory = -1;

    private final ThreadLocal<BiomeEvent.GetWaterColor> waterColorEventLocal = ThreadLocal.withInitial(() -> new BiomeEvent.GetWaterColor((BiomeGenBase)(Object)this, 0));
    private final ThreadLocal<BiomeEvent.GetGrassColor> grassColorEventLocal = ThreadLocal.withInitial(() -> new BiomeEvent.GetGrassColor((BiomeGenBase)(Object)this, 0));
    private final ThreadLocal<BiomeEvent.GetFoliageColor> foliageColorEventLocal = ThreadLocal.withInitial(() -> new BiomeEvent.GetFoliageColor((BiomeGenBase)(Object)this, 0));

    @Surround(method = {"getFloatTemperature", "getFloatTemperatureOld"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/NoiseGeneratorPerlin;func_151601_a(DD)D"), require = 1)
    private void angelica$cachedTemperatureNoise(NoiseGeneratorPerlin noise, double x, double z) {
        @Surround.Carry
        final SmoothBiomeColorCache cache = SmoothBiomeColorCache.getActiveCache();
        @Surround.Carry
        final int index = cache == null ? -1 : cache.temperatureNoiseIndex(noise, x, z);
        @Surround.Carry
        final double cached = index < 0 ? Double.NaN : cache.getTemperatureNoise(index);
        @Surround.Skip
        final boolean hit = !Double.isNaN(cached);
    }

    @Surround.Skipped
    private double angelica$cachedTemperatureNoiseHit(@Surround.Carry double cached) {
        return cached;
    }

    @Surround.Return
    private double angelica$storeTemperatureNoise(double value, @Surround.Carry SmoothBiomeColorCache cache, @Surround.Carry int index) {
        if (index >= 0) cache.setTemperatureNoise(index, value);
        return value;
    }

    @Unique
    private void prepareEvent(BiomeEvent.BiomeColor event, int defaultColor) {
        event.newColor = defaultColor;
        EventUtils.clearPhase(event);
        ((AccessorBiomeColorEvent)event).setOriginalColor(defaultColor);
    }

    @Redirect(method = "getWaterColorMultiplier", at = @At(value = "NEW", target = "(Lnet/minecraft/world/biome/BiomeGenBase;I)Lnet/minecraftforge/event/terraingen/BiomeEvent$GetWaterColor;"))
    private BiomeEvent.GetWaterColor memoizeWaterObject(BiomeGenBase biome, int original) {
        BiomeEvent.GetWaterColor event = waterColorEventLocal.get();
        prepareEvent(event, original);
        return event;
    }

    @Redirect(method = "getModdedBiomeGrassColor", at = @At(value = "NEW", target = "(Lnet/minecraft/world/biome/BiomeGenBase;I)Lnet/minecraftforge/event/terraingen/BiomeEvent$GetGrassColor;"))
    private BiomeEvent.GetGrassColor memoizeGrassObject(BiomeGenBase biome, int original) {
        BiomeEvent.GetGrassColor event = grassColorEventLocal.get();
        prepareEvent(event, original);
        return event;
    }

    @Redirect(method = "getModdedBiomeFoliageColor", at = @At(value = "NEW", target = "(Lnet/minecraft/world/biome/BiomeGenBase;I)Lnet/minecraftforge/event/terraingen/BiomeEvent$GetFoliageColor;"))
    private BiomeEvent.GetFoliageColor memoizeFoliageObject(BiomeGenBase biome, int original) {
        BiomeEvent.GetFoliageColor event = foliageColorEventLocal.get();
        prepareEvent(event, original);
        return event;
    }

    @Override
    public int iris$getCachedCategory() {
        return cachedBiomeCategory;
    }

    @Override
    public void iris$setCachedCategory(int category) {
        this.cachedBiomeCategory = category;
    }
}

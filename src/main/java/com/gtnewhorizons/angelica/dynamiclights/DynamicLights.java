package com.gtnewhorizons.angelica.dynamiclights;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockPosImpl;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

public class DynamicLights {
    private static DynamicLights instance;

    private static final double MAX_RADIUS = 7.75;
    private static final double MAX_RADIUS_SQUARED = MAX_RADIUS * MAX_RADIUS;
    private final Set<IDynamicLightSource> dynamicLightSources = new HashSet<>();
    private final ReentrantReadWriteLock lightSourcesLock = new ReentrantReadWriteLock();
    private long lastUpdate = System.currentTimeMillis();
    private int lastUpdateCount = 0;

    public static DynamicLights get() {
        if (instance == null)
            instance = new DynamicLights();
        return instance;
    }

    public static boolean isEnabled() {
        // TODO controlled by config
        //return !Objects.equals(DynamicLightsConfig.Quality.get(), "OFF");
        return true;
    }

    /**
     * Updates all light sources.
     *
     * @param renderer the renderer
     */
    public void updateAll(@NotNull SodiumWorldRenderer renderer) {
        if (!isEnabled())
            return;

        long now = System.currentTimeMillis();
        if (now >= this.lastUpdate + 50) {
            this.lastUpdate = now;
            this.lastUpdateCount = 0;

            this.lightSourcesLock.readLock().lock();
            for (var lightSource : this.dynamicLightSources) {
                if (lightSource.angelica$updateDynamicLight(renderer))
                    this.lastUpdateCount++;
            }
            this.lightSourcesLock.readLock().unlock();
        }
    }

    public int getLastUpdateCount() {
        return this.lastUpdateCount;
    }

    public void addLightSource(IDynamicLightSource lightSource) {
        this.lightSourcesLock.writeLock().lock();
        this.dynamicLightSources.add(lightSource);
        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Removes the light source from the tracked light sources.
     *
     * @param lightSource the light source to remove
     */
    public void removeLightSource(@NotNull IDynamicLightSource lightSource) {
        this.lightSourcesLock.writeLock().lock();

        var dynamicLightSources = this.dynamicLightSources.iterator();
        IDynamicLightSource it;
        while (dynamicLightSources.hasNext()) {
            it = dynamicLightSources.next();
            if (it.equals(lightSource)) {
                dynamicLightSources.remove();
                if (SodiumWorldRenderer.getInstance() != null)
                    lightSource.angelica$scheduleTrackedChunksRebuild(SodiumWorldRenderer.getInstance());
                break;
            }
        }

        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Removes light sources if the filter matches.
     *
     * @param filter the removal filter
     */
    public void removeLightSources(@NotNull Predicate<IDynamicLightSource> filter) {
        this.lightSourcesLock.writeLock().lock();

        var dynamicLightSources = this.dynamicLightSources.iterator();
        IDynamicLightSource it;
        while (dynamicLightSources.hasNext()) {
            it = dynamicLightSources.next();
            if (filter.test(it)) {
                dynamicLightSources.remove();
                if (SodiumWorldRenderer.getInstance() != null) {
                    if (it.angelica$getLuminance() > 0)
                        it.angelica$resetDynamicLight();
                    it.angelica$scheduleTrackedChunksRebuild(SodiumWorldRenderer.getInstance());
                }
                break;
            }
        }

        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Clears light sources.
     */
    public void clearLightSources() {
        this.lightSourcesLock.writeLock().lock();

        var dynamicLightSources = this.dynamicLightSources.iterator();
        IDynamicLightSource it;
        while (dynamicLightSources.hasNext()) {
            it = dynamicLightSources.next();
            dynamicLightSources.remove();
            if (SodiumWorldRenderer.getInstance() != null) {
                if (it.angelica$getLuminance() > 0)
                    it.angelica$resetDynamicLight();
                it.angelica$scheduleTrackedChunksRebuild(SodiumWorldRenderer.getInstance());
            }
        }

        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Returns whether the light source is tracked or not.
     *
     * @param lightSource the light source to check
     * @return {@code true} if the light source is tracked, else {@code false}
     */
    public boolean containsLightSource(@NotNull IDynamicLightSource lightSource) {
        //if (!lightSource.angelica$getDynamicLightWorld().isClientSide())
            //return false;

        boolean result;
        this.lightSourcesLock.readLock().lock();
        result = this.dynamicLightSources.contains(lightSource);
        this.lightSourcesLock.readLock().unlock();
        return result;
    }

    /**
     * Returns the number of dynamic light sources that currently emit lights.
     *
     * @return the number of dynamic light sources emitting light
     */
    public int getLightSourcesCount() {
        int result;

        this.lightSourcesLock.readLock().lock();
        result = this.dynamicLightSources.size();
        this.lightSourcesLock.readLock().unlock();

        return result;
    }

    public double getDynamicLightLevel(int x, int y, int z) {
        double result = 0;
        this.lightSourcesLock.readLock().lock();
        for (var lightSource : this.dynamicLightSources) {
            result = maxDynamicLightLevel(x, y, z, lightSource, result);
        }
        this.lightSourcesLock.readLock().unlock();

        return MathHelper.clamp_double(result, 0, 15);
    }

    public double getDynamicLightLevel(@NotNull BlockPosImpl pos) {
        return this.getDynamicLightLevel(pos.getX(), pos.getY(), pos.getZ());
    }

    public static double maxDynamicLightLevel(int x, int y, int z, @NotNull IDynamicLightSource lightSource, double currentLightLevel) {
        int luminance = lightSource.angelica$getLuminance();
        if (luminance > 0) {
            // Can't use Entity#squaredDistanceTo because of eye Y coordinate.
            double dx = x - lightSource.angelica$getDynamicLightX() + 0.5;
            double dy = y - lightSource.angelica$getDynamicLightY() + 0.5;
            double dz = z - lightSource.angelica$getDynamicLightZ() + 0.5;

            double distanceSquared = dx * dx + dy * dy + dz * dz;
            // 7.75 because else we would have to update more chunks and that's not a good idea.
            // 15 (max range for blocks) would be too much and a bit cheaty.
            if (distanceSquared <= MAX_RADIUS_SQUARED) {
                double multiplier = 1.0 - Math.sqrt(distanceSquared) / MAX_RADIUS;
                double lightLevel = multiplier * (double) luminance;
                if (lightLevel > currentLightLevel) {
                    return lightLevel;
                }
            }
        }
        return currentLightLevel;
    }

    /**
     * Returns the dynamic light level generated by the light source at the specified position.
     *
     * @param pos the position
     * @param lightSource the light source
     * @param currentLightLevel the current surrounding dynamic light level
     * @return the dynamic light level at the specified position
     */
    public static double maxDynamicLightLevel(@NotNull BlockPosImpl pos, @NotNull IDynamicLightSource lightSource, double currentLightLevel) {
        return maxDynamicLightLevel(pos.getX(), pos.getY(), pos.getZ(), lightSource, currentLightLevel);
    }

    /**
     * Returns the lightmap with combined light levels.
     *
     * @param pos the position
     * @param lightmap the vanilla lightmap coordinates
     * @return the modified lightmap coordinates
     */
    public int getLightmapWithDynamicLight(@NotNull BlockPosImpl pos, int lightmap) {
        return this.getLightmapWithDynamicLight(this.getDynamicLightLevel(pos), lightmap);
    }

    public int getLightmapWithDynamicLight(int x, int y, int z, int lightmap) {
        return this.getLightmapWithDynamicLight(this.getDynamicLightLevel(x, y, z), lightmap);
    }

    /**
     * Returns the lightmap with combined light levels.
     *
     * @param entity the entity
     * @param lightmap the vanilla lightmap coordinates
     * @return the modified lightmap coordinates
     */
    public int getLightmapWithDynamicLight(@NotNull Entity entity, int lightmap) {
        //int posLightLevel = (int) this.getDynamicLightLevel(entity.get);
        //int entityLuminance = ((IDynamicLightSource) entity).tdv$getLuminance();

        //return this.getLightmapWithDynamicLight(Math.max(posLightLevel, entityLuminance), lightmap);
        return 8;
    }

    /**
     * Returns the lightmap with combined light levels.
     *
     * @param dynamicLightLevel the dynamic light level
     * @param lightmap the vanilla lightmap coordinates
     * @return the modified lightmap coordinates
     */
    public int getLightmapWithDynamicLight(double dynamicLightLevel, int lightmap) {
        if (dynamicLightLevel > 0) {
            // lightmap is (skyLevel << 20 | blockLevel << 4)

            // Get vanilla block light level.
            int blockLevel = (lightmap & 0xFFFF) >> 4;
            if (dynamicLightLevel > blockLevel) {
                // Equivalent to a << 4 bitshift with a little quirk: this one ensure more precision (more decimals are saved).
                int luminance = (int) (dynamicLightLevel * 16.0);
                lightmap &= 0xfff00000;
                lightmap |= luminance & 0x000fffff;
            }
        }

        return lightmap;
    }

    /**
     * Schedules a chunk rebuild at the specified chunk position.
     *
     * @param renderer the renderer
     * @param chunkPos the chunk position
     */
    public static void scheduleChunkRebuild(@NotNull SodiumWorldRenderer renderer, @NotNull BlockPosImpl chunkPos) {
        scheduleChunkRebuild(renderer, chunkPos.getX(), chunkPos.getY(), chunkPos.getZ());
    }

    /**
     * Schedules a chunk rebuild at the specified chunk position.
     *
     * @param renderer the renderer
     * @param chunkPos the packed chunk position
     */
    public static void scheduleChunkRebuild(@NotNull SodiumWorldRenderer renderer, long chunkPos) {
        scheduleChunkRebuild(renderer, BlockPos.unpackLongX(chunkPos), BlockPos.unpackLongY(chunkPos), BlockPos.unpackLongZ(chunkPos));
    }

    public static void scheduleChunkRebuild(@NotNull SodiumWorldRenderer renderer, int x, int y, int z) {
        renderer.scheduleRebuildForChunk(x, y, z, false);
    }

    /**
     * Updates the tracked chunk sets.
     *
     * @param chunkPos the packed chunk position
     * @param old the set of old chunk coordinates to remove this chunk from it
     * @param newPos the set of new chunk coordinates to add this chunk to it
     */
    public static void updateTrackedChunks(@NotNull BlockPosImpl chunkPos, @Nullable LongOpenHashSet old, @Nullable LongOpenHashSet newPos) {
        if (old != null || newPos != null) {
            long pos = chunkPos.asLong();
            if (old != null)
                old.remove(pos);
            if (newPos != null)
                newPos.add(pos);
        }
    }

    /**
     * Updates the dynamic lights tracking.
     *
     * @param lightSource the light source
     */
    public static void updateTracking(@NotNull IDynamicLightSource lightSource) {
        boolean enabled = lightSource.angelica$isDynamicLightEnabled();
        int luminance = lightSource.angelica$getLuminance();

        if (!enabled && luminance > 0) {
            lightSource.angelica$setDynamicLightEnabled(true);
        } else if (enabled && luminance < 1) {
            lightSource.angelica$setDynamicLightEnabled(false);
        }
    }

    /**
     * Returns the luminance from an item stack.
     *
     * @param stack the item stack
     * @param submergedInWater {@code true} if the stack is submerged in water, else {@code false}
     * @return the luminance of the item
     */
    //public static int getLuminanceFromItemStack(@NotNull ItemStack stack, boolean submergedInWater) {
        //return ItemLightSources.getLuminance(stack, submergedInWater);
    //}
}

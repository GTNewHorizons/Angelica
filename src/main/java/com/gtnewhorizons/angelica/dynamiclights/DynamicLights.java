package com.gtnewhorizons.angelica.dynamiclights;

import baubles.common.lib.PlayerHandler;
import lain.mods.cos.CosmeticArmorReworked;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.blockpos.IBlockPos;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizons.angelica.api.BlockLightProvider;
import com.gtnewhorizons.angelica.api.IDynamicLightProducer;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.backhand.BackhandReflectionCompat;
import com.gtnewhorizons.angelica.compat.battlegear2.Battlegear2Compat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.dynamiclights.config.EntityLightConfig;
import com.gtnewhorizons.angelica.helpers.ItemBlockLight;
import com.gtnewhorizons.angelica.mixins.interfaces.PrimedEntityAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mods.battlegear2.api.core.IBattlePlayer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DynamicLights {
    private static DynamicLights instance;
    private static IDynamicLightWorldRenderer activeRenderer;
    public static DynamicLightsMode Mode = DynamicLightsMode.OFF;
    public static boolean ShaderForce = false;
    public static boolean FrustumCullingEnabled = true;
    public static boolean AdaptiveTickingEnabled = true;

    public static final boolean configEnabled = AngelicaConfig.enableDynamicLights;

    private static final double MAX_RADIUS = 7.75;
    private final Set<IDynamicLightSource> dynamicLightSources = new ObjectOpenHashSet<>();
    private static final IDynamicLightSource[] NO_SOURCES = new IDynamicLightSource[0];
    private IDynamicLightSource[] lightSourceArray = NO_SOURCES;
    // The sources that light things under a shaderpack without shader force, see isActive
    private IDynamicLightSource[] shaderpackSourceArray = NO_SOURCES;
    private final ReentrantReadWriteLock lightSourcesLock = new ReentrantReadWriteLock();
    private volatile int lightSourceCount = 0;
    private volatile int shaderpackSourceCount = 0;
    private final ChunkRebuildManager chunkRebuildManager = new ChunkRebuildManager();
    private long lastUpdate = System.currentTimeMillis();
    private int lastUpdateCount = 0;
    // Which sources gave light at the last update: 0 none, 1 the ones lit under shaderpacks, 2 all of them
    private int litState = -1;

    /** Must be called under write lock. */
    private void rebuildLightSourceArray() {
        this.lightSourceArray = this.dynamicLightSources.toArray(NO_SOURCES);
        int count = 0;
        for (IDynamicLightSource source : this.lightSourceArray) {
            if (source.angelica$isLitUnderShaderpacks()) count++;
        }
        final IDynamicLightSource[] shaderpackSources = new IDynamicLightSource[count];
        int i = 0;
        for (IDynamicLightSource source : this.lightSourceArray) {
            if (source.angelica$isLitUnderShaderpacks()) shaderpackSources[i++] = source;
        }
        this.shaderpackSourceArray = shaderpackSources;
        this.shaderpackSourceCount = count;
    }

    /** The sources that give light right now. Call under the read lock. */
    private IDynamicLightSource[] activeSources() {
        if (isEnabled()) return this.lightSourceArray;
        return configEnabled && Mode.isEnabled() ? this.shaderpackSourceArray : NO_SOURCES;
    }

    /** A tracked source changed whether it's lit under shaderpacks. */
    public void onShaderpackLitChanged(IDynamicLightSource lightSource) {
        this.lightSourcesLock.writeLock().lock();
        try {
            if (this.dynamicLightSources.contains(lightSource)) rebuildLightSourceArray();
        } finally {
            this.lightSourcesLock.writeLock().unlock();
        }
    }

    public static DynamicLights get() {
        if (instance == null)
            instance = new DynamicLights();
        return instance;
    }

    public static boolean isEnabled() {
        return configEnabled &&
            Mode.isEnabled() &&
            // if shader force is enabled then true
            // if not, then true when shaders are not in use
            (ShaderForce || !IrisApi.getInstance().isShaderPackInUse());
    }

    /**
     * True when a light that's lit under shaderpacks (or not, see {@link IDynamicLightSource#angelica$isLitUnderShaderpacks})
     * gives light right now. Those only skip the shaderpack check, dynamic lights turned off still means off.
     */
    public static boolean isEnabled(boolean litUnderShaderpacks) {
        return isEnabled() || litUnderShaderpacks && configEnabled && Mode.isEnabled();
    }

    /**
     * True when any light gives light right now: all of them ({@link #isEnabled()}), or only the ones lit under
     * shaderpacks when a pack turned the rest off. What the light pipeline checks before it does any dynamic light work.
     */
    public static boolean isActive() {
        return isEnabled() || configEnabled && Mode.isEnabled() && get().shaderpackSourceCount > 0;
    }

    public boolean hasLightSources() {
        return lightSourceCount > 0;
    }

    public static IDynamicLightWorldRenderer getActiveRenderer() {
        return activeRenderer;
    }

    public static void setActiveRenderer(IDynamicLightWorldRenderer renderer) {
        activeRenderer = renderer;
    }

    /**
     * Updates all light sources.
     *
     * @param renderer the renderer
     */
    public void updateAll(@NotNull IDynamicLightWorldRenderer renderer) {
        final int state = isEnabled() ? 2 : configEnabled && Mode.isEnabled() ? 1 : 0;
        if (state != this.litState) {
            if (this.litState != -1) relightAll(renderer);
            this.litState = state;
        }

        if (!isActive())
            return;

        final long now = System.currentTimeMillis();
        if (now >= this.lastUpdate + Mode.getDelay()) {
            this.lastUpdate = now;
            this.lastUpdateCount = 0;

            // Get camera info for adaptive ticking
            double cameraX = 0, cameraY = 0, cameraZ = 0;
            double lookDirX = 0, lookDirZ = 1;
            int worldTick = 0;

            if (AdaptiveTickingEnabled) {
                final Minecraft mc = Minecraft.getMinecraft();
                if (mc.theWorld != null && mc.renderViewEntity != null) {
                    cameraX = mc.renderViewEntity.posX;
                    cameraY = mc.renderViewEntity.posY + mc.renderViewEntity.getEyeHeight();
                    cameraZ = mc.renderViewEntity.posZ;
                    worldTick = (int) (mc.theWorld.getTotalWorldTime() & 0x7FFFFFFF);

                    // Calculate look direction from yaw (normalized horizontal direction)
                    final float yaw = mc.renderViewEntity.rotationYaw;
                    final double yawRad = Math.toRadians(yaw);
                    lookDirX = -Math.sin(yawRad);
                    lookDirZ = Math.cos(yawRad);
                }
            }

            this.lightSourcesLock.readLock().lock();
            for (var lightSource : activeSources()) {
                // Entity type filter
                if (!EntityLightConfig.isEntityTypeEnabled(lightSource)) {
                    continue;
                }

                // Adaptive ticking filter
                if (AdaptiveTickingEnabled) {
                    final AdaptiveTickMode mode = AdaptiveTickCalculator.calculate(
                        lightSource, cameraX, cameraY, cameraZ, lookDirX, lookDirZ);
                    if (!mode.shouldTickThisFrame(worldTick, lightSource.hashCode())) {
                        continue;
                    }
                }

                if (lightSource.angelica$updateDynamicLight(renderer))
                    this.lastUpdateCount++;
            }
            this.lightSourcesLock.readLock().unlock();
        }
    }

    /**
     * The player switched dynamic lights, shader force or the shaderpack, so which sources give light changed. The
     * sections they lit rebuild, and every source starts over on its next update. Without this a light that went dark
     * stays in the terrain until something else rebuilds it, and one that sits still never comes back.
     */
    private void relightAll(@NotNull IDynamicLightWorldRenderer renderer) {
        this.lightSourcesLock.readLock().lock();
        try {
            for (IDynamicLightSource source : this.lightSourceArray) {
                source.angelica$scheduleTrackedChunksRebuild(renderer);
                source.angelica$resetDynamicLight();
            }
        } finally {
            this.lightSourcesLock.readLock().unlock();
        }
    }

    public int getLastUpdateCount() {
        return this.lastUpdateCount;
    }

    public void addLightSource(IDynamicLightSource lightSource) {
        this.lightSourcesLock.writeLock().lock();
        if (this.dynamicLightSources.add(lightSource)) {
            this.lightSourceCount = this.dynamicLightSources.size();
            rebuildLightSourceArray();
        }
        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Removes the light source from the tracked light sources.
     *
     * @param lightSource the light source to remove
     */
    public void removeLightSource(@NotNull IDynamicLightSource lightSource) {
        this.lightSourcesLock.writeLock().lock();

        if (this.dynamicLightSources.remove(lightSource)) {
            this.lightSourceCount = this.dynamicLightSources.size();
            rebuildLightSourceArray();
            if (activeRenderer != null)
                lightSource.angelica$scheduleTrackedChunksRebuild(activeRenderer);
        }

        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Removes all tracked light sources, to call when unloading the client world
     */
    public void removeAllLightSources() {
        this.lightSourcesLock.writeLock().lock();

        this.dynamicLightSources.clear();
        this.lightSourceCount = 0;
        rebuildLightSourceArray();

        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Removes light sources if the filter matches.
     *
     * @param filter the removal filter
     */
    public void removeLightSources(@NotNull Predicate<IDynamicLightSource> filter) {
        this.lightSourcesLock.writeLock().lock();

        final var dynamicLightSources = this.dynamicLightSources.iterator();
        IDynamicLightSource it;
        boolean changed = false;
        while (dynamicLightSources.hasNext()) {
            it = dynamicLightSources.next();
            if (filter.test(it)) {
                dynamicLightSources.remove();
                changed = true;
                if (activeRenderer != null) {
                    if (it.angelica$getLuminance() > 0)
                        it.angelica$resetDynamicLight();
                    it.angelica$scheduleTrackedChunksRebuild(activeRenderer);
                }
                break;
            }
        }
        if (changed) {
            this.lightSourceCount = this.dynamicLightSources.size();
            rebuildLightSourceArray();
        }

        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Clears light sources.
     */
    public void clearLightSources() {
        this.lightSourcesLock.writeLock().lock();

        final var dynamicLightSources = this.dynamicLightSources.iterator();
        IDynamicLightSource it;
        while (dynamicLightSources.hasNext()) {
            it = dynamicLightSources.next();
            dynamicLightSources.remove();
            if (activeRenderer != null) {
                if (it.angelica$getLuminance() > 0)
                    it.angelica$resetDynamicLight();
                it.angelica$scheduleTrackedChunksRebuild(activeRenderer);
            }
        }
        this.lightSourceCount = 0;
        rebuildLightSourceArray();

        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * Returns whether the light source is tracked or not.
     *
     * @param lightSource the light source to check
     * @return {@code true} if the light source is tracked, else {@code false}
     */
    public boolean containsLightSource(@NotNull IDynamicLightSource lightSource) {
        final boolean result;
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
        final int result;

        this.lightSourcesLock.readLock().lock();
        result = this.dynamicLightSources.size();
        this.lightSourcesLock.readLock().unlock();

        return result;
    }

    public double getDynamicLightLevel(int x, int y, int z) {
        double result = 0;
        this.lightSourcesLock.readLock().lock();
        for (var lightSource : activeSources()) {
            result = maxDynamicLightLevel(x, y, z, lightSource, result);
        }
        this.lightSourcesLock.readLock().unlock();

        return MathHelper.clamp_double(result, 0, 15);
    }

    /**
     * Gets dynamic light level at an exact world position (for smooth per-vertex lighting).
     */
    public double getDynamicLightLevel(double x, double y, double z) {
        double result = 0;
        this.lightSourcesLock.readLock().lock();
        for (var lightSource : activeSources()) {
            result = maxDynamicLightLevelExact(x, y, z, lightSource, result);
        }
        this.lightSourcesLock.readLock().unlock();

        return MathHelper.clamp_double(result, 0, 15);
    }

    public double getDynamicLightLevel(@NotNull BlockPos pos) {
        return this.getDynamicLightLevel(pos.getX(), pos.getY(), pos.getZ());
    }

    public static double maxDynamicLightLevel(int x, int y, int z, @NotNull IDynamicLightSource lightSource, double currentLightLevel) {
        final int luminance = lightSource.angelica$getLuminance();
        if (luminance > 0) {
            // Can't use Entity#squaredDistanceTo because of eye Y coordinate.
            final double dx = x - lightSource.angelica$getDynamicLightX() + 0.5;
            final double dy = y - lightSource.angelica$getDynamicLightY() + 0.5;
            final double dz = z - lightSource.angelica$getDynamicLightZ() + 0.5;

            final double distanceSquared = dx * dx + dy * dy + dz * dz;
            // Held torch reach is 7.75 because else we would have to update more chunks and that's not a good idea.
            // Lights that ask for more get it (see angelica$getReach), and rebuild more chunks for it.
            final double lightLevel = levelAt(lightSource, luminance, distanceSquared);
            if (lightLevel > currentLightLevel) {
                return lightLevel;
            }
        }
        return currentLightLevel;
    }

    /**
     * Calculates dynamic light level at an exact position (no block center offset).
     * Used for smooth per-vertex lighting.
     */
    public static double maxDynamicLightLevelExact(double x, double y, double z, @NotNull IDynamicLightSource lightSource, double currentLightLevel) {
        final int luminance = lightSource.angelica$getLuminance();
        if (luminance > 0) {
            final double dx = x - lightSource.angelica$getDynamicLightX();
            final double dy = y - lightSource.angelica$getDynamicLightY();
            final double dz = z - lightSource.angelica$getDynamicLightZ();

            final double distanceSquared = dx * dx + dy * dy + dz * dz;
            final double lightLevel = levelAt(lightSource, luminance, distanceSquared);
            if (lightLevel > currentLightLevel) {
                return lightLevel;
            }
        }
        return currentLightLevel;
    }

    /**
     * Returns the dynamic light level generated by the light source at the specified position.
     *
     * @param pos               the position
     * @param lightSource       the light source
     * @param currentLightLevel the current surrounding dynamic light level
     * @return the dynamic light level at the specified position
     */
    public static double maxDynamicLightLevel(@NotNull BlockPos pos, @NotNull IDynamicLightSource lightSource, double currentLightLevel) {
        return maxDynamicLightLevel(pos.getX(), pos.getY(), pos.getZ(), lightSource, currentLightLevel);
    }

    public int getLightmapWithDynamicLight(int x, int y, int z, int lightmap) {
        return this.getLightmapWithDynamicLight(this.getDynamicLightLevel(x, y, z), lightmap);
    }

    public static int addDynamicLight(IBlockAccess world, int x, int y, int z, int lightmap) {
        if (!isActive()) return lightmap;
        final DynamicLights dl = get();
        if (!dl.hasLightSources()) return lightmap;
        final double dynamicLightLevel = dl.getDynamicLightLevel(x, y, z);
        if (dynamicLightLevel > 0 && !world.getBlock(x, y, z).isOpaqueCube()) return dl.getLightmapWithDynamicLight(dynamicLightLevel, lightmap);
        return lightmap;
    }

    /**
     * Calls the consumer for every lit light source within radius of the point. Runs on a snapshot of the sources, so
     * the consumer may add or remove lights.
     */
    public void forEachSourceNear(double x, double y, double z, double radius, Consumer<IDynamicLightSource> consumer) {
        final IDynamicLightSource[] sources;
        this.lightSourcesLock.readLock().lock();
        try {
            sources = activeSources();
        } finally {
            this.lightSourcesLock.readLock().unlock();
        }
        final double radiusSq = radius * radius;
        for (IDynamicLightSource source : sources) {
            if (source.angelica$getLuminance() <= 0) continue;
            final double dx = source.angelica$getDynamicLightX() - x;
            final double dy = source.angelica$getDynamicLightY() - y;
            final double dz = source.angelica$getDynamicLightZ() - z;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) consumer.accept(source);
        }
    }

    /** Light level of a source of this luminance at this distance, with the held torch reach. */
    public static double falloff(int luminance, double distance) {
        return falloff(luminance, -1, distance);
    }

    /**
     * Light level of a source of this luminance and reach (see {@link IDynamicLightSource#angelica$getReach}) at this
     * distance.
     */
    public static double falloff(int luminance, float reach, double distance) {
        if (luminance <= 0 || reach == 0) return 0;
        // Held torch: straight down to 0 at 7.75 blocks, whatever the luminance
        if (reach < 0) return distance > MAX_RADIUS ? 0 : (1.0 - distance / MAX_RADIUS) * luminance;
        // Like block light: down 15 / reach levels a block, so 15 is vanilla
        return Math.max(0, luminance - distance * 15.0 / reach);
    }

    /** How far the source's light gets at its current luminance, 0 when it's dark. */
    public static double reachOf(@NotNull IDynamicLightSource source) {
        final int luminance = source.angelica$getLuminance();
        if (luminance <= 0) return 0;
        final float reach = source.angelica$getReach();
        return reach < 0 ? MAX_RADIUS : reach * luminance / 15.0;
    }

    private static double levelAt(@NotNull IDynamicLightSource source, int luminance, double distanceSquared) {
        final double reach = reachOf(source);
        if (distanceSquared >= reach * reach) return 0;
        return falloff(luminance, source.angelica$getReach(), Math.sqrt(distanceSquared));
    }

    public List<IDynamicLightSource> getSourcesForChunk(int chunkMinX, int chunkMinY, int chunkMinZ) {
        // Chunk center
        final double centerX = chunkMinX + 8.0;
        final double centerY = chunkMinY + 8.0;
        final double centerZ = chunkMinZ + 8.0;

        // Smooth lighting reads the cells a block outside the section too, and the middle of the furthest of those is
        // sqrt(3) * 8.5 ≈ 14.72 from the chunk center. Plus the source's reach (7.75 for a held torch), slightly padded
        // for safety

        List<IDynamicLightSource> result = null;

        this.lightSourcesLock.readLock().lock();
        try {
            for (var source : activeSources()) {
                final double dx = source.angelica$getDynamicLightX() - centerX;
                final double dy = source.angelica$getDynamicLightY() - centerY;
                final double dz = source.angelica$getDynamicLightZ() - centerZ;
                final double distSq = dx * dx + dy * dy + dz * dz;
                final double searchRadius = 14.8 + reachOf(source);

                if (distSq <= searchRadius * searchRadius) {
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    result.add(source);
                }
            }
        } finally {
            this.lightSourcesLock.readLock().unlock();
        }

        return result != null ? result : Collections.emptyList();
    }

    public int getDynamicLightRGB(double x, double y, double z) {
        int accum = 0;
        this.lightSourcesLock.readLock().lock();
        try {
            for (var lightSource : activeSources()) {
                accum = accumulateSourceRGB(x, y, z, lightSource, accum);
            }
        } finally {
            this.lightSourcesLock.readLock().unlock();
        }
        return accum;
    }

    public int getDynamicLightRGBFromSources(double x, double y, double z, List<IDynamicLightSource> sources) {
        if (sources == null || sources.isEmpty()) return 0;
        int accum = 0;
        for (int i = 0, size = sources.size(); i < size; i++) {
            accum = accumulateSourceRGB(x, y, z, sources.get(i), accum);
        }
        return accum;
    }

    private static int accumulateSourceRGB(double x, double y, double z, IDynamicLightSource source, int accum) {
        final int luminance = source.angelica$getLuminance();
        if (luminance <= 0) return accum;

        final int rgb = source.angelica$getLuminanceRGB();
        if (rgb == 0) return accum;

        final double dx = x - source.angelica$getDynamicLightX();
        final double dy = y - source.angelica$getDynamicLightY();
        final double dz = z - source.angelica$getDynamicLightZ();
        final double distSq = dx * dx + dy * dy + dz * dz;

        final double multiplier = levelAt(source, luminance, distSq) / luminance;
        if (multiplier <= 0) return accum;
        final int r = (int) (BlockLightProvider.unpackR(rgb) * multiplier);
        final int g = (int) (BlockLightProvider.unpackG(rgb) * multiplier);
        final int b = (int) (BlockLightProvider.unpackB(rgb) * multiplier);

        return BlockLightProvider.packRGB(
            Math.max(BlockLightProvider.unpackR(accum), r),
            Math.max(BlockLightProvider.unpackG(accum), g),
            Math.max(BlockLightProvider.unpackB(accum), b));
    }

    public double getDynamicLightLevelFromSources(double x, double y, double z, List<IDynamicLightSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return 0;
        }
        double result = 0;
        for (int i = 0, size = sources.size(); i < size; i++) {
            result = maxDynamicLightLevelExact(x, y, z, sources.get(i), result);
        }
        return MathHelper.clamp_double(result, 0, 15);
    }

    /**
     * Returns the lightmap with combined light levels.
     *
     * @param dynamicLightLevel the dynamic light level
     * @param lightmap          the vanilla lightmap coordinates
     * @return the modified lightmap coordinates
     */
    public int getLightmapWithDynamicLight(double dynamicLightLevel, int lightmap) {
        if (dynamicLightLevel > 0) {
            // lightmap is (skyLevel << 20 | blockLevel << 4)

            // Get vanilla block light level.
            final int blockLevel = (lightmap & 0xFFFF) >> 4;
            if (dynamicLightLevel > blockLevel) {
                // Equivalent to a << 4 bitshift with a little quirk: this one ensure more precision (more decimals are saved).
                final int luminance = (int) (dynamicLightLevel * 16.0);
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
    public static void scheduleChunkRebuild(@NotNull IDynamicLightWorldRenderer renderer, @NotNull IBlockPos chunkPos) {
        scheduleChunkRebuild(renderer, chunkPos.getX(), chunkPos.getY(), chunkPos.getZ());
    }

    /**
     * Schedules a chunk rebuild at the specified chunk position.
     *
     * @param renderer the renderer
     * @param chunkPos the packed chunk position
     */
    public static void scheduleChunkRebuild(@NotNull IDynamicLightWorldRenderer renderer, long chunkPos) {
        scheduleChunkRebuild(renderer, CoordinatePacker.unpackX(chunkPos), CoordinatePacker.unpackY(chunkPos), CoordinatePacker.unpackZ(chunkPos));
    }

    public static void scheduleChunkRebuild(@NotNull IDynamicLightWorldRenderer renderer, int x, int y, int z) {
        if (FrustumCullingEnabled && instance != null) {
            instance.chunkRebuildManager.requestRebuild(x, y, z);
        } else {
            renderer.scheduleRebuildForChunk(x, y, z, false);
        }
    }

    public static void scheduleChunkRebuildForRemoval(@NotNull IDynamicLightWorldRenderer renderer, int x, int y, int z) {
        renderer.scheduleRebuildForChunk(x, y, z, false);
    }

    public static void scheduleChunkRebuildForRemoval(@NotNull IDynamicLightWorldRenderer renderer, long chunkPos) {
        scheduleChunkRebuildForRemoval(renderer, CoordinatePacker.unpackX(chunkPos), CoordinatePacker.unpackY(chunkPos), CoordinatePacker.unpackZ(chunkPos));
    }

    public void processChunkRebuilds(@Nullable Viewport viewport) {
        if (activeRenderer == null) {
            return;
        }
        chunkRebuildManager.processVisible(viewport, activeRenderer);
    }

    public ChunkRebuildManager getChunkRebuildManager() {
        return chunkRebuildManager;
    }

    /**
     * Updates the tracked chunk sets.
     *
     * @param chunkPos the packed chunk position
     * @param old      the set of old chunk coordinates to remove this chunk from it
     * @param newPos   the set of new chunk coordinates to add this chunk to it
     */
    public static void updateTrackedChunks(@NotNull IBlockPos chunkPos, @Nullable LongOpenHashSet old, @Nullable LongOpenHashSet newPos) {
        if (old != null || newPos != null) {
            final long pos = chunkPos.asLong();
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
        final boolean enabled = lightSource.angelica$isDynamicLightEnabled();
        final int luminance = lightSource.angelica$getLuminance();

        if (!enabled && luminance > 0) {
            lightSource.angelica$setDynamicLightEnabled(true);
        } else if (enabled && luminance < 1) {
            lightSource.angelica$setDynamicLightEnabled(false);
        }
    }

    /**
     * Returns the luminance from an item stack.
     *
     * @param stack            the item stack
     * @param submergedInWater {@code true} if the stack is submerged in water, else {@code false}
     * @return the luminance of the item
     */
    public static int getLuminanceFromItemStack(@NotNull ItemStack stack) {
        final Item item = stack.getItem();
        if (item instanceof IDynamicLightProducer lightProducer) {
            return lightProducer.getLuminance(stack);
        } else if (item instanceof ItemBlock itemBlock) {
            return ItemBlockLight.getLightValue(itemBlock, stack);
        }

        if (item == Items.lava_bucket) return Blocks.lava.getLightValue();

        return 0;
    }

    public static int getLuminanceFromEntityItem(@NotNull EntityItem item, int stackLuminance) {
        if (stackLuminance <= 0) return 0;
        if (item.isBurning()) return 15;
        // TODO only have certain items not glow in water?
        if (item.isInsideOfMaterial(Material.water)) return 0;
        return stackLuminance;
    }

    public static int getLuminanceFromEntity(@NotNull Entity entity) {
        if (entity instanceof EntityItem item) {
            return getLuminanceFromEntityItem(item, getLuminanceFromItemStack(item.getEntityItem()));
        }

        if (entity.isBurning()) return 15;

        if (entity.isInsideOfMaterial(Material.water)) return 0;

        int luminance = 0;
        if (entity instanceof EntityLivingBase living) {

            // check equipment + hand for light (should work for all entities)
            ItemStack itemStack;
            for (int i = 0; i < 5; i++) {
                if ((itemStack = living.getEquipmentInSlot(i)) != null) {
                    luminance = Math.max(luminance, getLuminanceFromItemStack(itemStack));
                }
            }

            if (living instanceof EntityPlayer player) {
                if (ModStatus.isBattlegearLoaded &&
                    player instanceof IBattlePlayer battlePlayer &&
                    battlePlayer.battlegear2$isBattlemode()
                ) {
                    final ItemStack offhand = Battlegear2Compat.getBattlegear2Offhand(player);
                    if (offhand != null) {
                        luminance = Math.max(luminance, getLuminanceFromItemStack(offhand));
                    }
                } else if (ModStatus.isBackhandLoaded){
                    final ItemStack offhand = BackhandReflectionCompat.getOffhandItem(player);
                    if (offhand != null) {
                        luminance = Math.max(luminance, getLuminanceFromItemStack(offhand));
                    }
                }

                if (ModStatus.isBaublesLoaded){
                    final var playerBaubles = PlayerHandler.getPlayerBaubles(player);
                    if (playerBaubles != null){
                        for (int i = 0; i < playerBaubles.getSizeInventory(); i++){
                            final var stack = playerBaubles.getStackInSlot(i);
                            if (stack != null){
                                luminance = Math.max(luminance, getLuminanceFromItemStack(stack));
                            }
                        }
                    }
                }

                if (ModStatus.isCosmeticArmorReworkedLoaded) {
                    final var cosArmor = CosmeticArmorReworked.invMan.getCosArmorInventoryClient(player.getUniqueID());
                    if (cosArmor != null) {
                        for (int i = 0; i < cosArmor.getSizeInventory(); i++) {
                            final var stack = cosArmor.getStackInSlot(i);
                            if (stack != null) {
                                luminance = Math.max(luminance, getLuminanceFromItemStack(stack));
                            }
                        }
                    }
                }
            }

            return luminance;
        }

        // TODO: Creepers? TNT? Dusts?
        if (entity instanceof PrimedEntityAccessor primedEntity) {
            luminance = Math.max(luminance, primedEntity.angelica$getLuminance());
        }

        return luminance;
    }
}

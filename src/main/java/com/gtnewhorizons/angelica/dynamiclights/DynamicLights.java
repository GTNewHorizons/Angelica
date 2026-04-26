package com.gtnewhorizons.angelica.dynamiclights;

import baubles.common.lib.PlayerHandler;
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
import com.gtnewhorizons.angelica.mixins.interfaces.PrimedEntityAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mods.battlegear2.api.core.IBattlePlayer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.block.Block;
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
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    private static final double MAX_RADIUS_SQUARED = MAX_RADIUS * MAX_RADIUS;
    private final Set<IDynamicLightSource> dynamicLightSources = new ObjectOpenHashSet<>();
    private IDynamicLightSource[] lightSourceArray = new IDynamicLightSource[0];
    private final ReentrantReadWriteLock lightSourcesLock = new ReentrantReadWriteLock();
    private volatile int lightSourceCount = 0;
    private final ChunkRebuildManager chunkRebuildManager = new ChunkRebuildManager();
    private long lastUpdate = System.currentTimeMillis();
    private int lastUpdateCount = 0;

    /** Must be called under write lock. */
    private void rebuildLightSourceArray() {
        this.lightSourceArray = this.dynamicLightSources.toArray(new IDynamicLightSource[0]);
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
        if (!isEnabled())
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
            for (var lightSource : this.lightSourceArray) {
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
        this.lightSourceArray = new IDynamicLightSource[0];

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
        for (var lightSource : this.lightSourceArray) {
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
        for (var lightSource : this.lightSourceArray) {
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
            // 7.75 because else we would have to update more chunks and that's not a good idea.
            // 15 (max range for blocks) would be too much and a bit cheaty.
            if (distanceSquared <= MAX_RADIUS_SQUARED) {
                final double multiplier = 1.0 - Math.sqrt(distanceSquared) / MAX_RADIUS;
                final double lightLevel = multiplier * (double) luminance;
                if (lightLevel > currentLightLevel) {
                    return lightLevel;
                }
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
            if (distanceSquared <= MAX_RADIUS_SQUARED) {
                final double multiplier = 1.0 - Math.sqrt(distanceSquared) / MAX_RADIUS;
                final double lightLevel = multiplier * (double) luminance;
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

    public List<IDynamicLightSource> getSourcesForChunk(int chunkMinX, int chunkMinY, int chunkMinZ) {
        // Chunk center
        final double centerX = chunkMinX + 8.0;
        final double centerY = chunkMinY + 8.0;
        final double centerZ = chunkMinZ + 8.0;

        // Max distance from chunk center to any corner is sqrt(3) * 8 ≈ 13.86
        // Add MAX_RADIUS to get the search radius (13.86 + 7.75)^2 ≈ 467
        final double searchRadiusSq = 470.0; // Slightly padded for safety

        List<IDynamicLightSource> result = null;

        this.lightSourcesLock.readLock().lock();
        try {
            for (var source : this.lightSourceArray) {
                final double dx = source.angelica$getDynamicLightX() - centerX;
                final double dy = source.angelica$getDynamicLightY() - centerY;
                final double dz = source.angelica$getDynamicLightZ() - centerZ;
                final double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq <= searchRadiusSq) {
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
            for (var lightSource : this.lightSourceArray) {
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

        if (distSq > MAX_RADIUS_SQUARED) return accum;

        final double multiplier = 1.0 - Math.sqrt(distSq) / MAX_RADIUS;
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
        if (item instanceof ItemBlock itemBlock) {
            final Block block = itemBlock.field_150939_a;
            if (block != null) {
                return block.getLightValue();
            }
        } else if (item instanceof IDynamicLightProducer lightProducer){
            return lightProducer.getLuminance();
        }

        if (item == Items.lava_bucket) return Blocks.lava.getLightValue();

        return 0;
    }

    public static int getLuminanceFromEntity(@NotNull Entity entity) {

        if (entity.isBurning()) return 15;

        final boolean inWater = entity.isInsideOfMaterial(Material.water);

        // TODO only have certain items not glow in water?
        if (inWater) return 0;

        if (entity instanceof EntityItem item) {
            return getLuminanceFromItemStack(item.getEntityItem());
        }

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
                }
                else if (ModStatus.isBackhandLoaded){
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

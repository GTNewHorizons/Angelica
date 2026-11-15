package com.gtnewhorizons.angelica.dynamiclights;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.blockpos.IBlockPos;
import com.gtnewhorizons.angelica.api.dynamiclights.PositionalLight;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.MathHelper;
import org.jetbrains.annotations.NotNull;

/**
 * {@link PositionalLight} for mods. Tracks chunks like entity lights do (see MixinEntity), but only the sections its
 * light gets into, worked out from its reach: 8 or fewer up to reach 7, mostly 8 for a held torch, up to 27 for a long
 * one. Plus the ones it left.
 */
public final class PositionalLightSource implements IDynamicLightSource, PositionalLight {

    private volatile double x, y, z;
    private volatile int luminance;
    private volatile boolean litUnderShaderpacks;
    private volatile float reach = TORCH_REACH;
    private boolean removed;

    private int lastLuminance;
    private float lastReach = TORCH_REACH;
    private double prevX, prevY, prevZ;
    private LongOpenHashSet tracked = new LongOpenHashSet();
    private LongOpenHashSet prevTracked = new LongOpenHashSet();

    @Override
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        // Also adds it back after a world unload drops Angelica's lights
        syncTracking();
    }

    @Override
    public void setLuminance(int luminance) {
        if (removed) return;
        this.luminance = Math.max(0, Math.min(15, luminance));
        syncTracking();
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }

    @Override
    public int getLuminance() {
        return luminance;
    }

    @Override
    public void setLitUnderShaderpacks(boolean lit) {
        if (litUnderShaderpacks == lit) return;
        litUnderShaderpacks = lit;
        DynamicLights.get().onShaderpackLitChanged(this);
        // It may have just gone dark (or lit) under a pack. Redo its chunks either way, the next update puts the light
        // back in where it still counts
        angelica$resetDynamicLight();
        final IDynamicLightWorldRenderer renderer = DynamicLights.getActiveRenderer();
        if (renderer != null) angelica$scheduleTrackedChunksRebuild(renderer);
    }

    @Override
    public void setReach(float reach) {
        // Anything under 0 (or NaN) is the held torch
        this.reach = reach >= 0 ? Math.min(15, reach) : TORCH_REACH;
    }

    @Override
    public float getReach() {
        return reach;
    }

    @Override
    public float angelica$getReach() {
        return reach;
    }

    @Override
    public boolean isLitUnderShaderpacks() {
        return litUnderShaderpacks;
    }

    @Override
    public boolean angelica$isLitUnderShaderpacks() {
        return litUnderShaderpacks;
    }

    @Override
    public void remove() {
        if (removed) return;
        removed = true;
        luminance = 0;
        syncTracking();
    }

    // In Angelica's lights while lit and out of them otherwise, dynamic lights on or not. DynamicLights.updateTracking
    // only removes while they're on, and re-adds on every call while they're off
    private void syncTracking() {
        final DynamicLights lights = DynamicLights.get();
        final boolean lit = luminance > 0;
        if (lit == lights.containsLightSource(this)) return;
        angelica$resetDynamicLight();
        if (lit) lights.addLightSource(this);
        else lights.removeLightSource(this);
    }

    @Override
    public double angelica$getDynamicLightX() {
        return x;
    }

    @Override
    public double angelica$getDynamicLightY() {
        return y;
    }

    @Override
    public double angelica$getDynamicLightZ() {
        return z;
    }

    @Override
    public void angelica$resetDynamicLight() {
        lastLuminance = 0;
    }

    @Override
    public int angelica$getLuminance() {
        return luminance;
    }

    @Override
    public void angelica$dynamicLightTick() {}

    @Override
    public boolean angelica$updateDynamicLight(@NotNull IDynamicLightWorldRenderer renderer) {
        final double x = this.x, y = this.y, z = this.z;
        final int luminance = this.luminance;
        final float reach = this.reach;
        // Small changes in reach count like small moves, a structure being scaled changes it every frame
        if (Math.abs(x - prevX) <= 0.1 && Math.abs(y - prevY) <= 0.1 && Math.abs(z - prevZ) <= 0.1
            && luminance == lastLuminance && Math.abs(reach - lastReach) <= 0.1) {
            return false;
        }
        prevX = x;
        prevY = y;
        prevZ = z;
        lastLuminance = luminance;
        lastReach = reach;

        final LongOpenHashSet newTracked = prevTracked;
        newTracked.clear();
        // Every section the light gets into, plus a block: smooth lighting on a face reads the cells in front of it,
        // which can be in the next section. Reach is at most 15, so never past the sections right next to this one
        final double far = DynamicLights.reachOf(this) + 1;
        if (far > 1) {
            final int cx = MathHelper.floor_double(x) >> 4, cy = MathHelper.floor_double(y) >> 4,
                cz = MathHelper.floor_double(z) >> 4;
            final int x0 = x - (cx << 4) < far ? -1 : 0, x1 = ((cx + 1) << 4) - x < far ? 1 : 0;
            final int y0 = y - (cy << 4) < far ? -1 : 0, y1 = ((cy + 1) << 4) - y < far ? 1 : 0;
            final int z0 = z - (cz << 4) < far ? -1 : 0, z1 = ((cz + 1) << 4) - z < far ? 1 : 0;
            for (int dx = x0; dx <= x1; dx++) {
                for (int dy = y0; dy <= y1; dy++) {
                    for (int dz = z0; dz <= z1; dz++) track(renderer, new BlockPos(cx + dx, cy + dy, cz + dz), newTracked);
                }
            }
        }

        // Whatever's left in the old set is out of range now
        angelica$scheduleTrackedChunksRebuild(renderer);
        prevTracked = tracked;
        tracked = newTracked;
        return true;
    }

    private void track(IDynamicLightWorldRenderer renderer, IBlockPos chunk, LongOpenHashSet newTracked) {
        DynamicLights.scheduleChunkRebuild(renderer, chunk);
        DynamicLights.updateTrackedChunks(chunk, tracked, newTracked);
    }

    @Override
    public void angelica$scheduleTrackedChunksRebuild(@NotNull IDynamicLightWorldRenderer renderer) {
        final LongIterator it = tracked.iterator();
        while (it.hasNext()) DynamicLights.scheduleChunkRebuildForRemoval(renderer, it.nextLong());
    }
}

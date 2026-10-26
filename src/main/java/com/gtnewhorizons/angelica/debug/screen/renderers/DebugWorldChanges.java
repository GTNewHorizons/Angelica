package com.gtnewhorizons.angelica.debug.screen.renderers;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IWorldAccess;

final class DebugWorldChanges implements IWorldAccess {

    final int[] box;
    final boolean[] dirty;

    DebugWorldChanges(int slots) {
        box = new int[slots * 6];
        dirty = new boolean[slots];
        for (int i = 0; i < slots; i++) clearBox(i);
    }

    void watch(int slot, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) return;
        final int o = slot * 6;
        if (minX < box[o]) box[o] = minX;
        if (minY < box[o + 1]) box[o + 1] = minY;
        if (minZ < box[o + 2]) box[o + 2] = minZ;
        if (maxX > box[o + 3]) box[o + 3] = maxX;
        if (maxY > box[o + 4]) box[o + 4] = maxY;
        if (maxZ > box[o + 5]) box[o + 5] = maxZ;
    }

    void clearBox(int slot) {
        final int o = slot * 6;
        box[o] = Integer.MAX_VALUE;
        box[o + 1] = Integer.MAX_VALUE;
        box[o + 2] = Integer.MAX_VALUE;
        box[o + 3] = Integer.MIN_VALUE;
        box[o + 4] = Integer.MIN_VALUE;
        box[o + 5] = Integer.MIN_VALUE;
        dirty[slot] = false;
    }

    boolean isDirty(int slot) {
        return dirty[slot];
    }

    void touch(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        final int n = dirty.length;
        for (int i = 0, o = 0; i < n; i++, o += 6) {
            if (box[o] > box[o + 3]) continue;
            if (maxX < box[o] || minX > box[o + 3]) continue;
            if (maxY < box[o + 1] || minY > box[o + 4]) continue;
            if (maxZ < box[o + 2] || minZ > box[o + 5]) continue;
            dirty[i] = true;
        }
    }

    @Override
    public void markBlockForUpdate(int x, int y, int z) {
        touch(x, y, z, x, y, z);
    }

    @Override
    public void markBlockForRenderUpdate(int x, int y, int z) {
        touch(x, y, z, x, y, z);
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x0, int y0, int z0, int x1, int y1, int z1) {
        touch(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
    }

    @Override
    public void playSound(String name, double x, double y, double z, float volume, float pitch) {}

    @Override
    public void playSoundToNearExcept(EntityPlayer player, String name, double x, double y, double z, float volume, float pitch) {}

    @Override
    public void spawnParticle(String name, double x, double y, double z, double motionX, double motionY, double motionZ) {}

    @Override
    public void onEntityCreate(Entity entity) {}

    @Override
    public void onEntityDestroy(Entity entity) {}

    @Override
    public void playRecord(String name, int x, int y, int z) {}

    @Override
    public void broadcastSound(int id, int x, int y, int z, int data) {}

    @Override
    public void playAuxSFX(EntityPlayer player, int id, int x, int y, int z, int data) {}

    @Override
    public void destroyBlockPartially(int breakerId, int x, int y, int z, int progress) {}

    @Override
    public void onStaticEntitiesChanged() {}
}

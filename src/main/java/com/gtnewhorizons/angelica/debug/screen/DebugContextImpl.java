package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public final class DebugContextImpl implements DebugContext {

    private static final long SAMPLE_INTERVAL_NS = 500_000_000L;

    private World world;
    private EntityClientPlayerMP player;
    private Chunk chunk;
    private int blockX;
    private int blockY;
    private int blockZ;
    private long nanoTime;
    private boolean sampleDue;
    private boolean sampled;
    private long sampledVersion;
    private long sampledNanos;
    private DebugEntryDef entry;
    private boolean entryFirstSample;

    public void prepare(Minecraft mc, long nanoTime, long entriesVersion) {
        final World w = mc.theWorld;
        final EntityClientPlayerMP p = mc.thePlayer;
        world = w;
        player = p;
        chunk = null;
        if (w != null && p != null) {
            blockX = MathHelper.floor_double(p.posX);
            blockY = MathHelper.floor_double(p.boundingBox.minY);
            blockZ = MathHelper.floor_double(p.posZ);
            final Chunk column = w.getChunkFromChunkCoords(blockX >> 4, blockZ >> 4);
            if (column != null && !column.isEmpty()) chunk = column;
        }
        this.nanoTime = nanoTime;
        sampleDue = !sampled || entriesVersion != sampledVersion || nanoTime - sampledNanos >= SAMPLE_INTERVAL_NS;
        if (sampleDue) {
            sampled = true;
            sampledVersion = entriesVersion;
            sampledNanos = nanoTime;
        }
    }

    void beginEntry(DebugEntryDef def) {
        entry = def;
        entryFirstSample = false;
    }

    public void release() {
        world = null;
        player = null;
        chunk = null;
        entry = null;
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public EntityClientPlayerMP player() {
        return player;
    }

    @Override
    public int blockX() {
        return blockX;
    }

    @Override
    public int blockY() {
        return blockY;
    }

    @Override
    public int blockZ() {
        return blockZ;
    }

    @Override
    public Chunk chunk() {
        return chunk;
    }

    @Override
    public long nanoTime() {
        return nanoTime;
    }

    @Override
    public boolean isSampleDue() {
        final DebugEntryDef def = entry;
        if (def != null && !def.sampleOffered) {
            def.sampleOffered = true;
            entryFirstSample = true;
        }
        return sampleDue || entryFirstSample;
    }
}

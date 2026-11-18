package com.gtnewhorizons.angelica.api.debug;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public interface DebugContext {

    World world();

    EntityClientPlayerMP player();

    int blockX();

    int blockY();

    int blockZ();

    Chunk chunk();

    long nanoTime();

    boolean isSampleDue();
}

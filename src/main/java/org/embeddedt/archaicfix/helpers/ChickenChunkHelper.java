package org.embeddedt.archaicfix.helpers;

import codechicken.chunkloader.ChunkLoaderManager;
import net.minecraft.world.WorldServer;

public class ChickenChunkHelper {
    public static void load(WorldServer world) {
        ChunkLoaderManager.load(world);
    }
}

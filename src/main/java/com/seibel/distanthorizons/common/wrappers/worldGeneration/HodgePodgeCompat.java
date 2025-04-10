
package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import com.mitchej123.hodgepodge.SimulationDistanceHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

public class HodgePodgeCompat {
    public static void preventChunkSimulation(World world, ChunkCoordIntPair chunk, boolean prevent) {
        SimulationDistanceHelper.preventChunkSimulation(world, chunk, prevent);
    }
}

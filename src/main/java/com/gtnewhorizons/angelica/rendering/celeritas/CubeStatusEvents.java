package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;

import com.cardinalstar.cubicchunks.api.event.CubeEvent;
import com.cardinalstar.cubicchunks.util.CubePos;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class CubeStatusEvents {

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new CubeStatusEvents());
    }

    private ChunkTracker getTracker() {
        return ChunkTrackerHolder.get(Minecraft.getMinecraft().theWorld);
    }

    @SubscribeEvent
    public void onCubeLoaded(CubeEvent.Load event) {
        if (!event.world.isRemote) return;

        if (getTracker() instanceof CubeStatusTracker cubic) {
            CubePos pos = event.pos;
            cubic.onCubeLoaded(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    @SubscribeEvent
    public void onCubeUnloaded(CubeEvent.Unload event) {
        if (!event.world.isRemote) return;

        if (getTracker() instanceof CubeStatusTracker cubic) {
            CubePos pos = event.pos;
            cubic.onCubeUnloaded(pos.getX(), pos.getY(), pos.getZ());
        }
    }
}

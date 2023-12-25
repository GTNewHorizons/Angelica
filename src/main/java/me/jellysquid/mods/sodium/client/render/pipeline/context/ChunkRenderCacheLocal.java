package me.jellysquid.mods.sodium.client.render.pipeline.context;

import lombok.Getter;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.cache.ArrayLightDataCache;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.ChunkRenderCache;
import me.jellysquid.mods.sodium.client.render.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

public class ChunkRenderCacheLocal extends ChunkRenderCache {
    private final ArrayLightDataCache lightDataCache;

    @Getter
    private final BlockRenderer blockRenderer;
    @Getter
    private final FluidRenderer fluidRenderer;
    @Getter
    private final WorldSlice worldSlice;

    public ChunkRenderCacheLocal(Minecraft client, WorldClient world) {
        this.worldSlice = new WorldSlice(world);
        this.lightDataCache = new ArrayLightDataCache(this.worldSlice);

        final LightPipelineProvider lightPipelineProvider = new LightPipelineProvider(lightDataCache);

        this.blockRenderer = new BlockRenderer(lightPipelineProvider);
        this.fluidRenderer = new FluidRenderer(lightPipelineProvider);

    }

    public void init(ChunkRenderContext context) {
        this.lightDataCache.reset(context.getOrigin());
        this.worldSlice.copyData(context);
    }

}

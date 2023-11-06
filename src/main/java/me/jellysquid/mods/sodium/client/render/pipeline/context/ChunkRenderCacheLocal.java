package me.jellysquid.mods.sodium.client.render.pipeline.context;

import com.gtnewhorizons.angelica.compat.mojang.BlockModels;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.cache.ArrayLightDataCache;
import me.jellysquid.mods.sodium.client.model.quad.blender.BiomeColorBlender;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.ChunkRenderCache;
import me.jellysquid.mods.sodium.client.render.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.WorldSliceLocal;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

public class ChunkRenderCacheLocal extends ChunkRenderCache {
    private final ArrayLightDataCache lightDataCache;

    private final BlockRenderer blockRenderer;
    private final FluidRenderer fluidRenderer;

    private final BlockModels blockModels;
    private final WorldSlice worldSlice;
    private WorldSliceLocal localSlice;

    public ChunkRenderCacheLocal(Minecraft client, World world) {
        this.worldSlice = new WorldSlice(world);
        this.lightDataCache = new ArrayLightDataCache(this.worldSlice);

        LightPipelineProvider lightPipelineProvider = new LightPipelineProvider(this.lightDataCache);
        BiomeColorBlender biomeColorBlender = this.createBiomeColorBlender();

        this.blockRenderer = new BlockRenderer(client, lightPipelineProvider, biomeColorBlender);
        this.fluidRenderer = new FluidRenderer(client, lightPipelineProvider, biomeColorBlender);

        // TODO: Sodium
        this.blockModels = null; // client.getBakedModelManager().getBlockModels();
    }

    public BlockModels getBlockModels() {
        return this.blockModels;
    }

    public BlockRenderer getBlockRenderer() {
        return this.blockRenderer;
    }

    public FluidRenderer getFluidRenderer() {
        return this.fluidRenderer;
    }

    public void init(ChunkRenderContext context) {
        this.lightDataCache.reset(context.getOrigin());
        this.worldSlice.copyData(context);
        // create the new local slice here so that it's unique whenever we copy new data
        // this is passed into mod code, since some depend on the provided BlockRenderView object being unique each time
        this.localSlice = new WorldSliceLocal(this.worldSlice);
    }

    public WorldSlice getWorldSlice() {
        return this.worldSlice;
    }

    public WorldSliceLocal getLocalSlice() {
        return this.localSlice;
    }
}

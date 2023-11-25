package me.jellysquid.mods.sodium.client.render.pipeline.context;

import com.gtnewhorizons.angelica.compat.mojang.BlockModels;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.ChunkRenderCache;
import me.jellysquid.mods.sodium.client.render.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

public class ChunkRenderCacheLocal extends ChunkRenderCache {
    private final BlockRenderer blockRenderer;
    private final FluidRenderer fluidRenderer;

    private final BlockModels blockModels;
    private final WorldSlice worldSlice;

    public ChunkRenderCacheLocal(Minecraft client, WorldClient world) {
        this.worldSlice = new WorldSlice(world);

        this.blockRenderer = new BlockRenderer(client);
        this.fluidRenderer = new FluidRenderer(client);

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
        this.worldSlice.copyData(context);
    }

    public WorldSlice getWorldSlice() {
        return this.worldSlice;
    }
}

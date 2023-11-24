package me.jellysquid.mods.sodium.client.render.pipeline.context;

import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.cache.HashLightDataCache;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.ChunkRenderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;

import java.util.Map;

public class ChunkRenderCacheShared extends ChunkRenderCache {
    private static final Map<World, ChunkRenderCacheShared> INSTANCES = new Reference2ObjectOpenHashMap<>();

    private final BlockRenderer blockRenderer;
    private final HashLightDataCache lightCache;

    private ChunkRenderCacheShared(WorldClient world) {
        Minecraft client = Minecraft.getMinecraft();

        this.lightCache = new HashLightDataCache((BlockRenderView)world);

        this.blockRenderer = new BlockRenderer(client);
    }

    public BlockRenderer getBlockRenderer() {
        return this.blockRenderer;
    }

    private void resetCache() {
        this.lightCache.clearCache();
    }

    public static ChunkRenderCacheShared getInstance(BlockRenderView world) {
        ChunkRenderCacheShared instance = INSTANCES.get(world);

        if (instance == null) {
            throw new IllegalStateException("No global renderer exists");
        }

        return instance;
    }

    public static void destroyRenderContext(BlockRenderView world) {
        if (INSTANCES.remove(world) == null) {
            throw new IllegalStateException("No render context exists for world: " + world);
        }
    }

    public static void createRenderContext(WorldClient world) {
        if (INSTANCES.containsKey(world)) {
            throw new IllegalStateException("Render context already exists for world: " + world);
        }

        INSTANCES.put(world, new ChunkRenderCacheShared(world));
    }

    public static void resetCaches() {
        for (ChunkRenderCacheShared context : INSTANCES.values()) {
            context.resetCache();
        }
    }
}

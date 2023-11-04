package me.jellysquid.mods.sodium.client.render.pipeline;

import me.jellysquid.mods.sodium.client.model.quad.blender.BiomeColorBlender;
import net.minecraft.client.MinecraftClient;

public class ChunkRenderCache {
    protected BiomeColorBlender createBiomeColorBlender() {
    	 return BiomeColorBlender.create(MinecraftClient.getInstance());
    }
}

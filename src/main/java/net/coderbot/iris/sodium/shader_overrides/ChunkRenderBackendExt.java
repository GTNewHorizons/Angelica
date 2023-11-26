package net.coderbot.iris.sodium.shader_overrides;

import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;

public interface ChunkRenderBackendExt {
	void iris$begin(PoseStack poseStack, BlockRenderPass pass);
}

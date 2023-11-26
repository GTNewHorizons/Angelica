package net.coderbot.iris.sodium.shader_overrides;

import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;

public interface ChunkRenderBackendExt {
	void iris$begin(MatrixStack matrixStack, BlockRenderPass pass);
}

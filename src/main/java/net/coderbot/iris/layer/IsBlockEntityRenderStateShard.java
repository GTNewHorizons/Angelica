package net.coderbot.iris.layer;

import com.gtnewhorizons.angelica.compat.mojang.RenderPhase;

public class IsBlockEntityRenderStateShard extends RenderPhase {
	public static final IsBlockEntityRenderStateShard INSTANCE = new IsBlockEntityRenderStateShard();

	private IsBlockEntityRenderStateShard() {
		super("iris:is_block_entity", GbufferPrograms::beginBlockEntities, GbufferPrograms::endBlockEntities);
	}
}

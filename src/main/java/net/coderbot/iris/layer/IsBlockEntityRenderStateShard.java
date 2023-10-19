package net.coderbot.iris.layer;

import net.coderbot.iris.compat.mojang.RenderStateShard;

public class IsBlockEntityRenderStateShard extends RenderStateShard {
	public static final IsBlockEntityRenderStateShard INSTANCE = new IsBlockEntityRenderStateShard();

	private IsBlockEntityRenderStateShard() {
		super("iris:is_block_entity", GbufferPrograms::beginBlockEntities, GbufferPrograms::endBlockEntities);
	}
}

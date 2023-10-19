package net.coderbot.iris.layer;

import net.coderbot.iris.compat.mojang.RenderStateShard;

public class IsEntityRenderStateShard extends RenderStateShard {
	public static final IsEntityRenderStateShard INSTANCE = new IsEntityRenderStateShard();

	private IsEntityRenderStateShard() {
		super("iris:is_entity", GbufferPrograms::beginEntities, GbufferPrograms::endEntities);
	}
}

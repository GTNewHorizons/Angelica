package net.coderbot.iris.layer;

import net.coderbot.iris.compat.mojang.RenderStateShard;

public class IsOutlineRenderStateShard extends RenderStateShard {
	public static final IsOutlineRenderStateShard INSTANCE = new IsOutlineRenderStateShard();

	private IsOutlineRenderStateShard() {
		super("iris:is_outline", GbufferPrograms::beginOutline, GbufferPrograms::endOutline);
	}
}

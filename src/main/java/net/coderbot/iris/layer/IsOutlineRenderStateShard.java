package net.coderbot.iris.layer;

import com.gtnewhorizons.angelica.compat.mojang.RenderStateShard;

public class IsOutlineRenderStateShard extends RenderStateShard {
	public static final IsOutlineRenderStateShard INSTANCE = new IsOutlineRenderStateShard();

	private IsOutlineRenderStateShard() {
		super("iris:is_outline", GbufferPrograms::beginOutline, GbufferPrograms::endOutline);
	}
}

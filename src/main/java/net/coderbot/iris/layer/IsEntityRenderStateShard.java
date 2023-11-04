package net.coderbot.iris.layer;

import com.gtnewhorizons.angelica.compat.mojang.RenderStateShard;

public class IsEntityRenderStateShard extends RenderStateShard {
	public static final IsEntityRenderStateShard INSTANCE = new IsEntityRenderStateShard();

	private IsEntityRenderStateShard() {
		super("iris:is_entity", GbufferPrograms::beginEntities, GbufferPrograms::endEntities);
	}
}

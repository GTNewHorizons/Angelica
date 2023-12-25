package net.coderbot.iris.layer;

import com.gtnewhorizons.angelica.compat.toremove.RenderPhase;

public class IsEntityRenderStateShard extends RenderPhase {
	public static final IsEntityRenderStateShard INSTANCE = new IsEntityRenderStateShard();

	private IsEntityRenderStateShard() {
		super("iris:is_entity", GbufferPrograms::beginEntities, GbufferPrograms::endEntities);
	}
}

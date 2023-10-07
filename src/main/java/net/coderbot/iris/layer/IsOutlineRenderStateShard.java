package net.coderbot.iris.layer;


public class IsOutlineRenderStateShard extends RenderStateShard {
	public static final IsOutlineRenderStateShard INSTANCE = new IsOutlineRenderStateShard();

	private IsOutlineRenderStateShard() {
		super("iris:is_outline", GbufferPrograms::beginOutline, GbufferPrograms::endOutline);
	}
}

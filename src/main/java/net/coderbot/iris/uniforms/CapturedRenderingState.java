package net.coderbot.iris.uniforms;

import lombok.Getter;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;

public class CapturedRenderingState {
	public static final CapturedRenderingState INSTANCE = new CapturedRenderingState();

	@Getter
    private float tickDelta;
	@Getter
    private int currentRenderedBlockEntity;
	private Runnable blockEntityIdListener = null;

	@Getter
    private int currentRenderedEntity = -1;
	private Runnable entityIdListener = null;

	private CapturedRenderingState() {
	}

	public void setTickDelta(float tickDelta) {
		this.tickDelta = tickDelta;
	}

    public void setCurrentBlockEntity(int entity) {
		this.currentRenderedBlockEntity = entity;

		if (this.blockEntityIdListener != null) {
			this.blockEntityIdListener.run();
		}
	}

    public void setCurrentEntity(int entity) {
		this.currentRenderedEntity = entity;

		if (this.entityIdListener != null) {
			this.entityIdListener.run();
		}
	}

	public ValueUpdateNotifier getEntityIdNotifier() {
		return listener -> this.entityIdListener = listener;
	}

	public ValueUpdateNotifier getBlockEntityIdNotifier() {
		return listener -> this.blockEntityIdListener = listener;
	}
}

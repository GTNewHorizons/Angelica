package net.coderbot.iris.uniforms;

import lombok.Getter;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;

import org.joml.Vector4f;

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

	@Getter
	private int currentRenderedItem = 0;
	private Runnable itemIdListener = null;

    @Getter
    private final Vector4f currentEntityColor = new Vector4f();
    private Runnable entityColorListener = null;

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

    public void setCurrentEntityColor(float r, float g, float b, float a) {
        this.currentEntityColor.set(r, g, b, a);

        if (this.entityColorListener != null) {
            this.entityColorListener.run();
        }
    }

	public void setCurrentRenderedItem(int item) {
		if (this.currentRenderedItem == item) {
			return;
		}

		this.currentRenderedItem = item;

		if (this.itemIdListener != null) {
			this.itemIdListener.run();
		}
	}

	public ValueUpdateNotifier getEntityIdNotifier() {
		return listener -> this.entityIdListener = listener;
	}

	public ValueUpdateNotifier getBlockEntityIdNotifier() {
		return listener -> this.blockEntityIdListener = listener;
	}

	public ValueUpdateNotifier getItemIdNotifier() {
		return listener -> this.itemIdListener = listener;
	}

    public ValueUpdateNotifier getEntityColorNotifier() { return listener -> this.entityColorListener = listener; }
}

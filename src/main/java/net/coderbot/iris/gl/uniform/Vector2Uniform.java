package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.joml.Vector2f;

import java.util.function.Supplier;

public class Vector2Uniform extends Uniform {
	private Vector2f cachedValue;
	private final Supplier<Vector2f> value;

	Vector2Uniform(int location, Supplier<Vector2f> value) {
		super(location);

		this.cachedValue = null;
		this.value = value;

	}

	@Override
	public void update() {
		Vector2f newValue = value.get();

		if (cachedValue == null || !newValue.equals(cachedValue)) {
			cachedValue = newValue;
			RenderSystem.uniform2f(this.location, newValue.x, newValue.y);
		}
	}
}

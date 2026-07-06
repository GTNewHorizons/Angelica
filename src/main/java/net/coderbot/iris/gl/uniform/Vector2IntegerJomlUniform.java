package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.function.Supplier;

public class Vector2IntegerJomlUniform extends Uniform {
	private final Vector2i cachedValue;
	private final Supplier<Vector2ic> value;

	Vector2IntegerJomlUniform(int location, Supplier<Vector2ic> value) {
		this(location, value, null);
	}

	Vector2IntegerJomlUniform(int location, Supplier<Vector2ic> value, ValueUpdateNotifier notifier) {
		super(location, notifier);

		this.cachedValue = new Vector2i();
		this.value = value;
	}

	@Override
	public void update() {
		updateValue();

		if (notifier != null) {
			notifier.setListener(this::updateValue);
		}
	}

	private void updateValue() {
		Vector2ic newValue = value.get();

		if (!newValue.equals(cachedValue)) {
			cachedValue.set(newValue.x(), newValue.y());
			RenderSystem.uniform2i(this.location, cachedValue.x, cachedValue.y);
		}
	}
}

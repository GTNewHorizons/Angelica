package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import java.util.function.Supplier;

public class Vector3IntegerJomlUniform extends Uniform {
    private Vector3ic cachedValue;
    private final Supplier<Vector3ic> value;

    Vector3IntegerJomlUniform(int location, Supplier<Vector3ic> value) {
        this(location, value, null);
    }

    Vector3IntegerJomlUniform(int location, Supplier<Vector3ic> value, ValueUpdateNotifier notifier) {
        super(location, notifier);

        this.cachedValue = null;
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
        Vector3ic newValue = value.get();

        if (cachedValue == null || !newValue.equals(cachedValue)) {
            cachedValue = newValue;
            RenderSystem.uniform3i(this.location, newValue.x(), newValue.y(), newValue.z());
        }
    }
}

package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.function.Supplier;

public class MatrixFromFloatArrayUniform extends Uniform {
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private final float[] cachedValue = new float[16];
	private boolean hasValue;
	private final Supplier<float[]> value;

	MatrixFromFloatArrayUniform(int location, Supplier<float[]> value) {
		super(location);

		this.value = value;
	}

	@Override
	public void update() {
		float[] newValue = value.get();

		if (!hasValue || !Arrays.equals(newValue, cachedValue)) {
			hasValue = true;
			System.arraycopy(newValue, 0, cachedValue, 0, 16);

			buffer.put(cachedValue);
			buffer.rewind();

			RenderSystem.uniformMatrix4fv(location, false, buffer);
		}
	}
}

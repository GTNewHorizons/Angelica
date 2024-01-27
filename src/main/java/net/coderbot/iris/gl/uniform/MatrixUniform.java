package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

public class MatrixUniform extends Uniform {
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private Matrix4f cachedValue;
	private final Supplier<Matrix4f> value;

	MatrixUniform(int location, Supplier<Matrix4f> value) {
		super(location);

		this.cachedValue = null;
		this.value = value;
	}

	@Override
	public void update() {
        final Matrix4f newValue = value.get();
        if( newValue == null ){
            throw new RuntimeException("MatrixUniform value is null");
        }
        if (!newValue.equals(cachedValue)) {
            cachedValue = new Matrix4f(newValue);

            cachedValue.get(buffer);
            buffer.rewind();

            RenderSystem.uniformMatrix4fv(location, false, buffer);
        }
	}
}

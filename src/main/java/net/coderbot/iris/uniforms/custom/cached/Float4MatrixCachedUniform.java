package net.coderbot.iris.uniforms.custom.cached;

import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.parsing.MatrixType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.function.Supplier;

public class Float4MatrixCachedUniform extends VectorCachedUniform<Matrix4f> {
	final private FloatBuffer buffer = ByteBuffer.allocateDirect(16 << 2).order(ByteOrder.nativeOrder()).asFloatBuffer();

	public Float4MatrixCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Matrix4f> supplier) {
		super(name, updateFrequency, new Matrix4f(), supplier);
	}

	@Override
	protected void setFrom(Matrix4f other) {
		((Matrix4f) this.cached).set(other);
	}

	@Override
	public void push(int location) {
		// `gets` the values from the matrix and put's them into a buffer
		this.cached.get(buffer);
		GL20.glUniformMatrix4fv(location, false, buffer);
	}

	@Override
	public MatrixType<Matrix4f> getType() {
		return MatrixType.MAT4;
	}
}

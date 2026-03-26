package net.coderbot.iris.uniforms.custom.cached;

import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.parsing.VectorType;
import org.joml.Vector3i;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector3ic;

import java.util.function.Supplier;

public class Int3VectorCachedUniform extends VectorCachedUniform<Vector3ic> {

	public Int3VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector3ic> supplier) {
		super(name, updateFrequency, new Vector3i(), supplier);
	}

	@Override
	protected void setFrom(Vector3ic other) {
		((Vector3i)this.cached).set(other);
	}

	@Override
	public void push(int location) {
		GLStateManager.glUniform3i(location, this.cached.x(), this.cached.y(), this.cached.z());
	}

	@Override
	public VectorType getType() {
		return VectorType.I_VEC3;
	}
}

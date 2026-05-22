package net.coderbot.iris.gl.uniform;

import net.minecraft.util.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2ic;
import org.joml.Vector3d;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.joml.Vector4fc;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public interface UniformHolder {
	UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, FloatSupplier value);

	UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, IntSupplier value);

	UniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, DoubleSupplier value);

	UniformHolder uniform1i(UniformUpdateFrequency updateFrequency, String name, IntSupplier value);

	UniformHolder uniform1b(UniformUpdateFrequency updateFrequency, String name, BooleanSupplier value);

	UniformHolder uniform2f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2f> value);

	UniformHolder uniform2i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector2ic> value);

	UniformHolder uniform3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3fc> value);

    UniformHolder uniform3i(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3ic> value);

	UniformHolder uniformVanilla3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vec3> value);

	UniformHolder uniformTruncated3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4fc> value);

	UniformHolder uniform3d(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3d> value);

	UniformHolder uniform4f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector4fc> value);

    UniformHolder uniform4fArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value);

	UniformHolder uniformMatrix(UniformUpdateFrequency updateFrequency, String name, Supplier<Matrix4fc> value);

	UniformHolder uniformMatrixFromArray(UniformUpdateFrequency updateFrequency, String name, Supplier<float[]> value);

	UniformHolder externallyManagedUniform(String name, UniformType type);
}

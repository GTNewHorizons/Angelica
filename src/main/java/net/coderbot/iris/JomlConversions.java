package net.coderbot.iris;

import net.minecraft.util.Vec3;
import org.joml.Vector3d;
import org.joml.Vector4f;

public class JomlConversions {
	public static Vector3d fromVec3(Vec3 vec) {
		return new Vector3d(vec.xCoord, vec.yCoord, vec.zCoord);
	}

	public static Vector4f toJoml(Vector4f v) {
		return new Vector4f(v.x(), v.y(), v.z(), v.w());
	}
}

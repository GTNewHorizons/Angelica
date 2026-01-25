package net.coderbot.iris.shadows.frustum.advanced;

import org.joml.Matrix4f;
import org.joml.Vector4f;

public class BaseClippingPlanes {
	private final Vector4f[] planes = new Vector4f[6];
	private final Matrix4f transformScratch = new Matrix4f();

	public BaseClippingPlanes() {
		for (int i = 0; i < planes.length; i++) {
			planes[i] = new Vector4f();
		}
	}

	public void init(Matrix4f view, Matrix4f projection) {
		// Transform = Transpose(Projection x View)
		transformScratch.set(projection);
		transformScratch.mul(view);
		transformScratch.transpose();

		transform(transformScratch, -1, 0, 0, planes[0]);
		transform(transformScratch, 1, 0, 0, planes[1]);
		transform(transformScratch, 0, -1, 0, planes[2]);
		transform(transformScratch, 0, 1, 0, planes[3]);
		// FAR clipping plane
		transform(transformScratch, 0, 0, -1, planes[4]);
		// NEAR clipping plane
		transform(transformScratch, 0, 0, 1, planes[5]);
	}

	private static void transform(Matrix4f transform, float x, float y, float z, Vector4f dest) {
		dest.set(x, y, z, 1.0F);
		dest.mul(transform);
		dest.normalize();
	}

	public Vector4f[] getPlanes() {
		return planes;
	}
}

package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.compat.mojang.Axis;
import me.jellysquid.mods.sodium.client.model.quad.Quad;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static me.jellysquid.mods.sodium.client.util.MathUtil.fuzzy_eq;

/**
 * Static routines of general utility for renderer implementations.
 * Renderers are not required to use these helpers, but they were
 * designed to be usable without the default renderer.
 */
public abstract class GeometryHelper {

    /**
     * Returns true if quad is parallel to the given face.
     * Does not validate quad winding order.
     * Expects convex quads with all points co-planar.
     */
    public static boolean isQuadParallelToFace(ForgeDirection face, NdQuadBuilder quad) {
        int i = Axis.fromDirection(face).ordinal();
        final float val = quad.posByIndex(0, i);
        return fuzzy_eq(val, quad.posByIndex(1, i)) && fuzzy_eq(val, quad.posByIndex(2, i)) && fuzzy_eq(val, quad.posByIndex(3, i));
    }

    /**
     * Identifies the face to which the quad is most closely aligned.
     * This mimics the value that {@link Quad#getLightFace()} returns, and is
     * used in the vanilla renderer for all diffuse lighting.
     *
     * <p>Derived from the quad face normal and expects convex quads with all points co-planar.
     */
    public static ForgeDirection lightFace(NdQuadBuilder quad) {
        final Vector3f normal = quad.faceNormal;
        return coerceVector(normal);
    }

    /**
     * Returns the direction closest to the given vector.
     */
    private static ForgeDirection coerceVector(Vector3f v) {
        return switch (GeometryHelper.longestAxis(v)) {
            case X -> v.x() > 0 ? ForgeDirection.EAST : ForgeDirection.WEST;
            case Y -> v.y() > 0 ? ForgeDirection.UP : ForgeDirection.DOWN;
            case Z -> v.z() > 0 ? ForgeDirection.SOUTH : ForgeDirection.NORTH;
            default -> ForgeDirection.UP; // handle WTF case
        };
    }

	/**
	 * @see #longestAxis(float, float, float)
	 */
	public static Axis longestAxis(Vector3f vec) {
		return longestAxis(vec.x(), vec.y(), vec.z());
	}

	/**
	 * Identifies the largest (max absolute magnitude) component (X, Y, Z) in the given vector.
	 */
	public static Axis longestAxis(float normalX, float normalY, float normalZ) {
		Axis result = Axis.Y;
		float longest = Math.abs(normalY);
		float a = Math.abs(normalX);

		if (a > longest) {
			result = Axis.X;
			longest = a;
		}

		return Math.abs(normalZ) > longest
				? Axis.Z : result;
	}

    /**
     * Rotates the given direction by the matrix, and returns the closest direction to the output.
     */
    public static ForgeDirection rotate(ForgeDirection d, Matrix4f rotMat) {
        return rotate(d, rotMat, new Vector3f());
    }

    /**
     * See {@link #rotate(ForgeDirection, Matrix4f)}. This overload allows you to pass a scratch vector instead.
     */
    public static ForgeDirection rotate(ForgeDirection d, Matrix4f rotMat, Vector3f v) {
        return coerceVector(v.set(d.offsetX, d.offsetY, d.offsetZ).mulPosition(rotMat));
    }
}

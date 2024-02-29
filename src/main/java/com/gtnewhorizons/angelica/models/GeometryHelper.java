package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.compat.mojang.Axis;
import me.jellysquid.mods.sodium.client.model.quad.Quad;
import net.minecraftforge.common.util.ForgeDirection;
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
        return switch (GeometryHelper.longestAxis(normal)) {
            case X -> normal.x() > 0 ? ForgeDirection.EAST : ForgeDirection.WEST;
            case Y -> normal.y() > 0 ? ForgeDirection.UP : ForgeDirection.DOWN;
            case Z -> normal.z() > 0 ? ForgeDirection.SOUTH : ForgeDirection.NORTH;
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
}

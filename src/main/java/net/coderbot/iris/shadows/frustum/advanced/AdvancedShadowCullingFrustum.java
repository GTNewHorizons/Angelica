package net.coderbot.iris.shadows.frustum.advanced;

import net.coderbot.iris.shadows.frustum.BoxCuller;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.util.AxisAlignedBB;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Math;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * A Frustum implementation that derives a tightly-fitted shadow pass frustum based on the player's camera frustum and
 * an assumption that the shadow map will only be sampled for the purposes of direct shadow casting, volumetric lighting,
 * and similar effects, but notably not sun-bounce GI or similar effects.
 *
 * <p>The key idea of this algorithm is that if you are looking at the sun, something behind you cannot directly cast
 * a shadow on things visible to you. It's clear why this wouldn't work for sun-bounce GI, since with sun-bounce GI an
 * object behind you could cause light to bounce on to things visible to you.</p>
 *
 * <p>Derived from L. Spiro's clever algorithm & helpful diagrams described in a two-part blog tutorial:</p>
 *
 * <ul>
 * <li><a href="http://lspiroengine.com/?p=153">Tutorial: Tightly Culling Shadow Casters for Directional Lights (Part 1)</a></li>
 * <li><a href="http://lspiroengine.com/?p=187">Tutorial: Tightly Culling Shadow Casters for Directional Lights (Part 2)</a></li>
 * </ul>
 *
 * <p>Notable changes include switching out some of the sub-algorithms for computing the "extruded" edge planes to ones that
 * are not sensitive to the specific internal ordering of planes and corners, in order to avoid potential bugs at the
 * cost of slightly more computations.</p>
 */
public class AdvancedShadowCullingFrustum extends Frustrum implements ViewportProvider, Frustum {
	private static final int MAX_CLIPPING_PLANES = 13;

	/**
	 * We store each plane equation as a Vector4f.
	 *
	 * <p>We can represent a plane equation of the form <code>ax + by + cz = d</code> as a 4-dimensional vector
	 * of the form <code>(a, b, c, -d)</code>. In the special case of a plane that intersects the origin, we get
	 * the 4-dimensional vector <code>(a, b, c, 0)</code>. (a, b, c) is the normal vector of the plane, and d is the
	 * distance of the plane from the origin along that normal vector.</p>
	 *
	 * <p>Then, to test a given point (x, y, z) against the plane, we simply extend that point to a 4-component
	 * homogenous vector (x, y, z, 1), and then compute the dot product. Computing the dot product gives us
	 * ax + by + cz - d = 0, or, rearranged, our original plane equation of ax = by + cz = d.</p>
	 *
	 * <p>Note that, for the purposes of frustum culling, we usually aren't interested in computing whether a point
	 * lies exactly on a plane. Rather, we are interested in determining which side of the plane the point exists on
	 * - the side closer to the origin, or the side farther away from the origin. Fortunately, doing this with the
	 * dot product is still simple. If the dot product is negative, then the point lies closer to the origin than the
	 * plane, and if the dot product is positive, then the point lies further from the origin than the plane.</p>
	 *
	 * <p>In this case, if the point is closer to the origin than the plane, it is outside of the area enclosed by the
	 * plane, and if the point is farther from the origin than the plane, it is inside the area enclosed by the plane.</p>
	 *
	 * <p>So:
	 * <ul>
	 *     <li>dot(plane, point) > 0 implies the point is inside</li>
	 *     <li>dot(plane, point) < 0 implies that the point is outside</li>
	 * </ul>
	 * </p>
	 */
	private static final Vector3f scratch3a = new Vector3f();
	private static final Vector3f scratch3b = new Vector3f();
	private static final Vector3f scratch3c = new Vector3f();
	private static final Vector3f scratch3d = new Vector3f();
	private static final Vector3f scratch3e = new Vector3f();
	private static final Vector3f scratch3f = new Vector3f();

	private final Vector4f[] planes = new Vector4f[MAX_CLIPPING_PLANES];
	private int planeCount = 0;

	private final Vector3f shadowLightVectorFromOrigin = new Vector3f();
	private BoxCuller boxCuller;
	private final Vector3d position = new Vector3d();

	private final BaseClippingPlanes baseClippingPlanes = new BaseClippingPlanes();
	private final boolean[] isBackArray = new boolean[6];

	public AdvancedShadowCullingFrustum() {
		for (int i = 0; i < MAX_CLIPPING_PLANES; i++) {
			planes[i] = new Vector4f();
		}
	}

	public void init(Matrix4f playerView, Matrix4f playerProjection, Vector3f shadowLightVector, BoxCuller boxCuller) {
		this.shadowLightVectorFromOrigin.set(shadowLightVector);
		this.boxCuller = boxCuller;
		this.planeCount = 0;

		baseClippingPlanes.init(playerView, playerProjection);

		addBackPlanes(baseClippingPlanes, isBackArray);
		addEdgePlanes(baseClippingPlanes, isBackArray);
	}

	private void addPlane(Vector4f plane) {
		planes[planeCount].set(plane);
		planeCount += 1;
	}

	private void addPlane(float x, float y, float z, float w) {
		planes[planeCount].set(x, y, z, w);
		planeCount += 1;
	}

	/**
	 * Adds the back planes of the player's view frustum from the perspective of the shadow light.
	 * This can eliminate many chunks, especially if the player is staring at the shadow light
	 * (sun / moon).
	 */
	private void addBackPlanes(BaseClippingPlanes baseClippingPlanes, boolean[] isBack) {
		final Vector4f[] basePlanes = baseClippingPlanes.getPlanes();

		for (int planeIndex = 0; planeIndex < basePlanes.length; planeIndex++) {
			final Vector4f plane = basePlanes[planeIndex];
			truncate(plane, scratch3a);

			// Find back planes by looking for planes with a normal vector that points
			// in the same general direction as the vector pointing from the origin to the shadow light

			// That is, the angle between those two vectors is less than or equal to 90 degrees,
			// meaning that the dot product is positive or zero.

			final float dot = scratch3a.dot(shadowLightVectorFromOrigin);

			final boolean back = dot > 0.0;
			final boolean edge = dot == 0.0;

			// TODO: audit behavior when the dot product is zero
			isBack[planeIndex] = back;

			if (back || edge) {
				addPlane(plane);
			}
		}
	}

	private void addEdgePlanes(BaseClippingPlanes baseClippingPlanes, boolean[] isBack) {
		final Vector4f[] planes = baseClippingPlanes.getPlanes();

		for (int planeIndex = 0; planeIndex < planes.length; planeIndex++) {
			if (!isBack[planeIndex]) {
				continue;
			}

			final Vector4f plane = planes[planeIndex];
			final NeighboringPlaneSet neighbors = NeighboringPlaneSet.forPlane(planeIndex);

			if (!isBack[neighbors.getPlane0()]) {
				addEdgePlane(plane, planes[neighbors.getPlane0()]);
			}

			if (!isBack[neighbors.getPlane1()]) {
				addEdgePlane(plane, planes[neighbors.getPlane1()]);
			}

			if (!isBack[neighbors.getPlane2()]) {
				addEdgePlane(plane, planes[neighbors.getPlane2()]);
			}

			if (!isBack[neighbors.getPlane3()]) {
				addEdgePlane(plane, planes[neighbors.getPlane3()]);
			}
		}
	}

	private Vector3f truncate(Vector4f base, Vector3f dest) {
		return dest.set(base.x(), base.y(), base.z());
	}

	private float lengthSquared(Vector3f v) {
		final float x = v.x();
		final float y = v.y();
		final float z = v.z();

		return x * x + y * y + z * z;
	}

	private Vector3f cross(Vector3f first, Vector3f second, Vector3f dest) {
		return dest.set(first).cross(second);
	}

	private void addEdgePlane(Vector4f backPlane4, Vector4f frontPlane4) {
		final Vector3f backPlaneNormal = truncate(backPlane4, scratch3a);
		final Vector3f frontPlaneNormal = truncate(frontPlane4, scratch3b);

		// vector along the intersection of the two planes
		final Vector3f intersection = cross(backPlaneNormal, frontPlaneNormal, scratch3c);

		// compute edge plane normal, we want the normal vector of the edge plane
		// to always be perpendicular to the shadow light vector (since that's
		// what makes it an edge plane!)
		final Vector3f edgePlaneNormal = cross(intersection, shadowLightVectorFromOrigin, scratch3d);

		// At this point, we have a normal vector for our new edge plane, but we don't
		// have a value for distance (d). We can solve for it with a little algebra,
		// given that we want all 3 planes to intersect at a line.

		// Given the following system of equations:
		// a₁x + b₁y + c₁z = d₁
		// a₂x + b₂y + c₂z = d₂
		// a₃x + b₃y + c₃z = d₃
		//
		// Solve for -d₃, if a₁, b₁, c₁, -d₁, a₂, b₂, c₂, -d₂, a₃, b₃, and c₃ are all known, such that
		// the 3 planes formed by the corresponding 3 plane equations intersect at a line.

		// First, we need to pick a point along the intersection line between our planes.
		// Unfortunately, we don't have a complete line - only its vector.
		//
		// Fortunately, we can compute that point. If we create a plane passing through the origin
		// with a normal vector parallel to the intersection line, then the intersection
		// of all 3 planes will be a point on the line of intersection between the two planes we care about.
		final Vector3f point;
		{
			// "Line of intersection between two planes"
			// https://stackoverflow.com/a/32410473 by ideasman42, CC BY-SA 3.0
			// (a modified version of "Intersection of 2-planes" from Graphics Gems 1, page 305

			// NB: We can assume that the intersection vector has a non-zero length.
			final Vector3f ixb = cross(intersection, backPlaneNormal, scratch3e);
			final Vector3f fxi = cross(frontPlaneNormal, intersection, scratch3f);

			ixb.mul(-frontPlane4.w());
			fxi.mul(-backPlane4.w());

			ixb.add(fxi);

			point = ixb;
			point.mul(1.0F / lengthSquared(intersection));
		}

		// Now that we have a point and a normal vector, we can make a plane.
		// dot(normal, (x, y, z) - point) = 0
		// a(x - point.x) + b(y - point.y) + c(z - point.z) = 0
		// d = a * point.x + b * point.y + c * point.z = dot(normal, point)
		// w = -d

		final float d = edgePlaneNormal.dot(point);
		addPlane(edgePlaneNormal.x(), edgePlaneNormal.y(), edgePlaneNormal.z(), -d);
	}

	// Note: These functions are copied & modified from the vanilla Frustum class.
	@Override
	public void setPosition(double cameraX, double cameraY, double cameraZ) {
		super.setPosition(cameraX, cameraY, cameraZ);

		if (this.boxCuller != null) {
			boxCuller.setPosition(cameraX, cameraY, cameraZ);
		}
	}

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
		if (boxCuller != null && boxCuller.isCulled(aabb)) {
			return false;
		}

		return this.isVisible(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
	}

	// Embeddium Frustum interface - receives view-relative coords
	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		if (boxCuller != null && boxCuller.isCulledViewRelative(minX, minY, minZ, maxX, maxY, maxZ)) {
			return false;
		}

		return checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public Viewport sodium$createViewport() {
		return new Viewport(this, position.set(xPosition, yPosition, zPosition));
	}

	private boolean isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		return this.checkCornerVisibility((float)(minX - xPosition), (float)(minY - yPosition), (float)(minZ - zPosition),
				                          (float)(maxX - xPosition), (float)(maxY - yPosition), (float)(maxZ - zPosition));
	}

	// view-relative coordinates.
	private boolean checkCornerVisibility(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		for (int i = 0; i < planeCount; ++i) {
			final Vector4f plane = this.planes[i];

			final float outsideBoundX = plane.x() < 0 ? minX : maxX;
			final float outsideBoundY = plane.y() < 0 ? minY : maxY;
			final float outsideBoundZ = plane.z() < 0 ? minZ : maxZ;

			if (Math.fma(plane.x(), outsideBoundX, Math.fma(plane.y(), outsideBoundY, plane.z() * outsideBoundZ)) < -plane.w()) {
				return false;
			}
		}

		return true;
	}
}

package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.gtnewhorizons.angelica.stereo.StereoState;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.ONCE;
import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * @see <a href="https://github.com/IrisShaders/ShaderDoc/blob/master/uniforms.md#camera">Uniforms: Camera</a>
 */
public class CameraUniforms {
	private static final Minecraft client = Minecraft.getMinecraft();
	private static final Vector3f tempVec3f = new Vector3f();
	private static final Vector3i tempVec3i = new Vector3i();

	private CameraUniforms() {
	}

	public static void addCameraUniforms(UniformHolder uniforms, FrameUpdateNotifier notifier) {
		final CameraPositionTracker tracker = new CameraPositionTracker(notifier);

		uniforms
			.uniform1f(ONCE, "near", () -> 0.05)
			.uniform1f(PER_FRAME, "far", CameraUniforms::getRenderDistanceInBlocks)
			.uniform3d(PER_FRAME, "cameraPosition", tracker::getCurrentCameraPosition)
			.uniform1f(PER_FRAME, "eyeAltitude", tracker::getCurrentCameraPositionY)
			.uniform3d(PER_FRAME, "previousCameraPosition", tracker::getPreviousCameraPosition)
			.uniform3i(PER_FRAME, "cameraPositionInt", () -> getCameraPositionInt(getUnshiftedCameraPosition()))
			.uniform3f(PER_FRAME, "cameraPositionFract", () -> getCameraPositionFract(getUnshiftedCameraPosition()))
			.uniform3i(PER_FRAME, "previousCameraPositionInt", () -> getCameraPositionInt(tracker.getPreviousCameraPositionUnshifted()))
			.uniform3f(PER_FRAME, "previousCameraPositionFract", () -> getCameraPositionFract(tracker.getPreviousCameraPositionUnshifted()));
	}

	public static Vector3fc getCameraPositionFract(Vector3d originalPos) {
		return tempVec3f.set(
			(float) (originalPos.x - Math.floor(originalPos.x)),
			(float) (originalPos.y - Math.floor(originalPos.y)),
			(float) (originalPos.z - Math.floor(originalPos.z))
		);
	}

	public static Vector3ic getCameraPositionInt(Vector3d originalPos) {
		return tempVec3i.set(
			(int) Math.floor(originalPos.x),
			(int) Math.floor(originalPos.y),
			(int) Math.floor(originalPos.z)
		);
	}

	private static int getRenderDistanceInBlocks() {
		// TODO: Should we ask the game renderer for this?
		return client.gameSettings.renderDistanceChunks * 16;
	}

	public static Vector3d getUnshiftedCameraPosition() {
        return RenderingState.INSTANCE.getCameraPosition();
	}

	static class CameraPositionTracker {
		/**
		 * Value range of cameraPosition. We want this to be small enough that precision is maintained when we convert
		 * from a double to a float, but big enough that shifts happen infrequently, since each shift corresponds with
		 * a noticeable change in shader animations and similar. 1000024 is the number used by Optifine for walking (however this is too much, so we choose 30000),
		 * with an extra 1024 check for if the user has teleported between camera positions.
		 */
		private static final double WALK_RANGE = 30000;
		private static final double TP_RANGE = 1000;

		// Per-eye storage: LEFT/MONO = slot 0, RIGHT = slot 1. update() fires once per renderWorld
		// call (twice per frame in stereo). Without per-eye slots, the RIGHT eye's update would
		// overwrite previousCameraPosition with LEFT eye's current-frame position, so RIGHT eye
		// shaders see zero motion between previous and current — temporal effects desync per eye.
		private final Vector3d[] previousCameraPositionPerEye = new Vector3d[] { new Vector3d(), new Vector3d() };
		private final Vector3d[] currentCameraPositionPerEye  = new Vector3d[] { new Vector3d(), new Vector3d() };
		private final Vector3d shift = new Vector3d();
		private final Vector3d[] previousCameraPositionUnshiftedPerEye = new Vector3d[] { new Vector3d(), new Vector3d() };
		private final Vector3d[] currentCameraPositionUnshiftedPerEye  = new Vector3d[] { new Vector3d(), new Vector3d() };

		CameraPositionTracker(FrameUpdateNotifier notifier) {
			notifier.addListener(this::update);
		}

		public Vector3d getPreviousCameraPosition() {
			return previousCameraPositionPerEye[StereoState.INSTANCE.currentEyeIndex()];
		}

		public Vector3d getCurrentCameraPosition() {
			return currentCameraPositionPerEye[StereoState.INSTANCE.currentEyeIndex()];
		}

		private void update() {
			final int eye = StereoState.INSTANCE.currentEyeIndex();
			previousCameraPositionPerEye[eye].set(currentCameraPositionPerEye[eye]);
			previousCameraPositionUnshiftedPerEye[eye].set(currentCameraPositionUnshiftedPerEye[eye]);
			currentCameraPositionPerEye[eye].set(getUnshiftedCameraPosition()).add(shift);
			currentCameraPositionUnshiftedPerEye[eye].set(getUnshiftedCameraPosition());

			updateShift();
		}

		/**
		 * Updates our shift values to try to keep |x| < 30000 and |z| < 30000, to maintain precision with cameraPosition.
		 * Since our actual range is 60000x60000, this means that we won't excessively move back and forth when moving
		 * around a chunk border.
		 */
		private void updateShift() {
			final int eye = StereoState.INSTANCE.currentEyeIndex();
			final Vector3d cur = currentCameraPositionPerEye[eye];
			final Vector3d prev = previousCameraPositionPerEye[eye];
			final double dX = getShift(cur.x, prev.x);
			final double dZ = getShift(cur.z, prev.z);

			if (dX != 0.0 || dZ != 0.0) {
				applyShift(dX, dZ);
			}
		}

		private static double getShift(double value, double prevValue) {
			if (Math.abs(value) > WALK_RANGE || Math.abs(value - prevValue) > TP_RANGE) {
				// Only shift by increments of WALK_RANGE - this is required for some packs (like SEUS PTGI) to work properly
				return -(value - (value % WALK_RANGE));
			} else {
				return 0.0;
			}
		}

		/**
		 * Shifts all current and future positions by the given amount. The shift is global (applied
		 * to both eye slots) so per-eye motion remains consistent across the precision-wraparound.
		 */
		private void applyShift(double dX, double dZ) {
			shift.x += dX;
			shift.z += dZ;
			for (int e = 0; e < currentCameraPositionPerEye.length; e++) {
				currentCameraPositionPerEye[e].x  += dX;
				previousCameraPositionPerEye[e].x += dX;
				currentCameraPositionPerEye[e].z  += dZ;
				previousCameraPositionPerEye[e].z += dZ;
			}
		}

		public double getCurrentCameraPositionY() {
			return currentCameraPositionPerEye[StereoState.INSTANCE.currentEyeIndex()].y;
		}

		public Vector3d getPreviousCameraPositionUnshifted() {
			return previousCameraPositionUnshiftedPerEye[StereoState.INSTANCE.currentEyeIndex()];
		}
	}
}

package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.ONCE;
import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * @see <a href="https://github.com/IrisShaders/ShaderDoc/blob/master/uniforms.md#camera">Uniforms: Camera</a>
 */
public class CameraUniforms {
	private static final Minecraft client = Minecraft.getMinecraft();

	private CameraUniforms() {
	}

	public static void addCameraUniforms(UniformHolder uniforms, FrameUpdateNotifier notifier) {
		final CameraPositionTracker tracker = new CameraPositionTracker(notifier);

		uniforms
            .uniform1f(ONCE, "near", () -> 0.05f)
            .uniform1f(PER_FRAME, "far", CameraUniforms::getRenderDistanceInBlocks)
            .uniform3d(PER_FRAME, "cameraPosition", tracker::getCurrentCameraPosition)
            .uniform3d(PER_FRAME, "previousCameraPosition", tracker::getPreviousCameraPosition)
            .uniform1f(PER_FRAME, "eyeAltitude", tracker::getCurrentCameraPositionY)
            // Optional but useful for motion blur or velocity-based shaders
            .uniform1f(PER_FRAME, "difX", () -> (float) (tracker.getCurrentCameraPosition().x - tracker.getPreviousCameraPosition().x))
            .uniform1f(PER_FRAME, "difY", () -> (float) (tracker.getCurrentCameraPosition().y - tracker.getPreviousCameraPosition().y))
            .uniform1f(PER_FRAME, "difZ", () -> (float) (tracker.getCurrentCameraPosition().z - tracker.getPreviousCameraPosition().z));
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
        private static final double WALK_RANGE = 30000; // Maximum range for walking
        private static final double TP_RANGE = 1000; // Maximum range for teleportation

        private final Vector3d previousCameraPosition = new Vector3d();
        private final Vector3d currentCameraPosition = new Vector3d();
        private final Vector3d shift = new Vector3d();


		CameraPositionTracker(FrameUpdateNotifier notifier) {
			notifier.addListener(this::update);
		}

        /**
         * Called once per frame. Updates the camera position and adjusts for large world coordinates if needed.
         */
        private void update() {
            previousCameraPosition.set(currentCameraPosition);
            currentCameraPosition.set(getUnshiftedCameraPosition()).add(shift);

            updateShift();
        }

        /**
         * Updates the shift if the camera's position has moved too far.
         */
        private void updateShift() {
            double dX = getShift(currentCameraPosition.x, previousCameraPosition.x);
            double dZ = getShift(currentCameraPosition.z, previousCameraPosition.z);

            if (dX != 0.0 || dZ != 0.0) {
                applyShift(dX, dZ);
            }
        }

        /**
         * Determines how much to shift axis.
         * - If the camera moves beyond ±WALK_RANGE Teleportation > TP_RANGE, shift is applied.
         */
        private static double getShift(double value, double prevValue) {
            if (Math.abs(value) > WALK_RANGE || Math.abs(value - prevValue) > TP_RANGE) {
                return -(value - (value % WALK_RANGE)); // Shift back to avoid precision issues
            } else {
                return 0.0;
            }
        }

        /**
         * Applies the calculated shift to the current and previous camera positions.
         */
        private void applyShift(double dX, double dZ) {
            shift.x += dX;
            shift.z += dZ;

            currentCameraPosition.x += dX;
            previousCameraPosition.x += dX;

            currentCameraPosition.z += dZ;
            previousCameraPosition.z += dZ;
        }

        /**
         * Returns the current camera's Y position.
         */
        public double getCurrentCameraPositionY() {
            return currentCameraPosition.y;
        }

        /**
         * Returns a defensive copy of the previous camera position to prevent external modification.
         */
        public Vector3d getPreviousCameraPosition() {
            return new Vector3d(previousCameraPosition);
        }

        /**
         * Returns a defensive copy of the current camera position to prevent external modification.
         */
        public Vector3d getCurrentCameraPosition() {
            return new Vector3d(currentCameraPosition);
        }
    }
}

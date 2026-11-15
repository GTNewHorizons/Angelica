package net.coderbot.iris.uniforms;

import org.joml.Matrix3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3ic;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Lets a draw's own model space stand in for the world's block grid, for geometry drawn rotated or off the grid
 * (structures, previews).
 * <p>
 * Packs pick out parts of some blocks by where a pixel is inside its world block, {@code fract(playerPos +
 * cameraPosition)}, e.g. the water in a cauldron or the eye of an end portal frame. Off the grid that cuts through the
 * block in the wrong place. While this is active the uniforms describe a world where the draw's model coordinates are
 * world coordinates, moved by whole blocks to stay near the real position. View space, clip space and the shadow map
 * come out exactly the same, so only what a pack works out in world space changes.
 * <p>
 * Every uniform that changes is in the remap tables below, keyed by name. The custom uniform input holder wraps those
 * suppliers when it's built (see the wrap methods), so the uniform classes themselves don't know about any of this. A
 * new world space uniform only needs a line here. The uniforms are worked out once a frame, so starting or ending this
 * works them out again and pushes them to the bound program, see {@link #UNIFORMS}.
 */
public final class LocalGridFrame {

    private static final Map<String, UnaryOperator<Vector3d>> VEC3D = new HashMap<>();
    private static final Map<String, UnaryOperator<Vector3fc>> VEC3F = new HashMap<>();
    private static final Map<String, UnaryOperator<Vector3ic>> VEC3I = new HashMap<>();
    private static final Map<String, UnaryOperator<Vector4fc>> VEC4F = new HashMap<>();
    private static final Map<String, UnaryOperator<Matrix4fc>> MATRIX = new HashMap<>();
    private static final Map<String, DoubleUnaryOperator> DOUBLE = new HashMap<>();

    /** Every uniform that changes under the frame. */
    public static final String[] UNIFORMS;

    private static boolean active;
    private static CameraUniforms.CameraPositionTracker tracker;

    // Model to player space is T = V^-1 * M, linear part L and translation t. Everything below comes from that
    private static final Matrix4f postfix = new Matrix4f();
    private static final Matrix4f postfixInverse = new Matrix4f();
    private static final Matrix3d linearInverse = new Matrix3d();
    private static final Vector3d eye = new Vector3d();
    private static final Vector3d modelCamera = new Vector3d();
    private static final Vector3d modelOrigin = new Vector3d();
    private static final Vector4f up = new Vector4f();

    // What the remaps hand back. Like the suppliers they wrap (CameraUniforms and MatrixUniforms keep scratch objects
    // too), a value is only good until the next remap runs. The cached uniforms in the input holder all copy it
    // straight away, anything new that takes a wrapped supplier has to do the same
    private static final Matrix4f scratch = new Matrix4f();
    private static final Vector3d current = new Vector3d();
    private static final Vector3d previous = new Vector3d();
    private static final Vector3d point = new Vector3d();
    private static final Vector3d offset = new Vector3d();
    private static final Vector3d direction = new Vector3d();
    private static final Vector3f offset3f = new Vector3f();
    private static final Vector4f offset4f = new Vector4f();

    static {
        // World positions. The shifted ones keep Iris's shift, the unshifted ones read the real camera directly
        VEC3D.put("cameraPosition", LocalGridFrame::camera);
        DOUBLE.put("eyeAltitude", y -> Math.rint(y + modelOrigin.y) + modelCamera.y);
        VEC3D.put("previousCameraPosition", real -> tracker == null ? real
            : previousCamera(tracker.getCurrentCameraPosition(), real));
        VEC3I.put("cameraPositionInt", real -> CameraUniforms.getCameraPositionInt(unshiftedCamera()));
        VEC3F.put("cameraPositionFract", real -> CameraUniforms.getCameraPositionFract(unshiftedCamera()));
        VEC3I.put("previousCameraPositionInt", real -> tracker == null ? real
            : CameraUniforms.getCameraPositionInt(unshiftedPreviousCamera()));
        VEC3F.put("previousCameraPositionFract", real -> tracker == null ? real
            : CameraUniforms.getCameraPositionFract(unshiftedPreviousCamera()));
        VEC3D.put("eyePosition", real -> worldPosition(CameraUniforms.getUnshiftedCameraPosition(), real));

        // Offsets from the unshifted camera
        VEC3D.put("relativeEyePosition", LocalGridFrame::offset);
        VEC3D.put("relativeVehiclePosition", LocalGridFrame::offset);
        // -256 on every axis means nothing is selected
        VEC3F.put("currentSelectedBlockPos", real -> real.x() == -256.0f && real.y() == -256.0f && real.z() == -256.0f
            ? real : offset3f.set(offset(point.set(real.x(), real.y(), real.z()))));
        // w is 0 when there's no bolt
        VEC4F.put("lightningBoltPosition", real -> {
            if (real.w() == 0.0f) return real;
            final Vector3d moved = offset(point.set(real.x(), real.y(), real.z()));
            return offset4f.set((float) moved.x, (float) moved.y, (float) moved.z, real.w());
        });

        // Directions in the world
        VEC3D.put("playerLookVector", LocalGridFrame::direction);
        VEC3D.put("playerBodyVector", LocalGridFrame::direction);
        VEC3D.put("vehicleLookVector", LocalGridFrame::direction);

        // View space, so only up changes
        VEC4F.put("upPosition", real -> up);

        // Matrices that take world space positions relative to the camera
        MATRIX.put("gbufferModelView", LocalGridFrame::modelView);
        MATRIX.put("gbufferPreviousModelView", LocalGridFrame::modelView);
        MATRIX.put("shadowModelView", LocalGridFrame::modelView);
        MATRIX.put("gbufferModelViewInverse", LocalGridFrame::modelViewInverse);
        MATRIX.put("shadowModelViewInverse", LocalGridFrame::modelViewInverse);

        UNIFORMS = Stream.of(VEC3D, VEC3F, VEC3I, VEC4F, MATRIX, DOUBLE).flatMap(m -> m.keySet().stream())
            .toArray(String[]::new);
    }

    private LocalGridFrame() {}

    public static boolean isActive() {
        return active;
    }

    /** The pipeline's camera tracker, for the uniforms that need the camera rather than their own value. */
    static void trackCamera(CameraUniforms.CameraPositionTracker cameraTracker) {
        tracker = cameraTracker;
    }

    /**
     * @param drawModelView the modelview the geometry is drawn with
     * @return false when it can't be done (a modelview that can't be inverted)
     */
    public static boolean begin(Matrix4fc drawModelView) {
        final Matrix4f view = new Matrix4f(MatrixUniforms.getGbufferModelView());
        final Matrix4f viewInverse = new Matrix4f(view).invert();
        final Matrix4f model = new Matrix4f(viewInverse).mul(drawModelView);
        if (!Float.isFinite(model.determinant()) || Math.abs(model.determinant3x3()) < 1e-8f) return false;

        // The camera's eye in player space, and in model space
        eye.set(viewInverse.m30(), viewInverse.m31(), viewInverse.m32());
        final Matrix3d linear = new Matrix3d(model.m00(), model.m01(), model.m02(), model.m10(), model.m11(),
            model.m12(), model.m20(), model.m21(), model.m22());
        linearInverse.set(linear).invert();
        modelOrigin.set(model.m30(), model.m31(), model.m32());
        modelCamera.set(eye).sub(modelOrigin);
        linearInverse.transform(modelCamera);

        // gbufferModelView' = V * translate(eye) * L, so V' * (p - camera) lands where M * p does
        postfix.identity().translate((float) eye.x, (float) eye.y, (float) eye.z)
            .mul(new Matrix4f().set3x3(model));
        postfix.invert(postfixInverse);

        // Model space up in view space, 100 long like the real upPosition
        final Vector3f modelUp = model.transformDirection(new Vector3f(0, 1, 0)).normalize(100);
        view.transformDirection(modelUp);
        up.set(modelUp, 0);

        active = true;
        return true;
    }

    public static void end() {
        active = false;
    }

    // The wrap methods are for the custom uniform input holder, each hands back the supplier with the remap applied
    // while the frame is active, or the same supplier if that uniform doesn't change
    public static Supplier<Vector3d> wrap3d(String name, Supplier<Vector3d> real) {
        return wrap(VEC3D.get(name), real);
    }

    public static Supplier<Vector3fc> wrap3f(String name, Supplier<Vector3fc> real) {
        return wrap(VEC3F.get(name), real);
    }

    public static Supplier<Vector3ic> wrap3i(String name, Supplier<Vector3ic> real) {
        return wrap(VEC3I.get(name), real);
    }

    public static Supplier<Vector4fc> wrap4f(String name, Supplier<Vector4fc> real) {
        return wrap(VEC4F.get(name), real);
    }

    public static Supplier<Matrix4fc> wrapMatrix(String name, Supplier<Matrix4fc> real) {
        return wrap(MATRIX.get(name), real);
    }

    public static DoubleSupplier wrapDouble(String name, DoubleSupplier real) {
        final DoubleUnaryOperator remap = DOUBLE.get(name);
        if (remap == null) return real;
        return () -> active ? remap.applyAsDouble(real.getAsDouble()) : real.getAsDouble();
    }

    private static <T> Supplier<T> wrap(UnaryOperator<T> remap, Supplier<T> real) {
        if (remap == null) return real;
        return () -> active ? remap.apply(real.get()) : real.get();
    }

    /** Where the model origin really is, rounded to whole blocks, so the frame's world stays near the real one. */
    private static Vector3d shift(Vector3dc realCamera, Vector3d dest) {
        return dest.set(
            Math.rint(realCamera.x() + modelOrigin.x),
            Math.rint(realCamera.y() + modelOrigin.y),
            Math.rint(realCamera.z() + modelOrigin.z));
    }

    /** cameraPosition (or its unshifted twin) for a real one. */
    private static Vector3d camera(Vector3d realCamera) {
        return shift(realCamera, current).add(modelCamera);
    }

    private static Vector3d unshiftedCamera() {
        return camera(CameraUniforms.getUnshiftedCameraPosition());
    }

    private static Vector3d unshiftedPreviousCamera() {
        final Vector3d realPrevious = tracker.getPreviousCameraPositionUnshifted();
        return previousCamera(CameraUniforms.getUnshiftedCameraPosition(), realPrevious);
    }

    /** previousCameraPosition, from the camera's last step turned into model space. */
    private static Vector3d previousCamera(Vector3d realCamera, Vector3d realPrevious) {
        previous.set(realCamera).sub(realPrevious);
        linearInverse.transform(previous);
        return previous.negate().add(camera(realCamera));
    }

    /** A real world position (eyePosition) moved into the frame's world, against the unshifted camera. */
    private static Vector3d worldPosition(Vector3dc realCamera, Vector3dc realPosition) {
        point.set(realPosition).sub(realCamera).sub(modelOrigin);
        linearInverse.transform(point);
        return shift(realCamera, current).add(point);
    }

    /**
     * An offset from the unshifted camera (relativeEyePosition and co), as the same offset in the frame's world. Works
     * out to {@link #worldPosition} minus the unshifted cameraPosition.
     */
    private static Vector3d offset(Vector3d realOffset) {
        return linearInverse.transform(offset.set(realOffset).sub(eye));
    }

    /** A world space direction turned into model space, same length. */
    private static Vector3d direction(Vector3d real) {
        final double length = real.length();
        if (length == 0) return real;
        return linearInverse.transform(direction.set(real)).normalize(length);
    }

    /** gbufferModelView, gbufferPreviousModelView or shadowModelView for a real one. */
    private static Matrix4fc modelView(Matrix4fc real) {
        return scratch.set(real).mul(postfix);
    }

    /** Their inverses, (R * P)^-1 = P^-1 * R^-1 for the real inverse. */
    private static Matrix4fc modelViewInverse(Matrix4fc realInverse) {
        return scratch.set(postfixInverse).mul(realInverse);
    }
}

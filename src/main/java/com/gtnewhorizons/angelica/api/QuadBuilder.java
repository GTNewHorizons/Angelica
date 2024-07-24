package com.gtnewhorizons.angelica.api;

import com.gtnewhorizons.angelica.models.NdQuadBuilder;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadViewMutable;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public interface QuadBuilder {

    /**
     * Causes texture to appear with no rotation.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    int BAKE_ROTATE_NONE = 0;

    /**
     * Causes texture to appear rotated 90 deg. clockwise relative to nominal face.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    int BAKE_ROTATE_90 = 1;

    /**
     * Causes texture to appear rotated 180 deg. relative to nominal face.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    int BAKE_ROTATE_180 = 2;

    /**
     * Causes texture to appear rotated 270 deg. clockwise relative to nominal face.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    int BAKE_ROTATE_270 = 3;

    /**
     * When enabled, texture coordinate are assigned based on vertex position.
     * Any existing UV coordinates will be replaced.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     *
     * <p>UV lock always derives texture coordinates based on nominal face, even
     * when the quad is not co-planar with that face, and the result is
     * the same as if the quad were projected onto the nominal face, which
     * is usually the desired result.
     */
    int BAKE_LOCK_UV = 4;

    /**
     * When set, U texture coordinates for the given sprite are
     * flipped as part of baking. Can be useful for some randomization
     * and texture mapping scenarios. Results are different from what
     * can be obtained via rotation and both can be applied.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    int BAKE_FLIP_U = 8;

    /**
     * Same as {@link #BAKE_FLIP_U} but for V coordinate.
     */
    int BAKE_FLIP_V = 16;

    /**
     * UV coordinates by default are assumed to be 0-16 scale for consistency
     * with conventional Minecraft model format. This is scaled to 0-1 during
     * baking before interpolation. Model loaders that already have 0-1 coordinates
     * can avoid wasteful multiplication/division by passing 0-1 coordinates directly.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    int BAKE_NORMALIZED = 32;

    /**
     * Tolerance for determining if the depth parameter to {@link #square(ForgeDirection, float, float, float, float, float)}
     * is effectively zero - meaning the face is a cull face.
     */
    float CULL_FACE_EPSILON = 0.00001f;

    static QuadBuilder getBuilder() {
        return new NdQuadBuilder();
    }

    /**
     * Copies data to out, returns it, and resets builder for further use.
     */
    QuadView build(QuadView out);

    /**
     * See {@link #build(QuadView)}. This also rotates the output by the given matrix.
     */
    QuadView build(QuadView out, Matrix4f rotMat);

    /**
     * Tries to automatically set the culling and nominal face from the quad's geometry.
     */
    void setCullFace();

    /**
     * Provides a hint to renderer about the facing of this quad. Not required,
     * but if provided can shortcut some geometric analysis if the quad is parallel to a block face.
     * Should be the expected value of {@link ModelQuadView#getLightFace()}. Value will be confirmed
     * and if invalid the correct light face will be calculated.
     *
     * <p>Null by default, and set automatically by {@link ModelQuadViewMutable#setCullFace(ForgeDirection)}.
     *
     * <p>Models may also find this useful as the face for texture UV locking and rotation semantics.
     *
     * <p>Note: This value is not persisted independently when the quad is encoded.
     * When reading encoded quads, this value will always be the same as {@link ModelQuadView#getLightFace()}.
     */
    void nominalFace(@Nullable ForgeDirection face);

    /**
     * See {@link #nominalFace(ForgeDirection)}
     */
    ForgeDirection nominalFace();

    /**
     * Sets the geometric vertex position for the given vertex,
     * relative to block origin, (0,0,0). Minecraft rendering is designed
     * for models that fit within a single block space and is recommended
     * that coordinates remain in the 0-1 range, with multi-block meshes
     * split into multiple per-block models.
     */
    void pos(int vertexIndex, float x, float y, float z);

    /**
     * Convenience: set pos with a vector. See {@link #pos(int, float, float, float)}.
     */
    void pos(int vertexIndex, Vector3f vec);

    /**
     * Gets the vertex position as a vector. This allocates a new vector, do not use in dynamic rendering!
     */
    Vector3f pos(int vertexIndex);

    /**
     * Convenience: access x, y, z by index 0-2.
     */
    float posByIndex(int vertexIndex, int coordinateIndex);

    /**
     * Convienence, gets the icon from the block atlas. See {@link #spriteBake(IIcon, int)}
     */
    void spriteBake(String spriteName, int bakeFlags);

    /**
     * Assigns sprite atlas u,v coordinates to this quad for the given sprite.
     * Can handle UV locking, rotation, interpolation, etc. Control this behavior
     * by passing additive combinations of the BAKE_ flags defined in this interface.
     */
    void spriteBake(IIcon sprite, int bakeFlags);

    /**
     * Helper method to assign vertex coordinates for a square aligned with the given face.
     * Ensures that vertex order is consistent with vanilla convention. (Incorrect order can
     * lead to bad AO lighting unless enhanced lighting logic is available/enabled.)
     *
     * <p>Square will be parallel to the given face and coplanar with the face (and culled if the
     * face is occluded) if the depth parameter is approximately zero. See {@link #CULL_FACE_EPSILON}.
     *
     * <p>All coordinates should be normalized (0-1).
     *
     * <p>The directions for the faces assume you're looking at them. For top and bottom, they assume you're looking north.
     */
    void square(ForgeDirection nominalFace, float left, float bottom, float right, float top, float depth);

    int tag();

    /**
     * Encodes an integer tag with this quad that can later be retrieved via
     * {@link #tag()}.  Useful for models that want to perform conditional
     * transformation or filtering on static meshes.
     */
    void tag(int tag);

    /**
     * Set texture coordinates.
     */
    void uv(int vertexIndex, float u, float v);

    /**
     * Modern Minecraft uses magic arrays to do this without breaking AO. This is the same thing, but without arrays.
     */
    static Vector3f mapSideToVertex(Vector3f from, Vector3f to, int index, ForgeDirection side) {

        return switch (side) {
            case DOWN -> switch (index) {
                case 0 -> new Vector3f(from.x, from.y, to.z);
                case 1 -> new Vector3f(from.x, from.y, from.z);
                case 2 -> new Vector3f(to.x, from.y, from.z);
                case 3 -> new Vector3f(to.x, from.y, to.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case UP -> switch (index) {
                case 0 -> new Vector3f(from.x, to.y, from.z);
                case 1 -> new Vector3f(from.x, to.y, to.z);
                case 2 -> new Vector3f(to.x, to.y, to.z);
                case 3 -> new Vector3f(to.x, to.y, from.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case NORTH -> switch (index) {
                case 0 -> new Vector3f(to.x, to.y, from.z);
                case 1 -> new Vector3f(to.x, from.y, from.z);
                case 2 -> new Vector3f(from.x, from.y, from.z);
                case 3 -> new Vector3f(from.x, to.y, from.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case SOUTH -> switch (index) {
                case 0 -> new Vector3f(from.x, to.y, to.z);
                case 1 -> new Vector3f(from.x, from.y, to.z);
                case 2 -> new Vector3f(to.x, from.y, to.z);
                case 3 -> new Vector3f(to.x, to.y, to.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case WEST -> switch (index) {
                case 0 -> new Vector3f(from.x, to.y, from.z);
                case 1 -> new Vector3f(from.x, from.y, from.z);
                case 2 -> new Vector3f(from.x, from.y, to.z);
                case 3 -> new Vector3f(from.x, to.y, to.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case EAST -> switch (index) {
                case 0 -> new Vector3f(to.x, to.y, to.z);
                case 1 -> new Vector3f(to.x, from.y, to.z);
                case 2 -> new Vector3f(to.x, from.y, from.z);
                case 3 -> new Vector3f(to.x, to.y, from.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case UNKNOWN -> null;
        };
    }
}

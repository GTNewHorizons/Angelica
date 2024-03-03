package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.api.QuadView;
import lombok.Setter;
import me.jellysquid.mods.sodium.client.model.quad.Quad;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

public class NdQuadBuilder extends Quad {

    /**
     * Causes texture to appear with no rotation.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_ROTATE_NONE = 0;

    /**
     * Causes texture to appear rotated 90 deg. clockwise relative to nominal face.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_ROTATE_90 = 1;

    /**
     * Causes texture to appear rotated 180 deg. relative to nominal face.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_ROTATE_180 = 2;

    /**
     * Causes texture to appear rotated 270 deg. clockwise relative to nominal face.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_ROTATE_270 = 3;

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
    public static final int BAKE_LOCK_UV = 4;

    /**
     * When set, U texture coordinates for the given sprite are
     * flipped as part of baking. Can be useful for some randomization
     * and texture mapping scenarios. Results are different from what
     * can be obtained via rotation and both can be applied.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_FLIP_U = 8;

    /**
     * Same as {@link #BAKE_FLIP_U} but for V coordinate.
     */
    public static final int BAKE_FLIP_V = 16;

    /**
     * UV coordinates by default are assumed to be 0-16 scale for consistency
     * with conventional Minecraft model format. This is scaled to 0-1 during
     * baking before interpolation. Model loaders that already have 0-1 coordinates
     * can avoid wasteful multiplication/division by passing 0-1 coordinates directly.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_NORMALIZED = 32;

    /**
     * Tolerance for determining if the depth parameter to {@link #square(ForgeDirection, float, float, float, float, float)}
     * is effectively zero - meaning the face is a cull face.
     */
    public static float CULL_FACE_EPSILON = 0.00001f;

    private ForgeDirection nominalFace = ForgeDirection.UNKNOWN;
    // Defaults to UP, but only because it can't be UNKNOWN or null
    private ForgeDirection lightFace = ForgeDirection.UP;
    private int geometryFlags = 0;
    private boolean isGeometryInvalid = true;
    private int tag = 0;
    final Vector3f faceNormal = new Vector3f();
    public final Material mat = new Material();
    @Setter
    private int drawMode = GL11.GL_QUADS;

    /**
     * Dumps to {@param out} and returns it.
     */
    public QuadView build(QuadView out) {

        if (this.drawMode != GL11.GL_QUADS)
            this.quadrangulate();

        // FRAPI does this late, but we need to do it before baking to Nd quads
        this.computeGeometry();
        out.copyFrom(this);
        this.clear();
        return out;
    }

    /**
     * See {@link #build(QuadView)}. This rotates the output by the given matrix.
     */
    public QuadView build(QuadView out, Matrix4f rotMat) {

        this.pos(0, this.pos(0).mulPosition(rotMat));
        this.pos(1, this.pos(1).mulPosition(rotMat));
        this.pos(2, this.pos(2).mulPosition(rotMat));

        if (this.drawMode == GL11.GL_QUADS)
            this.pos(3, this.pos(3).mulPosition(rotMat));
        else
            this.quadrangulate();

        this.computeGeometry();

        // Reset the cull face
        this.setCullFace();

        out.copyFrom(this);
        this.clear();
        return out;
    }

    public void clear() {

        Arrays.fill(this.data, 0);
        this.setCullFace(ForgeDirection.UNKNOWN);
        this.lightFace = ForgeDirection.UP;
        this.geometryFlags = 0;
        this.isGeometryInvalid = true;
        this.tag(0);
        this.setColorIndex(-1);
        this.mat.reset();
        this.drawMode = GL11.GL_QUADS;
    }

    private void computeGeometry() {
        if (this.isGeometryInvalid) {
            this.isGeometryInvalid = false;

            NormalHelper.computeFaceNormal(this.faceNormal, this);

            // depends on face normal
            this.lightFace = GeometryHelper.lightFace(this);

            // depends on light face
            this.geometryFlags = ModelQuadFlags.getQuadFlags(this);
        }
    }

    @Override
    public boolean isShade() {
        return this.mat.getDiffuse();
    }

    @Override
    @NotNull
    public ForgeDirection getLightFace() {
        this.computeGeometry();
        return this.lightFace;
    }

    @Override
    public void setCullFace(ForgeDirection dir) {
        super.setCullFace(dir);
        this.nominalFace(dir);
    }

    /**
     * Tries to automatically set the culling and nominal face from the computed geometry.
     */
    public void setCullFace() {
        this.computeGeometry();
        if ((this.geometryFlags & ModelQuadFlags.IS_ALIGNED) != 0)
            this.setCullFace(this.lightFace);
        else {
            this.setCullFace(ForgeDirection.UNKNOWN);
            this.nominalFace(this.lightFace);
        }
    }

    /**
     * Provides a hint to renderer about the facing of this quad. Not required,
     * but if provided can shortcut some geometric analysis if the quad is parallel to a block face.
     * Should be the expected value of {@link #getLightFace()}. Value will be confirmed
     * and if invalid the correct light face will be calculated.
     *
     * <p>Null by default, and set automatically by {@link #setCullFace(ForgeDirection)}.
     *
     * <p>Models may also find this useful as the face for texture UV locking and rotation semantics.
     *
     * <p>Note: This value is not persisted independently when the quad is encoded.
     * When reading encoded quads, this value will always be the same as {@link #getLightFace()}.
     */
    public void nominalFace(@Nullable ForgeDirection face) {
        this.nominalFace = face;
    }

    /**
     * See {@link #nominalFace(ForgeDirection)}
     */
    public ForgeDirection nominalFace() {
        return this.nominalFace;
    }

    /**
     * Sets the geometric vertex position for the given vertex,
     * relative to block origin, (0,0,0). Minecraft rendering is designed
     * for models that fit within a single block space and is recommended
     * that coordinates remain in the 0-1 range, with multi-block meshes
     * split into multiple per-block models.
     */
    public void pos(int vertexIndex, float x, float y, float z) {

        this.setX(vertexIndex, x);
        this.setY(vertexIndex, y);
        this.setZ(vertexIndex, z);
        isGeometryInvalid = true;
    }

    /**
     * Convenience: set pos with a vector. See {@link #pos(int, float, float, float)}.
     */
    public void pos(int vertexIndex, Vector3f vec) {

        this.setX(vertexIndex, vec.x);
        this.setY(vertexIndex, vec.y);
        this.setZ(vertexIndex, vec.z);
        isGeometryInvalid = true;
    }

    /**
     * Gets the vertex position as a vector. This allocates a new vector, do not use in dynamic rendering!
     */
    public Vector3f pos(int vertexIndex) {

        return new Vector3f(
            this.getX(vertexIndex),
            this.getY(vertexIndex),
            this.getZ(vertexIndex)
        );
    }

    /**
     * Convenience: access x, y, z by index 0-2.
     */
    float posByIndex(int vertexIndex, int coordinateIndex) {
        return Float.intBitsToFloat(this.data[vertexIndex * Quad.INTS_PER_VERTEX + Quad.X_INDEX + coordinateIndex]);
    }

    /**
     * Convienence, gets the icon from the block atlas. See {@link #spriteBake(IIcon, int)}
     */
    public void spriteBake(String spriteName, int bakeFlags) {

        final IIcon icon = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(spriteName);
        TexHelper.bakeSprite(this, icon, bakeFlags);
    }

    /**
     * Assigns sprite atlas u,v coordinates to this quad for the given sprite.
     * Can handle UV locking, rotation, interpolation, etc. Control this behavior
     * by passing additive combinations of the BAKE_ flags defined in this interface.
     */
    public void spriteBake(IIcon sprite, int bakeFlags) {
        TexHelper.bakeSprite(this, sprite, bakeFlags);
    }

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
    public void square(ForgeDirection nominalFace, float left, float bottom, float right, float top, float depth) {
        if (Math.abs(depth) < CULL_FACE_EPSILON) {
            setCullFace(nominalFace);
            depth = 0; // avoid any inconsistency for face quads
        } else {
            setCullFace(ForgeDirection.UNKNOWN);
        }

        nominalFace(nominalFace);
        switch (nominalFace) {
            case UP:
                depth = 1 - depth;
                top = 1 - top;
                bottom = 1 - bottom;

            case DOWN:
                pos(0, left, depth, top);
                pos(1, left, depth, bottom);
                pos(2, right, depth, bottom);
                pos(3, right, depth, top);
                break;

            case EAST:
                depth = 1 - depth;
                left = 1 - left;
                right = 1 - right;

            case WEST:
                pos(0, depth, top, left);
                pos(1, depth, bottom, left);
                pos(2, depth, bottom, right);
                pos(3, depth, top, right);
                break;

            case SOUTH:
                depth = 1 - depth;
                left = 1 - left;
                right = 1 - right;

            case NORTH:
                pos(0, 1 - left, top, depth);
                pos(1, 1 - left, bottom, depth);
                pos(2, 1 - right, bottom, depth);
                pos(3, 1 - right, top, depth);
                break;
        }
    }

    public int tag() {
        return this.tag;
    }

    /**
     * Encodes an integer tag with this quad that can later be retrieved via
     * {@link NdQuadBuilder#tag()}.  Useful for models that want to perform conditional
     * transformation or filtering on static meshes.
     */
    public void tag(int tag) {
        this.tag = tag;
    }

    /**
     * Set texture coordinates.
     */
    public void uv(int vertexIndex, float u, float v) {

        this.setTexU(vertexIndex, u);
        this.setTexV(vertexIndex, v);
    }

    @Override
    public int getFlags() {
        return this.geometryFlags;
    }

    @Override
    public TextureAtlasSprite rubidium$getSprite() {
        return null;
    }

    /**
     * Modern Minecraft uses magic arrays to do this without breaking AO. This is the same thing, but without arrays.
     */
    public static Vector3f mapSideToVertex(Vector3f from, Vector3f to, int index, ForgeDirection side) {

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

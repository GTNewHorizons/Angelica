package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.api.QuadBuilder;
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

import static com.gtnewhorizons.angelica.models.json.FaceRewindHelper.DOWN;
import static com.gtnewhorizons.angelica.models.json.FaceRewindHelper.EAST;
import static com.gtnewhorizons.angelica.models.json.FaceRewindHelper.NORTH;
import static com.gtnewhorizons.angelica.models.json.FaceRewindHelper.SOUTH;
import static com.gtnewhorizons.angelica.models.json.FaceRewindHelper.UP;
import static com.gtnewhorizons.angelica.models.json.FaceRewindHelper.WEST;
import static java.lang.Float.MAX_VALUE;
import static java.lang.Float.MIN_VALUE;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class NdQuadBuilder extends Quad implements QuadBuilder {

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

    @Override
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

    private void clear() {

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

    @Override
    public void setCullFace() {
        this.computeGeometry();
        if ((this.geometryFlags & ModelQuadFlags.IS_ALIGNED) != 0)
            this.setCullFace(this.lightFace);
        else {
            this.setCullFace(ForgeDirection.UNKNOWN);
            this.nominalFace(this.lightFace);
        }
    }

    @Override
    public void nominalFace(@Nullable ForgeDirection face) {
        this.nominalFace = face;
    }

    @Override
    public ForgeDirection nominalFace() {
        return this.nominalFace;
    }

    @Override
    public void pos(int vertexIndex, float x, float y, float z) {

        this.setX(vertexIndex, x);
        this.setY(vertexIndex, y);
        this.setZ(vertexIndex, z);
        isGeometryInvalid = true;
    }

    @Override
    public void pos(int vertexIndex, Vector3f vec) {

        this.setX(vertexIndex, vec.x);
        this.setY(vertexIndex, vec.y);
        this.setZ(vertexIndex, vec.z);
        isGeometryInvalid = true;
    }

    @Override
    public Vector3f pos(int vertexIndex) {

        return new Vector3f(
            this.getX(vertexIndex),
            this.getY(vertexIndex),
            this.getZ(vertexIndex)
        );
    }

    @Override
    public float posByIndex(int vertexIndex, int coordinateIndex) {
        return Float.intBitsToFloat(this.data[vertexIndex * Quad.VERTEX_STRIDE + Quad.X_INDEX + coordinateIndex]);
    }

    /**
     * Rewinds the quad to the standard order.
     */
    public void rewind() {
        boolean[] targets = switch (getLightFace()) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            case UNKNOWN -> throw new RuntimeException("Expected non-UNKNOWN face!");
        };

        int iFirst = 0;
        float tx = targets[0] ? MIN_VALUE : MAX_VALUE;
        float ty = targets[1] ? MIN_VALUE : MAX_VALUE;
        float tz = targets[2] ? MIN_VALUE : MAX_VALUE;

        // Find the correct starting vertex
        for (int i = 0; i < 4; ++i) {
            float otx = tx;
            float oty = ty;
            float otz = tz;

            tx = targets[0] ? max(getX(i), tx) : min(getX(i), tx);
            ty = targets[1] ? max(getY(i), ty) : min(getY(i), ty);
            tz = targets[2] ? max(getZ(i), tz) : min(getZ(i), tz);

            // If the vertex pushed one of the target values, it's potentially the right one
            if (otx != tx || oty != ty || otz != otz) iFirst = i;
        }

        assert (tx == getX(iFirst) && ty == getY(iFirst) && tz == getZ(iFirst));

        // Shift the vertices - copy to a temp array, then copy back in the right order, wrapping appropriately
        int[] tmp = getRawData().clone();
        for (int i = 0; i < 4; ++i) {
            System.arraycopy(tmp, (iFirst + i) % 4 * VERTEX_STRIDE, data, i * VERTEX_STRIDE, VERTEX_STRIDE);
        }
    }

    @Override
    public void spriteBake(String spriteName, int bakeFlags) {

        final IIcon icon = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(spriteName);
        TexHelper.bakeSprite(this, icon, bakeFlags);
    }

    @Override
    public void spriteBake(IIcon sprite, int bakeFlags) {
        TexHelper.bakeSprite(this, sprite, bakeFlags);
    }

    @Override
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

    @Override
    public void tag(int tag) {
        this.tag = tag;
    }

    @Override
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
}

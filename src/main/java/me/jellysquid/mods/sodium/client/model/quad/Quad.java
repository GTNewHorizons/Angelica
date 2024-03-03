package me.jellysquid.mods.sodium.client.model.quad;

import com.gtnewhorizons.angelica.api.QuadView;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * Roughly equivalent to 16.5's BakedQuadView and BakedQuadMixin
 */
public class Quad implements QuadView {
    // Adapted from Neodymium

    public static final int INTS_PER_VERTEX = 8;
    public static final int QUAD_STRIDE = INTS_PER_VERTEX * 4;
    public static final int X_INDEX = 0;
    public static final int Y_INDEX = 1;
    public static final int Z_INDEX = 2;
    public static final int U_INDEX = 3;
    public static final int V_INDEX = 4;
    public static final int COLOR_INDEX = 5;
    public static final int NORMAL_INDEX = 6;
    public static final int LIGHTMAP_INDEX = 7;

    private final static int DEFAULT_LIGHTMAP = 15 << 20 | 15 << 4;
    private final static int DEFAULT_COLOR = 0xFFFFFFFF;

    protected final int[] data = new int[QUAD_STRIDE];

    @Getter
    private boolean deleted = false;

    private final Vector3f vectorA = new Vector3f(), vectorB = new Vector3f(), vectorC = new Vector3f();

    @Getter
    private boolean shade;
    private int cachedFlags;
    @Getter
    private ForgeDirection face;
    private int colorIndex = -1;
    private TextureAtlasSprite sprite = null;

    /** Returns the face, forced to take one of 6 directions to mirror the behavior of baked quads in 1.16.5. */
    @Override
    public ForgeDirection getLightFace() {
        return this.face != ForgeDirection.UNKNOWN ? this.face : ForgeDirection.UP;
    }

    @Override
    public ForgeDirection getCullFace() {
        return this.face;
    }

    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.data[idx * INTS_PER_VERTEX + X_INDEX]);
    }

    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.data[idx * INTS_PER_VERTEX + Y_INDEX]);
    }

    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.data[idx * INTS_PER_VERTEX + Z_INDEX]);
    }

    @Override
    public float getTexU(int idx) {
        return Float.intBitsToFloat(this.data[idx * INTS_PER_VERTEX + U_INDEX]);
    }

    @Override
    public float getTexV(int idx) {
        return Float.intBitsToFloat(this.data[idx * INTS_PER_VERTEX + V_INDEX]);
    }

    @Override
    public int getColor(int idx) {
        return this.data[idx * INTS_PER_VERTEX + COLOR_INDEX];
    }

    @Override
    public int getLight(int idx) {
        return this.data[idx * INTS_PER_VERTEX + LIGHTMAP_INDEX];
    }

    @Override
    public int getNormal(int idx) {
        return this.data[idx * INTS_PER_VERTEX + NORMAL_INDEX];
    }

    @Override
    public int getFlags() {
        return this.cachedFlags;
    }

    @Override
    public int getColorIndex() {
        return this.colorIndex;
    }

    @Override
    public TextureAtlasSprite rubidium$getSprite() {
        return this.sprite;
    }


    @Override
    public void setCullFace(ForgeDirection face) {
        this.face = face;
    }

    @Override
    public void setX(int idx, float x) {
        this.data[idx * INTS_PER_VERTEX + X_INDEX] = Float.floatToRawIntBits(x);
    }

    @Override
    public void setY(int idx, float y) {
        this.data[idx * INTS_PER_VERTEX + Y_INDEX] = Float.floatToRawIntBits(y);
    }

    @Override
    public void setZ(int idx, float z) {
        this.data[idx * INTS_PER_VERTEX + Z_INDEX] = Float.floatToRawIntBits(z);
    }

    @Override
    public void setTexU(int idx, float u) {
        this.data[idx * INTS_PER_VERTEX + U_INDEX] = Float.floatToRawIntBits(u);
    }

    @Override
    public void setTexV(int idx, float v) {
        this.data[idx * INTS_PER_VERTEX + V_INDEX] = Float.floatToRawIntBits(v);
    }

    @Override
    public void setColor(int idx, int c) {
        this.data[idx * INTS_PER_VERTEX + COLOR_INDEX] = c;
    }

    @Override
    public void setLight(int idx, int l) {
        this.data[idx * INTS_PER_VERTEX + LIGHTMAP_INDEX] = l;
    }

    @Override
    public void setNormal(int idx, int n) {
        this.data[idx * INTS_PER_VERTEX + NORMAL_INDEX] = n;
    }

    @Override
    public void setFlags(int flags) {
        this.cachedFlags = flags;
    }

    @Override
    public void setColorIndex(int i) {
        this.colorIndex = i;
    }

    @Override
    public void setSprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    @Override
    public int[] getRawData() {
        return this.data;
    }

    /**
     * Convenience: access x, y, z by index 0-2.
     */
    private float posByIndex(int vertexIndex, int coordinateIndex) {
        return Float.intBitsToFloat(this.data[vertexIndex * INTS_PER_VERTEX + coordinateIndex]);
    }

    /**
     * Convenience: access x, y, z by index 0-2.
     */
    private void setPosByIndex(int vertexIndex, int coordinateIndex, float val) {
        this.data[vertexIndex * INTS_PER_VERTEX + coordinateIndex] = Float.floatToRawIntBits(val);
    }

    /**
     * Offset all xs, ys, or zs by index 0-2.
     */
    private void offsetPos(int idx, float offset) {
        this.setPosByIndex(0, idx, this.posByIndex(0, idx) + offset);
        this.setPosByIndex(1, idx, this.posByIndex(1, idx) + offset);
        this.setPosByIndex(2, idx, this.posByIndex(2, idx) + offset);
        this.setPosByIndex(3, idx, this.posByIndex(3, idx) + offset);
    }

    private void clearNormals() {
        this.setNormal(0, 0);
        this.setNormal(1, 0);
        this.setNormal(2, 0);
        this.setNormal(3, 0);
    }

    private void clearLight() {
        this.setLight(0, DEFAULT_LIGHTMAP);
        this.setLight(1, DEFAULT_LIGHTMAP);
        this.setLight(2, DEFAULT_LIGHTMAP);
        this.setLight(3, DEFAULT_LIGHTMAP);
    }

    private boolean isEmpty() {
        return this.getX(0) == this.getX(1)
            && this.getX(1) == this.getX(2)
            && this.getX(2) == this.getX(3)
            && this.getY(0) == this.getY(1)
            && this.getY(1) == this.getY(2)
            && this.getY(2) == this.getY(3);
    }

    private void calcNormal() {
        this.vectorA.set(
            this.getX(1) - this.getX(0),
            this.getY(1) - this.getY(0),
            this.getZ(1) - this.getZ(0)
        );
        this.vectorB.set(
            this.getX(2) - this.getX(1),
            this.getY(2) - this.getY(1),
            this.getZ(2) - this.getZ(1)
        );
        this.vectorA.cross(this.vectorB, this.vectorC);
    }

    protected void quadrangulate() {
        this.setX(3, this.getX(2));
        this.setY(3, this.getY(2));
        this.setZ(3, this.getZ(2));

        this.setTexU(3, this.getTexU(2));
        this.setTexV(3, this.getTexV(2));

        this.setLight(3, this.getLight(2));
        this.setColor(3, this.getColor(2));
        this.setNormal(3, this.getNormal(2));
    }

    @Override
    public QuadView copyFrom(QuadView quad) {

        System.arraycopy(quad.getRawData(), 0, this.data, 0, QUAD_STRIDE);

        this.deleted = quad.isDeleted();
        this.shade = quad.isShade();
        this.face = quad.getFace();
        this.colorIndex = quad.getColorIndex();
        this.cachedFlags = quad.getFlags();
        this.sprite = quad.rubidium$getSprite();

        return this;
    }

    private void read(int[] rawBuffer, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, BlockRenderer.Flags flags) {
        System.arraycopy(rawBuffer, offset, this.data, 0, QUAD_STRIDE);

        if (offsetX != 0) this.offsetPos(0, offsetX);
        if (offsetY != 0) this.offsetPos(1, offsetY);
        if (offsetZ != 0) this.offsetPos(2, offsetZ);

        if (!flags.hasColor) this.setColors(DEFAULT_COLOR);
        if (!flags.hasNormals) this.clearNormals();
        if (!flags.hasBrightness) this.clearLight();

        // sus
        this.shade = flags.hasBrightness;

        if (drawMode == GL11.GL_TRIANGLES) this.quadrangulate();
    }

    @Override
    public void setState(int[] rawBuffer, int offset, BlockRenderer.Flags flags, int drawMode, float offsetX, float offsetY, float offsetZ) {
        this.deleted = false;

        read(rawBuffer, offset, offsetX, offsetY, offsetZ, drawMode, flags);

        if (this.isEmpty()) {
            // ignore empty quads (e.g. alpha pass of EnderIO item conduits)
            this.deleted = true;
            return;
        }

        this.calcNormal();
        this.face = ModelQuadFacing.toDirection(ModelQuadFacing.fromVector(this.vectorC));
        this.cachedFlags = ModelQuadFlags.getQuadFlags(this);
    }

    @Override
    public String toString() {
        return String.format(
            Locale.ENGLISH,
            "%s[(%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f)]",
            this.deleted ? "XXX " : "",
            this.getX(0),
            this.getY(0),
            this.getZ(0),
            this.getX(1),
            this.getY(1),
            this.getZ(1),
            this.getX(2),
            this.getY(2),
            this.getZ(2),
            this.getX(3),
            this.getY(3),
            this.getZ(3)
        );
    }
}

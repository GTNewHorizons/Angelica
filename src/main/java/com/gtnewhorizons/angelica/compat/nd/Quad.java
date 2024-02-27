package com.gtnewhorizons.angelica.compat.nd;

import lombok.Getter;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.Locale;

public class Quad implements ModelQuadView {
    // Adapted from Neodymium

    private final static int DEFAULT_BRIGHTNESS = 15 << 20 | 15 << 4;
    private final static int DEFAULT_COLOR = 0xFFFFFFFF;

    public float[] xs = new float[4];
    public float[] ys = new float[4];
    public float[] zs = new float[4];
    public float[] us = new float[4];
    public float[] vs = new float[4];
    public int[] cs = new int[4];
    public int[] ns = new int[4];
    public int[] bs = new int[4];

    public boolean deleted;

    private final Vector3f vectorA = new Vector3f(), vectorB = new Vector3f(), vectorC = new Vector3f();

    @Getter
    private boolean shade;
    private int cachedFlags;
    @Getter
    private ForgeDirection face;
    private int colorIndex = -1;

    public int[] getColors() {
        return this.cs;
    }

    /** Returns the face, forced to take one of 6 directions to mirror the behavior of baked quads in 1.16.5. */
    @Override
    public ForgeDirection getLightFace() {
        return this.face != ForgeDirection.UNKNOWN ? this.face : ForgeDirection.UP;
    }

    @Override
    public float getX(int idx) {
        return xs[idx];
    }

    @Override
    public float getY(int idx) {
        return ys[idx];
    }

    @Override
    public float getZ(int idx) {
        return zs[idx];
    }

    @Override
    public int getColor(int idx) {
        return cs[idx];
    }

    @Override
    public float getTexU(int idx) {
        return us[idx];
    }

    @Override
    public float getTexV(int idx) {
        return vs[idx];
    }

    @Override
    public int getFlags() {
        return this.cachedFlags;
    }

    @Override
    public int getLight(int idx) {
        return bs[idx];
    }

    @Override
    public int getNormal(int idx) {
        return ns[idx];
    }

    @Override
    public int getColorIndex() {
        return colorIndex;
    }

    @Override
    public TextureAtlasSprite rubidium$getSprite() {
        return null;
    }

    public void copyFrom(Quad quad) {

        System.arraycopy(quad.xs, 0, this.xs, 0, 4);
        System.arraycopy(quad.ys, 0, this.ys, 0, 4);
        System.arraycopy(quad.zs, 0, this.zs, 0, 4);

        System.arraycopy(quad.us, 0, this.us, 0, 4);
        System.arraycopy(quad.vs, 0, this.vs, 0, 4);

        System.arraycopy(quad.cs, 0, this.cs, 0, 4);
        System.arraycopy(quad.ns, 0, this.ns, 0, 4);
        System.arraycopy(quad.bs, 0, this.bs, 0, 4);

        this.deleted = quad.deleted;
        this.shade = quad.shade;
        this.cachedFlags = quad.cachedFlags;
        this.face = quad.face;
        this.colorIndex = quad.colorIndex;
    }

    public void setRaw(int[] data, boolean shade, @Nullable ForgeDirection face, int colorIndex, int flags) {

        int i = 0;
        for(int vi = 0; vi < 4; vi++) {

            xs[vi] = Float.intBitsToFloat(data[i + 0]);
            ys[vi] = Float.intBitsToFloat(data[i + 1]);
            zs[vi] = Float.intBitsToFloat(data[i + 2]);

            us[vi] = Float.intBitsToFloat(data[i + 3]);
            vs[vi] = Float.intBitsToFloat(data[i + 4]);

            cs[vi] = data[i + 5];
            ns[vi] = data[i + 6];
            bs[vi] = data[i + 7];

            i += 8;
        }

        this.shade = shade;
        this.deleted = false;
        this.face = face;
        this.colorIndex = colorIndex;
        this.cachedFlags = flags;
    }

    private void read(int[] rawBuffer, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, BlockRenderer.Flags flags) {
        final int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        int i = offset;
        for(int vi = 0; vi < vertices; vi++) {

            xs[vi] = Float.intBitsToFloat(rawBuffer[i + 0]) + offsetX;
            ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;

            us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;
            ns[vi] = flags.hasNormals ? rawBuffer[i + 6] : 0;
            bs[vi] = flags.hasBrightness ? rawBuffer[i + 7] : DEFAULT_BRIGHTNESS;

            i += 8;
        }

        // sus
        this.shade = flags.hasBrightness;

        if(vertices == 3) {

            // Quadrangulate!
            xs[3] = xs[2];
            ys[3] = ys[2];
            zs[3] = zs[2];

            us[3] = us[2];
            vs[3] = vs[2];

            bs[3] = bs[2];
            cs[3] = cs[2];
            ns[3] = ns[2];
        }
    }

    public void setState(int[] rawBuffer, int offset, BlockRenderer.Flags flags, int drawMode, float offsetX, float offsetY, float offsetZ) {
        deleted = false;

        read(rawBuffer, offset, offsetX, offsetY, offsetZ, drawMode, flags);

        if(xs[0] == xs[1] && xs[1] == xs[2] && xs[2] == xs[3] && ys[0] == ys[1] && ys[1] == ys[2] && ys[2] == ys[3]) {
            // ignore empty quads (e.g. alpha pass of EnderIO item conduits)
            deleted = true;
            return;
        }

        vectorA.set(xs[1] - xs[0], ys[1] - ys[0], zs[1] - zs[0]);
        vectorB.set(xs[2] - xs[1], ys[2] - ys[1], zs[2] - zs[1]);
        vectorA.cross(vectorB, vectorC);

        this.face = ModelQuadFacing.toDirection(ModelQuadFacing.fromVector(vectorC));
        this.cachedFlags = ModelQuadFlags.getQuadFlags(this);
    }

    public static boolean isValid(Quad q) {
        return q != null && !q.deleted;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[(%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f)]", deleted ? "XXX " : "", xs[0], ys[0], zs[0], xs[1], ys[1], zs[1], xs[2], ys[2], zs[2], xs[3], ys[3], zs[3]);
    }
}

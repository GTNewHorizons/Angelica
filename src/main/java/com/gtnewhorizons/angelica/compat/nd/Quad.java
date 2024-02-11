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

    public ModelQuadFacing normal;

    private final Vector3f vectorA = new Vector3f(), vectorB = new Vector3f(), vectorC = new Vector3f();

    private boolean hasColor;
    private boolean hasShade;
    private boolean hasNormals;

    private int cachedFlags;
    private ForgeDirection face;
    private int colorIndex = -1;

    public boolean hasColor() {
        return this.hasColor;
    }

    public int[] getColors() {
        return this.cs;
    }

    public boolean hasShade() {
        return this.hasShade;
    }

    public boolean hasNormals() {
        return this.hasNormals;
    }

    /** Returns the face, forced to take one of 6 directions to mirror the behavior of baked quads in 1.16.5. */
    public ForgeDirection getCoercedFace() {
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

    private void read(int[] rawBuffer, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, BlockRenderer.Flags flags) {
        final int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for(int vi = 0; vi < vertices; vi++) {
            int i = offset + vi * 8;

            xs[vi] = Float.intBitsToFloat(rawBuffer[i + 0]) + offsetX;
            ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;

            us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;
            ns[vi] = flags.hasNormals ? rawBuffer[i + 6] : 0;
            bs[vi] = flags.hasBrightness ? rawBuffer[i + 7] : DEFAULT_BRIGHTNESS;

            this.hasColor = flags.hasColor;
            this.hasShade = flags.hasBrightness;
            this.hasNormals = flags.hasNormals;


            i += 8;
        }

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

        normal = ModelQuadFacing.fromVector(vectorC);
        this.face = ModelQuadFacing.toDirection(normal);
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

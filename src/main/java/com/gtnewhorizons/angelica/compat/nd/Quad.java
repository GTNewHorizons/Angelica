package com.gtnewhorizons.angelica.compat.nd;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public class Quad implements ModelQuadView {
    // Temporarily borrowed from Neodymium

    private final static int DEFAULT_BRIGHTNESS = 15 << 20 | 15 << 4;
    private final static int DEFAULT_COLOR = 0xFFFFFFFF;

    public float[] xs = new float[4];
    public float[] ys = new float[4];
    public float[] zs = new float[4];
    public float minX = Float.POSITIVE_INFINITY;
    public float minY = Float.POSITIVE_INFINITY;
    public float minZ = Float.POSITIVE_INFINITY;
    public float maxX = Float.NEGATIVE_INFINITY;
    public float maxY = Float.NEGATIVE_INFINITY;
    public float maxZ = Float.NEGATIVE_INFINITY;
    public float[] us = new float[4];
    public float[] vs = new float[4];
    public int[] bs = new int[4];
    public int[] cs = new int[4];
    // TODO normals?
    public boolean deleted;
    public boolean noMerge;

    public QuadNormal normal;
    public int offset;
    public BlockRenderer.Flags flags;

    // Is positive U direction parallel to edge 0-1?
    public boolean uDirectionIs01;

    public boolean isRectangle;

    // 0: quads glued together on edge 1-2 or 3-0 ("megaquad row length")
    // 1: quads glued together on edge 0-1 or 2-3 ("megaquad column length")
    private int[] quadCountByDirection = {1, 1};
    public static int[] totalMergeCountByPlane = new int[3];

    // When we merge with another quad, we forget what we used to be like.
    // Keep a reference to the quad we first merged with, and use it as a reminder.
    public Quad mergeReference;

    private static final Vector3f vectorA = new Vector3f();
    private static final Vector3f vectorB = new Vector3f();
    private static final Vector3f vectorC = new Vector3f();
    private boolean hasColor;
    private boolean hasShade;

    public boolean hasColor() {
        return this.hasColor;
    }

    public boolean hasShade() {
        return this.hasShade;
    }

    public ForgeDirection getFace() {
        // TODO: Sodium/Quad Facing
        return ForgeDirection.UP;
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
        return 0;
    }

    @Override
    public int getLight(int idx) {
        return bs[idx];
    }

    @Override
    public int getNormal(int idx) {
        return 0;
    }

    @Override
    public int getColorIndex() {
        return 0;
    }

    @Override
    public TextureAtlasSprite rubidium$getSprite() {
        return null;
    }

    private void read(int[] rawBuffer, int offset, float offsetX, float offsetY, float offsetZ, int drawMode, BlockRenderer.Flags flags) {
        int vertices = drawMode == GL11.GL_TRIANGLES ? 3 : 4;
        for(int vi = 0; vi < vertices; vi++) {
            int i = offset + vi * 8;

            xs[vi] = Float.intBitsToFloat(rawBuffer[i + 0]) + offsetX;
            ys[vi] = Float.intBitsToFloat(rawBuffer[i + 1]) + offsetY;
            zs[vi] = Float.intBitsToFloat(rawBuffer[i + 2]) + offsetZ;

            us[vi] = Float.intBitsToFloat(rawBuffer[i + 3]);
            vs[vi] = Float.intBitsToFloat(rawBuffer[i + 4]);

            bs[vi] = flags.hasBrightness ? rawBuffer[i + 7] : DEFAULT_BRIGHTNESS;
            cs[vi] = flags.hasColor ? rawBuffer[i + 5] : DEFAULT_COLOR;
            this.hasColor |= flags.hasColor;
            this.hasShade |= flags.hasBrightness;

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
        }
    }

    public void setState(int[] rawBuffer, int offset, BlockRenderer.Flags flags, int drawMode, float offsetX, float offsetY, float offsetZ) {
        resetState();

        read(rawBuffer, offset, offsetX, offsetY, offsetZ, drawMode, flags);

        if(xs[0] == xs[1] && xs[1] == xs[2] && xs[2] == xs[3] && ys[0] == ys[1] && ys[1] == ys[2] && ys[2] == ys[3]) {
            // ignore empty quads (e.g. alpha pass of EnderIO item conduits)
            deleted = true;
            return;
        }

        uDirectionIs01 = us[0] != us[1];

        updateMinMaxXYZ();
        updateIsRectangle();
        if(!isRectangle) {
            // merging non-rectangles (e.g. Carpenter's Blocks wedge) is buggy, don't do it
            noMerge = true;
        }

        vectorA.set(xs[1] - xs[0], ys[1] - ys[0], zs[1] - zs[0]);
        vectorB.set(xs[2] - xs[1], ys[2] - ys[1], zs[2] - zs[1]);
        vectorA.cross(vectorB, vectorA);

        normal = QuadNormal.fromVector(vectorC);
    }

    private void resetState() {
        Arrays.fill(xs, 0);
        Arrays.fill(ys, 0);
        Arrays.fill(zs, 0);
        Arrays.fill(us, 0);
        Arrays.fill(vs, 0);
        Arrays.fill(bs, 0);
        Arrays.fill(cs, 0);

        minX = Float.POSITIVE_INFINITY;
        minY = Float.POSITIVE_INFINITY;
        minZ = Float.POSITIVE_INFINITY;
        maxX = Float.NEGATIVE_INFINITY;
        maxY = Float.NEGATIVE_INFINITY;
        maxZ = Float.NEGATIVE_INFINITY;

        deleted = noMerge = false;
        normal = null;
        offset = 0;
        flags = null;
        uDirectionIs01 = false;
        Arrays.fill(quadCountByDirection, 1);
        Arrays.fill(totalMergeCountByPlane, 0);
        mergeReference = null;
    }

    public void writeToBuffer(BufferWriter out) throws IOException {
        for(int vertexI = 0; vertexI < 4; vertexI++) {
            int vi = vertexI;
            int provokingI = 3;

            float x = xs[vi];
            float y = ys[vi];
            float z = zs[vi];

            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(z);

            float u = us[vi];
            float v = vs[vi];

            if(false/*Config.shortUV*/) {
                out.writeShort((short)(Math.round(u * 32768f)));
                out.writeShort((short)(Math.round(v * 32768f)));
            } else {
                out.writeFloat(u);
                out.writeFloat(v);
            }

            int b = bs[vi];

            out.writeInt(b);

            int c = cs[vi];

            out.writeInt(c);

            if(false/*Config.simplifyChunkMeshes*/) {
                if((quadCountByUVDirection(false) == 1 && quadCountByUVDirection(true) == 1)) {
                    // let the fragment shader know this is not a megaquad
                    out.writeByte((byte)255);
                    out.writeByte((byte)255);
                    out.writeByte((byte)255);
                    out.writeByte((byte)255);
                } else {
                    out.writeByte(us[vi] == us[provokingI] ? 0 : (byte)quadCountByUVDirection(false));
                    out.writeByte(vs[vi] == vs[provokingI] ? 0 : (byte)quadCountByUVDirection(true));
                    out.writeByte(us[vi] == us[provokingI] ? (byte)0 : 1);
                    out.writeByte(vs[vi] == vs[provokingI] ? (byte)0 : 1);
                }
            }

            assert out.position() % getStride() == 0;

            //System.out.println("[" + vertexI + "] x: " + x + ", y: " + y + " z: " + z + ", u: " + u + ", v: " + v + ", b: " + b + ", c: " + c);
        }
    }

    public int quadCountByUVDirection(boolean v) {
        if(v) {
            return quadCountByDirection[uDirectionIs01 ? 0 : 1];
        } else {
            return quadCountByDirection[uDirectionIs01 ? 1 : 0];
        }
    }

    public static int getStride() {
        return
                3 * 4                                       // XYZ          (float)
                + 2 * (/*Config.shortUV*/false ? 2 : 4)     // UV           (float)
                + 4                                         // B            (int)
                + 4                                         // C            (int)
                + (/*Config.simplifyChunkMeshes*/ false ? 4 : 0)      // megaquad XY  (byte)
                ;
    }

    private boolean isTranslatedCopyOf(Quad o, boolean checkValid) {
        if((!isValid(this) && checkValid) || !isValid(o) || normal != o.normal) return false;

        if(mergeReference != null) {
            return mergeReference.isTranslatedCopyOf(o, false);
        }

        for(int i = 1; i < 4; i++) {
            double relX = xs[i] - xs[0];
            double relY = ys[i] - ys[0];
            double relZ = zs[i] - zs[0];

            if(o.xs[i] != o.xs[0] + relX || o.ys[i] != o.ys[0] + relY || o.zs[i] != o.zs[0] + relZ) {
                return false;
            }
        }

        for(int i = 0; i < 4; i++) {
            if(us[i] != o.us[i] || vs[i] != o.vs[i] || bs[i] != o.bs[i] || cs[i] != o.cs[i]) {
                return false;
            }
        }

        return true;
    }

    public void tryToMerge(Quad o) {
        if(noMerge || o.noMerge) return;

        if(isTranslatedCopyOf(o, true)) {
            int numVerticesTouching = 0;
            boolean[] verticesTouching = new boolean[4];
            for(int i = 0; i < 4; i++) {
                for(int j = 0; j < 4; j++) {
                    if(xs[i] == o.xs[j] && ys[i] == o.ys[j] && zs[i] == o.zs[j]) {
                        verticesTouching[i] = true;
                        numVerticesTouching++;
                    }
                }
            }
            if(numVerticesTouching == 2) {
                for(int i = 0; i < 4; i++) {
                    if(verticesTouching[i]) {
                        copyVertexFrom(o, i, i);
                    }
                }

                if((verticesTouching[0] && verticesTouching[1]) || (verticesTouching[2] && verticesTouching[3])) {
                    quadCountByDirection[0] += o.quadCountByDirection[0];
                }
                if((verticesTouching[1] && verticesTouching[2]) || (verticesTouching[3] && verticesTouching[0])) {
                    quadCountByDirection[1] += o.quadCountByDirection[1];
                }

                totalMergeCountByPlane[getPlane().ordinal() - 1]++;

                mergeReference = o;

                o.deleted = true;
            }
        }
    }

    private void copyVertexFrom(Quad o, int src, int dest) {
        xs[dest] = o.xs[src];
        ys[dest] = o.ys[src];
        zs[dest] = o.zs[src];
        us[dest] = o.us[src];
        vs[dest] = o.vs[src];
        bs[dest] = o.bs[src];
        cs[dest] = o.cs[src];

        updateMinMaxXYZ(); // TODO isn't doing this a waste? I should get rid of the min/maxXYZ variables entirely.
    }

    private void updateMinMaxXYZ() {
        for(int i = 0; i < 4; i++) {
            minX = Math.min(minX, xs[i]);
            minY = Math.min(minY, ys[i]);
            minZ = Math.min(minZ, zs[i]);
            maxX = Math.max(maxX, xs[i]);
            maxY = Math.max(maxY, ys[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
    }

    private void updateIsRectangle() {
        isRectangle =
                vertexExists(minX, minY, minZ) &&
                vertexExists(minX, minY, maxZ) &&
                vertexExists(minX, maxY, minZ) &&
                vertexExists(minX, maxY, maxZ) &&
                vertexExists(maxX, minY, minZ) &&
                vertexExists(maxX, minY, maxZ) &&
                vertexExists(maxX, maxY, minZ) &&
                vertexExists(maxX, maxY, maxZ);
    }

    private boolean vertexExists(float x, float y, float z) {
        for(int i = 0; i < 4; i++) {
            if(xs[i] == x && ys[i] == y && zs[i] == z) {
                return true;
            }
        }
        return false;
    }

    // maybe minXYZ and maxXYZ should be arrays instead
    public double getMin(int coord) {
        return coord == 0 ? minX : coord == 1 ? minY : coord == 2 ? minZ : -1;
    }

    public double getMax(int coord) {
        return coord == 0 ? maxX : coord == 1 ? maxY : coord == 2 ? maxZ : -1;
    }

    public boolean onSamePlaneAs(Quad o) {
        return isValid(this) && isValid(o) && getPlane() == o.getPlane() &&
            ((getPlane() == Plane.XY && minZ == o.minZ) ||
                    (getPlane() == Plane.XZ && minY == o.minY) ||
                    (getPlane() == Plane.YZ && minX == o.minX));
    }

    public Plane getPlane() {
        return Plane.fromNormal(normal);
    }

    public static boolean isValid(Quad q) {
        return q != null && !q.deleted;
    }

    public boolean isClockwiseXZ() {
        return (xs[1] - xs[0]) * (zs[2] - zs[0]) - (xs[2] - xs[0]) * (zs[1] - zs[0]) < 0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s(%.1f, %.1f, %.1f -- %.1f, %.1f, %.1f)", deleted ? "XXX " : "", minX, minY, minZ, maxX, maxY, maxZ);
        //return String.format(Locale.ENGLISH, "%s[(%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f), (%.1f, %.1f, %.1f)]", deleted ? "XXX " : "", xs[0], ys[0], zs[0], xs[1], ys[1], zs[1], xs[2], ys[2], zs[2], xs[3], ys[3], zs[3]);
    }

    public static class QuadPlaneComparator implements Comparator<Quad> {

        public static final QuadPlaneComparator[] quadPlaneComparators = new QuadPlaneComparator[]{
                new QuadPlaneComparator(2, 1, 0), // PLANE_XY -> ZYX
                new QuadPlaneComparator(1, 2, 0), // PLANE_XZ -> YZX
                new QuadPlaneComparator(0, 2, 1)  // PLANE_YZ -> XZY
        };

        private final int c0;
        private final int c1;
        private final int c2;

        public QuadPlaneComparator(int firstCoordToCompare, int secondCoordToCompare, int thirdCoordToCompare) {
            this.c0 = firstCoordToCompare;
            this.c1 = secondCoordToCompare;
            this.c2 = thirdCoordToCompare;
        }

        @Override
        public int compare(Quad a, Quad b) {
            if(a.getMin(c0) < b.getMin(c0)) {
                return -1;
            } else if(a.getMin(c0) > b.getMin(c0)) {
                return 1;
            } else {
                if(a.getMin(c1) < b.getMin(c1)) {
                    return -1;
                } else if(a.getMin(c1) > b.getMin(c1)) {
                    return 1;
                } else {
                    if(a.getMin(c2) < b.getMin(c2)) {
                        return -1;
                    } else if(a.getMin(c2) > b.getMin(c2)) {
                        return 1;
                    } else {
                        return (int)Math.signum(a.offset - b.offset);
                    }
                }
            }
        }
    }

    public static enum Plane {
        NONE,
        XY,
        XZ,
        YZ;

        public static Plane fromNormal(QuadNormal normal) {
            return switch (normal) {
                case POSITIVE_X, NEGATIVE_X -> YZ;
                case POSITIVE_Y, NEGATIVE_Y -> XZ;
                case POSITIVE_Z, NEGATIVE_Z -> XY;
                default -> NONE;
            };
        }
    }

    public boolean isPosEqual(Quad b) {
        return Arrays.equals(xs, b.xs) && Arrays.equals(ys, b.ys) && Arrays.equals(zs, b.zs);
    }
}

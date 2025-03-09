package org.embeddedt.embeddium.impl.render.viewport;

import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.embeddedt.embeddium.impl.util.PositionUtil;
import org.joml.RoundingMode;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector3ic;

public final class Viewport {
    private final Frustum frustum;
    private final CameraTransform transform;

    private final Vector3i chunkCoords;
    private final Vector3i blockCoords;

    public Viewport(Frustum frustum, Vector3d position) {
        this.frustum = frustum;
        this.transform = new CameraTransform(position.x, position.y, position.z);

        this.chunkCoords = new Vector3i(
                PositionUtil.posToSectionCoord(position.x),
                PositionUtil.posToSectionCoord(position.y),
                PositionUtil.posToSectionCoord(position.z)
        );

        this.blockCoords = new Vector3i(position.x, position.y, position.z, RoundingMode.FLOOR);
    }

    public boolean isBoxVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.frustum.testAab(
                (float)(minX - this.transform.intX) - this.transform.fracX,
                (float)(minY - this.transform.intY) - this.transform.fracY,
                (float)(minZ - this.transform.intZ) - this.transform.fracZ,
                (float)(maxX - this.transform.intX) - this.transform.fracX,
                (float)(maxY - this.transform.intY) - this.transform.fracY,
                (float)(maxZ - this.transform.intZ) - this.transform.fracZ
        );
    }

    public boolean isBoxVisible(int intOriginX, int intOriginY, int intOriginZ, float floatSize) {
        return isBoxVisible(intOriginX, intOriginY, intOriginZ, floatSize, floatSize, floatSize);
    }

    public boolean isBoxVisible(int intOriginX, int intOriginY, int intOriginZ, float floatSizeX, float floatSizeY, float floatSizeZ) {
        float floatOriginX = (intOriginX - this.transform.intX) - this.transform.fracX;
        float floatOriginY = (intOriginY - this.transform.intY) - this.transform.fracY;
        float floatOriginZ = (intOriginZ - this.transform.intZ) - this.transform.fracZ;

        return this.frustum.testAab(
                floatOriginX - floatSizeX,
                floatOriginY - floatSizeY,
                floatOriginZ - floatSizeZ,

                floatOriginX + floatSizeX,
                floatOriginY + floatSizeY,
                floatOriginZ + floatSizeZ
        );
    }

    public CameraTransform getTransform() {
        return this.transform;
    }

    public Vector3ic getChunkCoord() {
        return this.chunkCoords;
    }

    public Vector3ic getBlockCoord() {
        return this.blockCoords;
    }
}

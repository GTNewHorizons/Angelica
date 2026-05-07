package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectDrawCallback;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.nmemAllocChecked;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.nmemReallocChecked;

public class VertexTransformTessellator extends DirectTessellator {
    public Matrix4f transformationMatrix = new Matrix4f();
    private final TestCallback drawCallback;
    private static final Vector4f reusableVector = new Vector4f();

    public VertexTransformTessellator(ByteBuffer initial, TestCallback callback) {
        super(initial);
        this.drawCallback = callback;
    }

    public VertexTransformTessellator(int capacity, TestCallback callback) {
        super(memAlloc(capacity), false);
        this.drawCallback = callback;
    }

    public void setTransformationMatrix(Matrix4f transformationMatrix) {
        this.transformationMatrix = transformationMatrix;
    }

    @Override
    public final void addVertex(double x, double y, double z) {
        if (format == null) {
            this.format = VertexFlags.getFormat(this); //TODO
        }

        ensureCapacity(this.format.getVertexSize());

        reusableVector.x = (float) (x + this.xOffset);
        reusableVector.y = (float) (y + this.yOffset);
        reusableVector.z = (float) (z + this.zOffset);
        reusableVector.w = 1;
        transformationMatrix.transform(reusableVector);
        reusableVector.x /= reusableVector.w;
        reusableVector.y /= reusableVector.w;
        reusableVector.z /= reusableVector.w;

        writePtr = format.writeToBuffer0(
            writePtr,
            this,
            reusableVector.x,
            reusableVector.y,
            reusableVector.z
        );
        this.vertexCount++;
    }

    @Override
    public int draw() {
        final int result = super.draw();
        if (drawCallback.onDraw(this)) {
            this.reset();
        }
        return result;
    }

    private void ensureCapacity(int bytes) { //TODO
        if (bufferRemaining() >= bytes) {
            return;
        }

        final long used = bufferLimit();

        int newCapacity = bufferCapacity() * 2;
        long required = used + bytes;

        while (newCapacity < required) {
            newCapacity *= 2;
        }

        if (!isResized()) {
            long newPtr = nmemAllocChecked(newCapacity);
            memCopy(startPtr, newPtr, used);
            startPtr = newPtr;
        } else {
            startPtr = nmemReallocChecked(startPtr, newCapacity);
        }

        writePtr = startPtr + used;
        endPtr = startPtr + newCapacity;
    }
}

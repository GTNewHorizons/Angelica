package com.gtnewhorizons.angelica.glsm.recording;

import akka.util.Index;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexedVAO;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IEmptyVertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IVertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

/**
 * Compiles multiple ByteBuffers of the same Vertex format into a VBO. Any buffer added to the builder will be freed afterward.
 */
public final class DisplayListVBOBuilder {

    // Removes the need for a HashMap. Since it's only an array of 16 values, this should be faster
    private final List<FormatData>[] formats = new List[VertexFlags.BITSET_SIZE];
    private int count;

    public DisplayListVBOBuilder addDraw(VertexFormat format, int drawMode, List<ByteBuffer> buffers) {
        List<FormatData> data = formats[format.getVertexFlags()];
        if (data == null) {
            data = new ArrayList<>();
            formats[format.getVertexFlags()] = data;
        }
        data.add(new FormatData(buffers, drawMode, count));
        count++;
        return this;
    }

    public DisplayListVBOBuilder addDraws(List<AccumulatedDraw> draws) {
        for (AccumulatedDraw draw : draws) {
            addDraw(draw.format, draw.drawMode, draw.drawBuffers);
        }
        return this;
    }

    public DisplayListVBO build() {
        final DisplayListVBO.SubVBO[] vbos = new DisplayListVBO.SubVBO[count];
        final ArrayList<ByteBuffer> allBuffers = new ArrayList<>();
        for (int i = 0; i < VertexFlags.BITSET_SIZE; i++) {
            List<FormatData> formatData = formats[i];
            if (formatData == null) continue;
            final VertexFormat format = DefaultVertexFormat.ALL_FORMATS[i];
            int start = 0;
            final IVertexArrayObject vao = VAOManager.createStorageVAO(format, -1); // drawMode will be ignored
            final IEmptyVertexBuffer vbo = vao.getVBO();
            for (FormatData data : formatData) {
                int vertexCount;
                final List<ByteBuffer> drawBuffers = data.buffers;
                if (drawBuffers.size() == 1) {
                    final ByteBuffer buffer = drawBuffers.get(0);
                    allBuffers.add(buffer);
                    vertexCount = format.getVertexCount(buffer);
                } else {
                    allBuffers.addAll(drawBuffers);
                    int size = 0;
                    for (ByteBuffer buffer : drawBuffers) {
                        size += buffer.remaining();
                    }
                    vertexCount = format.getVertexCount(size);
                }
                if (data.drawMode == GL11.GL_QUADS) {
                    IVertexArrayObject indexedVAO = new IndexedVAO(vbo, IndexBuffer.convertQuadsToTrigs(start, start + vertexCount));
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(indexedVAO, GL11.GL_TRIANGLES, start, vertexCount / 4 * 6);
                } else {
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(vao, data.drawMode, start, vertexCount);
                }


                start += vertexCount;
            }
            ByteBuffer bigBuffer = mergeAndDelete(allBuffers);
            vbo.allocate(bigBuffer, false);
            memFree(bigBuffer);

            allBuffers.clear();
        }
        return new DisplayListVBO(vbos);
    }

    private static ByteBuffer mergeAndDelete(List<ByteBuffer> buffers) {
        if (buffers.size() == 1) {
            return buffers.get(0);
        }
        int needed = 0;
        for (ByteBuffer buffer : buffers) {
            needed += buffer.remaining();
        }

        ByteBuffer out = memAlloc(needed);
        long dst = memAddress0(out);

        for (ByteBuffer buffer : buffers) {
            final int len = buffer.limit();
            final long address = memAddress0(buffer);
            memCopy(address, dst, len);
            dst += len;

            nmemFree(address);
        }

        out.limit(needed);
        return out;
    }

    private static final class FormatData {

        private final List<ByteBuffer> buffers;
        private final int drawMode;
        private final int drawIndex;

        public FormatData(List<ByteBuffer> buffers, int drawMode, int drawIndex) {
            this.buffers = buffers;
            this.drawMode = drawMode;
            this.drawIndex = drawIndex;
        }
    }
}

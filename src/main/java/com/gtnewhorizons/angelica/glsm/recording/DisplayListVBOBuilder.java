package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

public final class DisplayListVBOBuilder {

    private final Object2ObjectOpenHashMap<VertexFormat, List<FormatData>> formats = new Object2ObjectOpenHashMap<>(4);
    private int count;

    public DisplayListVBOBuilder addDraw(VertexFormat format, int drawMode, ByteBuffer buffer) {
        List<FormatData> data = formats.get(format);
        if (data == null) {
            data = new ArrayList<>();
            formats.put(format, data);
        }
        data.add(new FormatData(buffer, drawMode, count));
        count++;
        return this;
    }

    public DisplayListVBOBuilder addDraw(VertexFormat format, int drawMode, List<ByteBuffer> buffers) {
        List<FormatData> data = formats.get(format);
        if (data == null) {
            data = new ArrayList<>();
            formats.put(format, data);
        }
        data.add(new FormatData(buffers, drawMode, count));
        count++;
        return this;
    }

    public DisplayListVBO build() {
        final DisplayListVBO.SubVBO[] vbos = new DisplayListVBO.SubVBO[count];
        final List<ByteBuffer> allBuffers = new ArrayList<>();
        for (Map.Entry<VertexFormat, List<FormatData>> entry : formats.entrySet()) {
            final VertexFormat format = entry.getKey();
            int start = 0;
            VertexBuffer vbo = VAOManager.createVAO(format, -1); // drawMode will be ignored
            for (FormatData data : entry.getValue()) {
                int vertexCount;
                final List<ByteBuffer> drawBuffers = data.buffers;
                if (drawBuffers.size() == 1) {
                    allBuffers.add(drawBuffers.get(0));
                    vertexCount = format.getVertexCount(drawBuffers.get(0));
                } else {
                    allBuffers.addAll(data.buffers);
                    int size = 0;
                    for (ByteBuffer buffer : allBuffers) {
                        size += buffer.remaining();
                    }
                    vertexCount = format.getVertexCount(size);
                }

                final int index = data.drawIndex;
                vbos[index] = new DisplayListVBO.SubVBO(vbo, data.drawMode, start, vertexCount);
                start += vertexCount;
            }
            ByteBuffer bigBuffer = merge(allBuffers);
            vbo.upload(bigBuffer);
            memFree(bigBuffer);

            allBuffers.clear();
        }
        return new DisplayListVBO(vbos);
    }

    private static ByteBuffer merge(List<ByteBuffer> buffers) {
        if (buffers.size() == 1) {
            return buffers.get(0);
        } else {
            int needed = 0;
            for (ByteBuffer buffer : buffers) {
                needed += buffer.remaining();
            }

            ByteBuffer out = memAlloc(needed);
            long dst = memAddress0(out);

            for (ByteBuffer buffer : buffers) {
                int len = buffer.remaining();
                long src = memAddress0(buffer) + buffer.position();
                memCopy(src, dst, len);
                dst += len;
            }

            out.position(needed);
            out.flip();
            return out;
        }
    }

    private static final class FormatData {

        private final List<ByteBuffer> buffers;
        private final int drawMode;
        private final int drawIndex;

        public FormatData(ByteBuffer buffer, int drawMode, int drawIndex) {
            this.buffers = new ArrayList<>();
            this.buffers.add(buffer);
            this.drawMode = drawMode;
            this.drawIndex = drawIndex;
        }

        public FormatData(List<ByteBuffer> buffers, int drawMode, int drawIndex) {
            this.buffers = buffers;
            this.drawMode = drawMode;
            this.drawIndex = drawIndex;
        }
    }
}

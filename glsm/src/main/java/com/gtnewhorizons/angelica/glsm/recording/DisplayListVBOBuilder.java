package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.BaseVAO;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexedVAO;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IVertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCalloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.nmemFree;

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
        final ImmediateExtendedAttribHandler extHandler = GLSMHooks.immediateExtendedHandler;
        final DisplayListVBO.SubVBO[] vbos = new DisplayListVBO.SubVBO[count];
        final ArrayList<ByteBuffer> allBuffers = new ArrayList<>();
        final IntArrayList extVbos = new IntArrayList();
        for (int i = 0; i < VertexFlags.BITSET_SIZE; i++) {
            List<FormatData> formatData = formats[i];
            if (formatData == null) continue;
            final VertexFormat format = DefaultVertexFormat.ALL_FORMATS[i];
            final boolean wantExt = extHandler != null && format.hasTexture();
            if (extHandler == null && GLSMConfig.extendedAttribsExpected && format.hasTexture()) {
                GLStateManager.warnOnce("dl-ext-no-handler",
                    "Textured display list compiled before the immediate ext-attribute handler was installed! POM data will be missing for this geometry.");
            }
            int start = 0;
            final IVertexArrayObject vao = VAOManager.createStorageVAO(format, -1, 0); // drawMode will be ignored
            final IVertexBuffer vbo = vao.getVBO();

            // Quad/triangle draws of this format whose VAOs need the ext attributes attached after upload.
            final List<IVertexArrayObject> extVaos = wantExt ? new ArrayList<>() : null;
            final IntArrayList extStarts = wantExt ? new IntArrayList() : null;
            final IntArrayList extCounts = wantExt ? new IntArrayList() : null;
            final IntArrayList extPrims = wantExt ? new IntArrayList() : null;

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
                        size += buffer.limit();
                    }
                    vertexCount = format.getVertexCount(size);
                }
                if (data.drawMode == GL11.GL_QUADS) {
                    final IVertexArrayObject indexedVAO = new IndexedVAO(vbo, IndexBuffer.convertQuadsToTrigs(start, start + vertexCount));
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(indexedVAO, GL11.GL_TRIANGLES, 0, vertexCount / 4 * 6);
                    if (wantExt) {
                        extVaos.add(indexedVAO);
                        extStarts.add(start);
                        extCounts.add(vertexCount);
                        extPrims.add(4);
                    }
                } else if (data.drawMode == GL11.GL_TRIANGLES) {
                    if (wantExt) {
                        final IVertexArrayObject triVao = new BaseVAO(vbo);
                        vbos[data.drawIndex] = new DisplayListVBO.SubVBO(triVao, GL11.GL_TRIANGLES, start, vertexCount);
                        extVaos.add(triVao);
                        extStarts.add(start);
                        extCounts.add(vertexCount);
                        extPrims.add(3);
                    } else {
                        vbos[data.drawIndex] = new DisplayListVBO.SubVBO(vao, GL11.GL_TRIANGLES, start, vertexCount);
                    }
                } else if (data.drawMode == GL11.GL_QUAD_STRIP) {
                    // GL_QUAD_STRIP is removed in core profile; an even-length GL_TRIANGLE_STRIP produces the same quads
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(vao, GL11.GL_TRIANGLE_STRIP, start, vertexCount & ~1);
                } else if (data.drawMode == GL11.GL_POLYGON) {
                    // GL_POLYGON is removed in core profile; convert to GL_TRIANGLE_FAN which is equivalent for convex polygons
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(vao, GL11.GL_TRIANGLE_FAN, start, vertexCount);
                } else {
                    vbos[data.drawIndex] = new DisplayListVBO.SubVBO(vao, data.drawMode, start, vertexCount);
                }


                start += vertexCount;
            }
            ByteBuffer bigBuffer = mergeAndDelete(allBuffers);
            vbo.allocate(bigBuffer, start);

            if (wantExt && !extVaos.isEmpty()) {
                final int extVbo = buildAndAttachExt(extHandler, format, bigBuffer, extVaos, extStarts, extCounts, extPrims);
                if (extVbo != 0) extVbos.add(extVbo);
            }

            memFree(bigBuffer);

            allBuffers.clear();
        }
        return new DisplayListVBO(vbos, extVbos.toIntArray());
    }

    private static int buildAndAttachExt(ImmediateExtendedAttribHandler handler, VertexFormat format,
                                         ByteBuffer bigBuffer,
                                         List<IVertexArrayObject> extVaos, IntArrayList extStarts, IntArrayList extCounts,
                                         IntArrayList extPrims) {
        final int extStride = ImmediateExtendedAttribHandler.EXT_STRIDE;
        final int stride = format.getVertexSize();
        final int posOffset = 0;
        final int texOffset = ImmediateExtendedAttribHandler.texOffset(format);
        final int normalOffset = ImmediateExtendedAttribHandler.normalOffset(format);

        int extVerts = 0;
        for (int q = 0; q < extStarts.size(); q++) {
            extVerts = Math.max(extVerts, extStarts.getInt(q) + extCounts.getInt(q));
        }

        final ByteBuffer ext = memCalloc(extVerts, extStride);
        final long extAddr = memAddress0(ext);
        final long srcBase = memAddress0(bigBuffer);
        for (int q = 0; q < extStarts.size(); q++) {
            final int rStart = extStarts.getInt(q);
            final int rCount = extCounts.getInt(q);
            final int rPrim = extPrims.getInt(q);
            handler.buildPacked(srcBase + (long) rStart * stride, stride, posOffset, texOffset, normalOffset, rCount, rPrim,
                extAddr + (long) rStart * extStride, extStride);
        }

        final int extVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, extVbo);
        ext.position(0).limit(extVerts * extStride);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, ext, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        memFree(ext);

        for (IVertexArrayObject iv : extVaos) {
            iv.bind();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, extVbo);
            ImmediateExtendedAttribHandler.setupExtAttribPointers(0L, extStride);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            iv.unbind();
        }
        return extVbo;
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

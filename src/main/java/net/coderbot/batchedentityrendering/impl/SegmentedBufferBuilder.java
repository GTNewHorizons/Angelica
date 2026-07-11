package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadView;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.rendering.tesr.TemplateBuffer;
import com.gtnewhorizons.angelica.rendering.tesr.MeshBuffer;
import com.gtnewhorizons.angelica.rendering.tesr.VertexTransform;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4fc;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;

public class SegmentedBufferBuilder {

    public static final VertexFormat FORMAT = BatchVertexFormats.POSITION_COLOR_TEXTURE_LIGHTF_NORMAL;

    static final class LayerBuffer {
        final RenderLayer layer;
        ByteBuffer buffer;
        final MeshBuffer mesh = new MeshBuffer();
        int vertexCount;
        boolean uploaded;
        long lastUsedMs;

        LayerBuffer(RenderLayer layer) {
            this.layer = layer;
            this.buffer = BufferUtils.createByteBuffer(Math.max(layer.getExpectedBufferSize(), 4096));
        }

        void ensureCapacity(int bytes) {
            buffer = MeshBuffer.ensureCapacity(buffer, bytes, true);
        }

        void uploadForDraw() {
            if (uploaded) return;
            uploaded = true;
            final ByteBuffer view = buffer.duplicate();
            view.flip();
            mesh.upload(layer.getVertexFormat(), layer.getDrawMode(), view, vertexCount);
        }

        void setupDraw() {
            mesh.bind();
        }

        void draw(int firstVertex, int count) {
            mesh.draw(firstVertex, count);
        }

        void finishDraw() {
            mesh.unbind();
        }

        void reset() {
            buffer.clear();
            vertexCount = 0;
            uploaded = false;
        }
    }

    private final Map<RenderLayer, LayerBuffer> layerBuffers = new Object2ObjectOpenHashMap<>();
    private final List<LayerBuffer> bufferList = new ArrayList<>();
    private final List<BufferSegment> segments = new ArrayList<>();
    private LayerBuffer current;
    private RenderLayer currentType;
    private int currentBlockEntityId;
    private int currentEntityColor;
    private int segmentStartVertex;
    private long allocatedBytes;

    public void begin(RenderLayer type, int blockEntityId) {
        final int entityColor = packEntityColor(CapturedRenderingState.INSTANCE.getCurrentEntityColor());
        if (type == currentType && blockEntityId == currentBlockEntityId && entityColor == currentEntityColor && current != null) {
            return;
        }
        endSegment();
        LayerBuffer buffer = layerBuffers.get(type);
        if (buffer == null) {
            buffer = new LayerBuffer(type);
            layerBuffers.put(type, buffer);
            bufferList.add(buffer);
            allocatedBytes += buffer.buffer.capacity();
        }
        current = buffer;
        currentType = type;
        currentBlockEntityId = blockEntityId;
        currentEntityColor = entityColor;
        segmentStartVertex = current.vertexCount;
    }

    public static int packEntityColor(Vector4fc c) {
        return ((int) (c.w() * 255f + 0.5f) << 24) | ((int) (c.x() * 255f + 0.5f) << 16) | ((int) (c.y() * 255f + 0.5f) << 8) | (int) (c.z() * 255f + 0.5f);
    }

    public void addQuad(ModelQuadView quad) {
        if (current == null) {
            throw new IllegalStateException("addQuad() without begin()");
        }
        final VertexFormat format = currentType.getVertexFormat();
        ensureCurrentCapacity(format.getVertexSize() * 4);
        format.writeQuad(quad, current.buffer);
        current.vertexCount += 4;
    }

    public void addTemplateInstance(TemplateBuffer template, Matrix4fc mv, Vector3f scratch, int colorABGR, int packedLight, Matrix4fc texMatrix) {
        if (current == null) {
            throw new IllegalStateException("addTemplateInstance() without begin()");
        }
        final VertexFormat format = currentType.getVertexFormat();
        ensureCurrentCapacity(format.getVertexSize() * template.vertexCount);
        final ByteBuffer buffer = current.buffer;
        final long ptr = memAddress0(buffer) + buffer.position();
        final long end = VertexTransform.writeInstance(ptr, format, template, mv, scratch, colorABGR, packedLight, texMatrix);
        buffer.position(buffer.position() + (int) (end - ptr));
        current.vertexCount += template.vertexCount;
    }

    private void ensureCurrentCapacity(int bytes) {
        final int before = current.buffer.capacity();
        current.ensureCapacity(bytes);
        allocatedBytes += current.buffer.capacity() - before;
    }

    private void endSegment() {
        if (current != null && current.vertexCount > segmentStartVertex) {
            segments.add(new BufferSegment(currentType, currentBlockEntityId, currentEntityColor, segmentStartVertex,
                current.vertexCount - segmentStartVertex, current));
        }
        segmentStartVertex = current == null ? 0 : current.vertexCount;
    }

    public List<BufferSegment> getSegments() {
        endSegment();
        currentType = null;
        current = null;
        return segments;
    }

    public boolean isEmpty() {
        for (int i = 0, n = bufferList.size(); i < n; i++) {
            if (bufferList.get(i).vertexCount != 0) return false;
        }
        return true;
    }

    public int getVertexCount() {
        int total = 0;
        for (int i = 0, n = bufferList.size(); i < n; i++) {
            total += bufferList.get(i).vertexCount;
        }
        return total;
    }

    public void reset() {
        for (int i = 0, n = bufferList.size(); i < n; i++) {
            bufferList.get(i).reset();
        }
        segments.clear();
        currentType = null;
        current = null;
        currentBlockEntityId = 0;
        segmentStartVertex = 0;
    }

    public void resetAndReclaim(long nowMs, long maxIdleMs) {
        for (int i = bufferList.size() - 1; i >= 0; i--) {
            final LayerBuffer lb = bufferList.get(i);
            if (lb.vertexCount > 0) {
                lb.lastUsedMs = nowMs;
            }
            lb.reset();
            if (nowMs - lb.lastUsedMs > maxIdleMs) {
                allocatedBytes -= lb.buffer.capacity();
                lb.mesh.delete();
                layerBuffers.remove(lb.layer);
                bufferList.remove(i);
            }
        }
        segments.clear();
        currentType = null;
        current = null;
        currentBlockEntityId = 0;
        segmentStartVertex = 0;
    }

    public void freeAll() {
        for (int i = 0, n = bufferList.size(); i < n; i++) {
            bufferList.get(i).mesh.delete();
        }
        layerBuffers.clear();
        bufferList.clear();
        segments.clear();
        currentType = null;
        current = null;
        currentBlockEntityId = 0;
        segmentStartVertex = 0;
        allocatedBytes = 0;
    }

    public long allocatedBytes() {
        return allocatedBytes;
    }

    public int bufferCount() {
        return layerBuffers.size();
    }
}

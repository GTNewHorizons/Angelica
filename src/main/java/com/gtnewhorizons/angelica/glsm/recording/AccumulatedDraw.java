package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.client.model.obj.Vertex;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;

/**
 * Represents an accumulated draw call during display list compilation.
 * Matrix-as-Data Architecture: Stores untransformed vertices + captured matrix.
 * The matrix will be applied at runtime, not baked into vertices.
 */
public class AccumulatedDraw {
    public final Matrix4f transform;
    public final VertexFormat format;
    public ByteBuffer drawData;
    public int vertexCount;
    public final int drawMode;
    public final int commandIndex; // Position in command list for state tracking

    public AccumulatedDraw(DirectTessellator tessellator, Matrix4f transform, int commandIndex) {
        this.transform = transform; // Snapshot for runtime application
        this.format = tessellator.getVertexFormat();
        this.drawMode = tessellator.drawMode;
        this.drawData = tessellator.getBufferCopy();
        this.vertexCount = tessellator.getVertexCount();
        this.commandIndex = commandIndex;
    }

    public AccumulatedDraw(VertexFormat format, int drawMode, ByteBuffer drawData, Matrix4f transform, int commandIndex) {
        this.transform = transform; // Snapshot for runtime application
        this.format = format;
        this.drawMode = drawMode;
        this.drawData = drawData;
        this.vertexCount = format.getVertexCount(drawData);
        this.commandIndex = commandIndex;
    }

    public void mergeDraw(ByteBuffer data) {
        List<ByteBuffer> buffers = Arrays.asList(drawData, data);
        int needed = drawData.remaining() + data.remaining();
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
        drawData = out;

        this.vertexCount = format.getVertexCount(needed);
    }

//    @Override
//    public String toString() {
//        return String.format("AccumulatedDraw[quads=%d, cmdIdx=%d, flags=C%dT%dL%dN%d]",
//            quads.size(), commandIndex, flags.hasColor ? 1 : 0, flags.hasTexture ? 1 : 0, flags.hasBrightness ? 1 : 0, flags.hasNormals ? 1 : 0);
//    }
}

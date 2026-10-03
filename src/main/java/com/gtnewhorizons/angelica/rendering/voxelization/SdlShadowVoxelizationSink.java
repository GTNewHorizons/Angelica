package com.gtnewhorizons.angelica.rendering.voxelization;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import net.coderbot.iris.pipeline.transform.RwImageStoreExtractor;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.buffer.GlBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;

public final class SdlShadowVoxelizationSink implements ShadowVoxelizer.Sink {

    private static final Logger LOG = LogManager.getLogger("Angelica");
    private static final int VBUF_BINDING = RwImageStoreExtractor.VG_VBUF_SSBO_BINDING;
    private static final int RANGES_BINDING = RwImageStoreExtractor.VG_RANGES_SSBO_BINDING;

    private static int encodersThisFrame;
    private static int regionsThisFrame;
    private static int rangesThisFrame;

    private final VoxelRangeTable table = new VoxelRangeTable();
    private int rangesBuf;
    private long rangesCapacityBytes;
    private boolean warnedBeginFailure;

    private static RenderBackend sdl() {
        return BackendManager.RENDER_BACKEND;
    }

    @Override
    public boolean region(RenderRegion region, GlVertexFormat format, float offsetX, float offsetY, float offsetZ) {
        final RenderRegion.DeviceResources resources = region.getResources();
        if (resources == null) return false;
        final GlBuffer vertexBuffer = resources.getVertexBuffer();
        if (vertexBuffer == null || vertexBuffer.handle() == 0) return false;
        table.beginRegion(vertexBuffer.handle(), offsetX, offsetY, offsetZ);
        regionsThisFrame++;
        return true;
    }

    @Override
    public void range(int vertexOffset, int vertexCount) {
        table.addRange(vertexOffset, vertexCount);
    }

    public static int takeEncoders() { final int n = encodersThisFrame; encodersThisFrame = 0; return n; }

    public static int takeRegions() { final int n = regionsThisFrame; regionsThisFrame = 0; return n; }

    public static int takeRanges() { final int n = rangesThisFrame; rangesThisFrame = 0; return n; }

    public void flush() {
        final int regions = table.regionCount();
        if (regions == 0) return;
        upload();
        RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, VBUF_BINDING, table.regionVbuf(0));
        final long pass = sdl().beginVoxelizationBatch(VBUF_BINDING);
        if (pass == 0) {
            if (!warnedBeginFailure) {
                warnedBeginFailure = true;
                LOG.warn("shadow voxelization: compute encoder refused; no voxel writes this pass");
            }
            return;
        }
        encodersThisFrame++;
        rangesThisFrame += table.entryCount();
        for (int i = 0; i < regions; i++) {
            if (i > 0) RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, VBUF_BINDING, table.regionVbuf(i));
            sdl().voxelizeRegion(pass, i > 0, table.regionX(i), table.regionY(i), table.regionZ(i), table.regionRangeBase(i), table.regionRangeCount(i), table.regionVertexTotal(i));
        }
        sdl().endVoxelizationBatch(pass);
    }

    private void upload() {
        final ByteBuffer data = table.entries();
        final int bytes = data.remaining();
        if (rangesBuf == 0) rangesBuf = GLStateManager.glGenBuffers();
        RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, RANGES_BINDING, rangesBuf);
        if (rangesCapacityBytes < bytes) {
            long cap = Math.max(rangesCapacityBytes, 4096L);
            while (cap < bytes) cap <<= 1;
            rangesCapacityBytes = cap;
            GLStateManager.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, rangesCapacityBytes, GL15.GL_STREAM_DRAW);
        }
        GLStateManager.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, data);
    }

    public void discard() {
        table.clear();
    }

    public void delete() {
        if (rangesBuf != 0) {
            GLStateManager.glDeleteBuffers(rangesBuf);
            rangesBuf = 0;
            rangesCapacityBytes = 0;
        }
        table.free();
    }
}

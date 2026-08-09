package com.gtnewhorizons.angelica.rendering.voxelization;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import net.coderbot.iris.pipeline.transform.RwImageStoreExtractor;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.buffer.GlBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;

public final class SdlShadowVoxelizationSink implements ShadowVoxelizer.Sink {

    private static final Logger LOG = LogManager.getLogger("Angelica");
    private static final int SSBO_BINDING = RwImageStoreExtractor.VG_VBUF_SSBO_BINDING;

    private static int encodersThisFrame;
    private static int dispatchesThisFrame;
    private static int regionsThisFrame;

    private boolean warnedBindFailure;
    private boolean warnedBeginFailure;
    private long pass;
    private int pendingVertexBuffer;
    private float pendingX;
    private float pendingY;
    private float pendingZ;

    private static SDLGPURenderBackend sdl() {
        return (SDLGPURenderBackend) BackendManager.RENDER_BACKEND;
    }

    @Override
    public boolean region(RenderRegion region, GlVertexFormat format, float offsetX, float offsetY, float offsetZ) {
        final RenderRegion.DeviceResources resources = region.getResources(format);
        if (resources == null) return false;
        final GlBuffer vertexBuffer = resources.getVertexBuffer();
        if (vertexBuffer == null || vertexBuffer.handle() == 0) return false;
        pendingVertexBuffer = vertexBuffer.handle();
        pendingX = offsetX;
        pendingY = offsetY;
        pendingZ = offsetZ;
        regionsThisFrame++;
        return true;
    }

    @Override
    public boolean range(int vertexOffset, int vertexCount) {
        if (pendingVertexBuffer != 0) {
            if (!sdl().bindVoxelizationRegion(SSBO_BINDING, pendingVertexBuffer, pass, pendingX, pendingY, pendingZ)) {
                if (!warnedBindFailure) {
                    warnedBindFailure = true;
                    LOG.warn("shadow voxelization: could not bind region vertex buffer {} at SSBO slot {}", pendingVertexBuffer, SSBO_BINDING);
                }
                return false;
            }
            pendingVertexBuffer = 0;
        }
        if (pass == 0) {
            pass = sdl().beginVoxelizationBatch(SSBO_BINDING);
            if (pass != 0) encodersThisFrame++;
            if (pass == 0) {
                if (!warnedBeginFailure) {
                    warnedBeginFailure = true;
                    LOG.warn("shadow voxelization: compute encoder refused; no voxel writes this pass");
                }
                return false;
            }
        }
        sdl().voxelizeRange(pass, vertexOffset, vertexCount);
        dispatchesThisFrame++;
        return true;
    }

    public static int takeEncoders() { final int n = encodersThisFrame; encodersThisFrame = 0; return n; }

    public static int takeDispatches() { final int n = dispatchesThisFrame; dispatchesThisFrame = 0; return n; }

    public static int takeRegions() { final int n = regionsThisFrame; regionsThisFrame = 0; return n; }

    @Override
    public void finish() {
        if (pass != 0) {
            sdl().endVoxelizationBatch(pass);
            pass = 0;
        }
        pendingVertexBuffer = 0;
    }
}

package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizons.angelica.config.GpuCullingMode;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.rendering.celeritas.MultiDrawModeResolver;
import me.jellysquid.mods.sodium.client.gui.options.named.MultiDrawMode;
import org.lwjgl.opengl.ContextCapabilities;

public final class GpuCulling {

    private GpuCulling() {}

    private static final SectionMetaBuffer SECTION_META = new SectionMetaBuffer();

    private static GpuDrivenChunkCuller culler;

    private static volatile GpuCullingMode activeMode = GpuCullingMode.CPU_ONLY;

    public static GpuCullingMode mode() {
        return activeMode;
    }

    public static void setMode(GpuCullingMode mode) {
        activeMode = mode;
    }

    public enum Availability {
        AVAILABLE,
        NO_BACKEND_COMPUTE,
        MULTI_DRAW_NOT_INDIRECT,
        NO_GL42,
        NO_SSBO,
        NO_MULTI_DRAW_INDIRECT
    }

    public static Availability availability() {
        final RenderBackend backend = BackendManager.RENDER_BACKEND;
        if (!backend.supportsGpuDrivenCulling()) return Availability.NO_BACKEND_COMPUTE;
        if (MultiDrawModeResolver.resolve() != MultiDrawMode.INDIRECT) return Availability.MULTI_DRAW_NOT_INDIRECT;

        if (backend.isSDLGPU()) return Availability.AVAILABLE;

        final ContextCapabilities caps = GLStateManager.capabilities;
        if (caps == null || !caps.OpenGL42) return Availability.NO_GL42;
        if (!RenderSystem.supportsSSBO()) return Availability.NO_SSBO;
        if (!RenderSystem.supportsMultiDrawIndirect()) return Availability.NO_MULTI_DRAW_INDIRECT;
        return Availability.AVAILABLE;
    }

    public static boolean isAvailable() {
        return availability() == Availability.AVAILABLE;
    }

    public static SectionMetaBuffer sectionMeta() {
        return SECTION_META;
    }

    public static GpuDrivenChunkCuller culler() {
        if (culler == null) culler = new GpuDrivenChunkCuller();
        return culler;
    }

    public static void onWorldUnload() {
        SECTION_META.reset();
    }

    public static void shutdown() {
        if (culler != null) {
            culler.shutdown();
            culler = null;
        }
        SECTION_META.shutdown();
    }
}

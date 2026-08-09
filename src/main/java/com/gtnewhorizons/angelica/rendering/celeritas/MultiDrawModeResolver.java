package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import me.jellysquid.mods.sodium.client.gui.options.named.MultiDrawMode;
import org.lwjgl.opengl.ContextCapabilities;

public final class MultiDrawModeResolver {

    private MultiDrawModeResolver() {}

    public static boolean indirectSupported() {
        final ContextCapabilities caps = GLStateManager.capabilities;
        return caps != null && (caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect);
    }

    public static MultiDrawMode resolve() {
        final MultiDrawMode configured = ClientProxy.options().advanced.multiDrawMode;
        if (configured != MultiDrawMode.INDIRECT && BackendManager.RENDER_BACKEND.isIndirectRequired()) {
            return MultiDrawMode.INDIRECT;
        }
        if (configured == MultiDrawMode.INDIRECT && !indirectSupported()) {
            return MultiDrawMode.DIRECT;
        }
        return configured;
    }
}

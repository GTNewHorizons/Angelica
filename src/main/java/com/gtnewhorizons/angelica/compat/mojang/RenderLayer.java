package com.gtnewhorizons.angelica.compat.mojang;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class RenderLayer { // Aka: RenderType (Iris)

    public RenderLayer(String name, VertexFormat format, int mode, int i, Object o, boolean b, Runnable setupRenderState, Runnable clearRenderState) {}

    public static RenderLayer solid() {
        return null;
    }

    public static RenderLayer cutout() {
        return null;
    }

    public static RenderLayer cutoutMipped() {
        return null;
    }

    public static RenderLayer translucent() {
        return null;
    }

    public static RenderLayer tripwire() {
        return null;
    }

    public static List<RenderLayer> getBlockLayers() {
        return Collections.emptyList();
    }

    public void setupRenderState() {}

    public void clearRenderState() {}

    public int mode() {
        return 1;
    }

    public VertexFormat format() {
        return null;
    }

    public boolean shouldSortOnUpload() {
        return true;
    }

    public int bufferSize() {
        return 0;
    }

    public void end(BufferBuilder buffer, int i, int i1, int i2) {}

    public Object affectsCrumbling() {
        return null;
    }

    public Optional<RenderLayer> outline() {
        return Optional.empty();
    }

    public boolean isOutline() {
        return false;
    }


}

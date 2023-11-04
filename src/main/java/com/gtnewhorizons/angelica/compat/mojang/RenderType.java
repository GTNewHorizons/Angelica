package com.gtnewhorizons.angelica.compat.mojang;

import java.util.Optional;

public abstract class RenderType {

    public RenderType(String name, VertexFormat format, int mode, int i, Object o, boolean b, Runnable setupRenderState, Runnable clearRenderState) {}

    public static RenderType solid() {
        return null;
    }

    public static RenderType cutout() {
        return null;
    }

    public static RenderType cutoutMipped() {
        return null;
    }

    public static RenderType translucent() {
        return null;
    }

    public static RenderType tripwire() {
        return null;
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

    public Optional<RenderType> outline() {
        return Optional.empty();
    }

    public boolean isOutline() {
        return false;
    }


}

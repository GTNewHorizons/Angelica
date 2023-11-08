package com.gtnewhorizons.angelica.compat.mojang;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class RenderLayer extends RenderPhase { // Aka: RenderType (Iris)
    @Getter
    private final VertexFormat vertexFormat;
    @Getter
    private final int drawMode;
    @Getter
    private final int expectedBufferSize;
    private final boolean hasCrumbling;
    private final boolean translucent;
    private final Optional<RenderLayer> optionalThis;
    public RenderLayer(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, startAction, endAction);
        this.vertexFormat = vertexFormat;
        this.drawMode = drawMode;
        this.expectedBufferSize = expectedBufferSize;
        this.hasCrumbling = hasCrumbling;
        this.translucent = translucent;
        this.optionalThis = Optional.of(this);
    }

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

    public boolean hasCrumbling() {
        return hasCrumbling;
    }

    public Optional<RenderLayer> outline() {
        return Optional.empty();
    }

    public boolean isOutline() {
        return false;
    }

    public Optional<RenderLayer> asOptional() {
        return optionalThis;
    }

    public void draw(BufferBuilder lv, int cameraX, int cameraY, int cameraZ) {
    }

}

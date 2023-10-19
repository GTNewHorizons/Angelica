package net.coderbot.iris.compat.mojang;

import lombok.Getter;

public class DrawState {
    @Getter
    private final VertexFormat format;
    @Getter
    private final int vertexCount;
    @Getter
    private final int mode;

    public DrawState(VertexFormat format, int vertexCount, int mode) {
        this.format = format;
        this.vertexCount = vertexCount;
        this.mode = mode;
    }

}

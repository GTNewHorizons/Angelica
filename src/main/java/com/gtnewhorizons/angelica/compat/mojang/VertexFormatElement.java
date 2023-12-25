package com.gtnewhorizons.angelica.compat.mojang;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.function.IntConsumer;


@Getter
public class VertexFormatElement {
    protected final Type type;
    protected final Usage usage;
    protected final int index;
    protected final int count;
    protected final int byteSize;

    public VertexFormatElement(int index, Type type, Usage usage, int count) {
        this.index = index;
        this.type = type;
        this.usage = usage;
        this.count = count;
        this.byteSize = type.getSize() * count;
    }

    public void setupBufferState(long l, int i) {
        this.usage.setupBufferState(this.count, this.type.getGlType(), i, l, this.index);
    }

    public void clearBufferState() {
        this.usage.clearBufferState(this.index);
    }

    public enum Usage {
        POSITION("Position", (i, j, k, l, m) -> {
            GL11.glVertexPointer(i, j, k, l);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        }, (i) -> GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY)),
        NORMAL("Normal", (i, j, k, l, m) -> {
            GL11.glNormalPointer(j, k, l);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        }, (i) -> GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY)),
        COLOR("Vertex Color", (i, j, k, l, m) -> {
            GL11.glColorPointer(i, j, k, l);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        }, i -> GL11.glDisableClientState(GL11.GL_COLOR_ARRAY)),
        UV("UV", (i, j, k, l, m) -> {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + m);
            GL11.glTexCoordPointer(i, j, k, l);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        }, i -> {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + i);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        }),
        PADDING("Padding", (i, j, k, l, m) -> {}, i-> {}),
        GENERIC("Generic", (i, j, k, l, m) -> {
            GL20.glEnableVertexAttribArray(m);
            GL20.glVertexAttribPointer(m, i, j, false, k, l);
        }, GL20::glDisableVertexAttribArray);

        @Getter private final String name;
        private final SetupState setupState;
        private final IntConsumer clearState;

        Usage(String name, SetupState setupState, IntConsumer clearState) {
            this.name = name;
            this.setupState = setupState;
            this.clearState = clearState;
        }

        private void setupBufferState(int i, int j, int k, long l, int m) {
            this.setupState.setupBufferState(i, j, k, l, m);
        }

        public void clearBufferState(int i) {
            this.clearState.accept(i);
        }

        interface SetupState {
            void setupBufferState(int i, int j, int k, long l, int m);
        }
    }

    @Getter
    public enum Type {
        FLOAT(4, "Float", GL11.GL_FLOAT),
        UBYTE(1, "Unsigned Byte", GL11.GL_UNSIGNED_BYTE),
        BYTE(1, "Byte", GL11.GL_BYTE),
        USHORT(2, "Unsigned Short", GL11.GL_UNSIGNED_SHORT),
        SHORT(2, "Short", GL11.GL_SHORT),
        UINT(4, "Unsigned Int", GL11.GL_UNSIGNED_INT),
        INT(4, "Int", GL11.GL_INT);

        private final int size;
        private final String name;
        private final int glType;

        Type(int size, String name, int glType) {
            this.size = size;
            this.name = name;
            this.glType = glType;
        }
    }
}

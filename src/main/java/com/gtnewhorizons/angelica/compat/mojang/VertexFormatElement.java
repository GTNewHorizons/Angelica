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

    public void setupBufferState(long offset, int stride) {
        this.usage.setupBufferState(this.count, this.type.getGlType(), stride, offset, this.index);
    }

    public void clearBufferState() {
        this.usage.clearBufferState(this.index);
    }

    public enum Usage {
        POSITION("Position", (size, type, stride, pointer, index) -> {
            GL11.glVertexPointer(size, type, stride, pointer);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL20.glVertexAttribPointer(index, size, type, false, stride, pointer);
        }, index -> GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY)),
        NORMAL("Normal", (size, type, stride, pointer, index) -> {
            GL11.glNormalPointer(type, stride, pointer);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
            GL20.glVertexAttribPointer(index, size, type, false, stride, pointer);
        }, index -> GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY)),
        COLOR("Vertex Color", (size, type, stride, pointer, index) -> {
            GL11.glColorPointer(size, type, stride, pointer);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GL20.glVertexAttribPointer(index, size, type, false, stride, pointer);
        }, index -> GL11.glDisableClientState(GL11.GL_COLOR_ARRAY)),
        UV("UV", (size, type, stride, pointer, index) -> {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + index);
            GL11.glTexCoordPointer(size, type, stride, pointer);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GL20.glVertexAttribPointer(index, size, type, false, stride, pointer);
        }, index -> {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + index);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        }),
        PADDING("Padding", (size, type, stride, pointer, index) -> {}, index-> {}),
        GENERIC("Generic", (size, type, stride, pointer, index) -> {
            GL20.glEnableVertexAttribArray(index);
            GL20.glVertexAttribPointer(index, size, type, false, stride, pointer);
        }, GL20::glDisableVertexAttribArray);

        @Getter private final String name;
        private final SetupState setupState;
        private final IntConsumer clearState;

        Usage(String name, SetupState setupState, IntConsumer clearState) {
            this.name = name;
            this.setupState = setupState;
            this.clearState = clearState;
        }

        private void setupBufferState(int size, int type, int stride, long pointer, int index) {
            this.setupState.setupBufferState(size, type, stride, pointer, index);
        }

        public void clearBufferState(int index) {
            this.clearState.accept(index);
        }

        interface SetupState {
            void setupBufferState(int size, int type, int stride, long pointer, int index);
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

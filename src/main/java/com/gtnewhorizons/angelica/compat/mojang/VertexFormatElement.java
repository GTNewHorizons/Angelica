package com.gtnewhorizons.angelica.compat.mojang;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;


@Getter
public class VertexFormatElement {
    protected final Type type;
    protected final Usage usage;
    protected final int textureIndex;
    protected final int count;
    protected final int byteSize;

    public VertexFormatElement(int textureIndex, Type type, Usage usage, int count) {
        this.textureIndex = textureIndex;
        this.type = type;
        this.usage = usage;
        this.count = count;
        this.byteSize = type.getSize() * count;
    }

    public void setupBufferState(int elementIndex, long offset, int stride) {
        this.usage.setupBufferState(this.count, this.type.getGlType(), stride, offset, this.textureIndex, elementIndex);
    }

    public void clearBufferState(int elementIndex) {
        this.usage.clearBufferState(this.textureIndex, elementIndex);
    }

    public enum Usage {
        POSITION("Position", (size, type, stride, pointer, textureIndex, elementIndex) -> {
            GL11.glVertexPointer(size, type, stride, pointer);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

            GL20.glEnableVertexAttribArray(elementIndex);
            GL20.glVertexAttribPointer(elementIndex, size, type, false, stride, pointer);
        }, (textureIndex, elementIndex)  -> {
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GL20.glDisableVertexAttribArray(elementIndex);
        }),
        NORMAL("Normal", (size, type, stride, pointer, textureIndex, elementIndex) -> {
            GL11.glNormalPointer(type, stride, pointer);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);

            GL20.glEnableVertexAttribArray(elementIndex);
            GL20.glVertexAttribPointer(elementIndex, size, type, false, stride, pointer);
        }, (textureIndex, elementIndex)  -> {
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);

            GL20.glDisableVertexAttribArray(elementIndex);
        }),
        COLOR("Vertex Color", (size, type, stride, pointer, textureIndex, elementIndex) -> {
            GL11.glColorPointer(size, type, stride, pointer);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);

            GL20.glEnableVertexAttribArray(elementIndex);
            GL20.glVertexAttribPointer(elementIndex, size, type, false, stride, pointer);
        }, (textureIndex, elementIndex)  -> {
            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);

            GL20.glDisableVertexAttribArray(elementIndex);
        }),
        UV("UV", (size, type, stride, pointer, textureIndex, elementIndex) -> {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + textureIndex);
            GL11.glTexCoordPointer(size, type, stride, pointer);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);

            GL20.glEnableVertexAttribArray(elementIndex);
            GL20.glVertexAttribPointer(elementIndex, size, type, false, stride, pointer);
        }, (textureIndex, elementIndex)  -> {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + textureIndex);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);

            GL20.glDisableVertexAttribArray(elementIndex);
        }),
        PADDING("Padding", (size, type, stride, pointer, textureIndex, elementIndex) -> {}, (textureIndex, elementIndex) -> {}),
        GENERIC("Generic", (size, type, stride, pointer, textureIndex, elementIndex) -> {
            GL20.glEnableVertexAttribArray(elementIndex);
            GL20.glVertexAttribPointer(elementIndex, size, type, false, stride, pointer);
        }, (textureIndex, elementIndex) -> GL20.glDisableVertexAttribArray(elementIndex));

        @Getter private final String name;
        private final SetupState setupState;
        private final ClearState clearState;

        Usage(String name, SetupState setupState, ClearState clearState) {
            this.name = name;
            this.setupState = setupState;
            this.clearState = clearState;
        }

        private void setupBufferState(int size, int type, int stride, long pointer, int textureIndex, int elementIndex) {
            this.setupState.setupBufferState(size, type, stride, pointer, textureIndex, elementIndex);
        }

        public void clearBufferState(int textureIndex, int elementIndex) {
            this.clearState.clearBufferState(textureIndex, elementIndex);
        }

        interface SetupState {
            void setupBufferState(int size, int type, int stride, long pointer, int textureIndex, int elementIndex);
        }
        interface ClearState {
            void clearBufferState(int textureIndex, int elementIndex);
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

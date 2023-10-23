package net.coderbot.iris.compat.mojang;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;

public class VertexFormat {
    protected final ImmutableList<VertexFormatElement> elements;
    protected final IntList offsets = new IntArrayList();
    @Getter
    protected final int vertexSize;

    public VertexFormat(ImmutableList<VertexFormatElement> elements) {
        this.elements = elements;
        int i = 0;
        for (VertexFormatElement element : elements) {
            offsets.add(i);
            i += element.getByteSize();
        }
        vertexSize = i;
    }

    public void setupBufferState(long l) {}

    public void clearBufferState() {}

}

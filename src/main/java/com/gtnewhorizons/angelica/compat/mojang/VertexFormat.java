package com.gtnewhorizons.angelica.compat.mojang;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;

import java.util.List;

public class VertexFormat {
    @Getter
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

    @Deprecated
    public void setupBufferState(long l) {
        final int i = this.getVertexSize();
        final List<VertexFormatElement> list = this.getElements();

        for(int j = 0; j < list.size(); ++j) {
            ((VertexFormatElement)list.get(j)).setupBufferState(l + (long)this.offsets.getInt(j), i);
        }
    }

    @Deprecated
    public void clearBufferState() {
        for (VertexFormatElement vertexformatelement : this.getElements()) {
            vertexformatelement.clearBufferState();
        }
    }

}

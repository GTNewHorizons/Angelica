package com.gtnewhorizons.angelica.compat.toremove;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormatElement;
import com.gtnewhorizons.angelica.compat.toremove.DefaultVertexFormat;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.vertices.ImmediateState;
import net.coderbot.iris.vertices.IrisVertexFormats;

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
        if (BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat() && ImmediateState.renderWithExtendedVertexFormat) {
            if (this == DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL || this == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
                IrisVertexFormats.TERRAIN.setupBufferState(l);
                return;
            } else if (this == DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL) {
                IrisVertexFormats.ENTITY.setupBufferState(l);
                return;
            }
        }

        final int i = this.getVertexSize();
        final List<VertexFormatElement> list = this.getElements();

        for(int j = 0; j < list.size(); ++j) {
            ((VertexFormatElement)list.get(j)).setupBufferState(l + (long)this.offsets.getInt(j), i);
        }
    }

    @Deprecated
    public void clearBufferState() {
        if (BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat() && ImmediateState.renderWithExtendedVertexFormat) {
            if (this == DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL || this == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
                IrisVertexFormats.TERRAIN.clearBufferState();
                return;
            } else if ( this == DefaultVertexFormat.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL) {
                IrisVertexFormats.ENTITY.clearBufferState();
                return;
            }
        }
        for (VertexFormatElement vertexformatelement : this.getElements()) {
            vertexformatelement.clearBufferState();
        }
    }

}

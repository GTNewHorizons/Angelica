package org.embeddedt.embeddium.impl.render.chunk.lists;

import org.embeddedt.embeddium.impl.common.datastructure.ContextBundle;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;

public interface RenderVisualsService {
    int HAS_BLOCK_GEOMETRY = 0;
    int HAS_SPRITES = 1;
    int HAS_BLOCK_ENTITIES = 2;

    int getVisualBitmaskForSection(ContextBundle<RenderSection> renderData);
}

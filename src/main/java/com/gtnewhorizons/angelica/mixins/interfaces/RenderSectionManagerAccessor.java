package com.gtnewhorizons.angelica.mixins.interfaces;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;

public interface RenderSectionManagerAccessor {
    Long2ReferenceMap<RenderSection> angelica$getSectionByPosition();
}

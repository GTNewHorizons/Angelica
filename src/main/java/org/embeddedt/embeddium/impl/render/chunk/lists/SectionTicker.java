package org.embeddedt.embeddium.impl.render.chunk.lists;

public interface SectionTicker {
    void tickVisibleRenders();
    void onRenderListUpdated(SortedRenderLists renderLists);
}

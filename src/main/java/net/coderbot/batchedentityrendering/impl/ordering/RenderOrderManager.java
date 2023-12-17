package net.coderbot.batchedentityrendering.impl.ordering;

import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;

public interface RenderOrderManager {
    void begin(RenderLayer type);
    void startGroup();
    boolean maybeStartGroup();
    void endGroup();
    void reset();
    Iterable<RenderLayer> getRenderOrder();
}

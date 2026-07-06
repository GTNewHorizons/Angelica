package net.coderbot.batchedentityrendering.impl.ordering;

import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;

import java.util.LinkedHashSet;

public class SimpleRenderOrderManager implements RenderOrderManager {
    private final LinkedHashSet<RenderLayer> renderTypes;

    public SimpleRenderOrderManager() {
        renderTypes = new LinkedHashSet<>();
    }

    public void begin(RenderLayer type) {
        renderTypes.add(type);
    }

    public void startGroup() {
        // no-op
    }

    public boolean maybeStartGroup() {
        // no-op
        return false;
    }

    public void endGroup() {
        // no-op
    }

    @Override
    public void reset() {
        renderTypes.clear();
    }

    public Iterable<RenderLayer> getRenderOrder() {
        return renderTypes;
    }
}

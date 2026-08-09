package net.coderbot.batchedentityrendering.impl.ordering;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayList;
import java.util.List;

public class SimpleRenderOrderManager implements RenderOrderManager {
    private final ObjectOpenHashSet<RenderLayer> seen = new ObjectOpenHashSet<>();
    private final ArrayList<RenderLayer> ordered = new ArrayList<>();

    public void begin(RenderLayer type) {
        if (seen.add(type)) {
            ordered.add(type);
        }
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
        seen.clear();
        ordered.clear();
    }

    public List<RenderLayer> getRenderOrder() {
        return ordered;
    }
}

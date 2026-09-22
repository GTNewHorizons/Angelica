package net.coderbot.batchedentityrendering.impl.ordering;

import net.coderbot.batchedentityrendering.impl.TransparencyType;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;

public class TranslucencyRenderOrderManager implements RenderOrderManager {
    private final EnumMap<TransparencyType, LinkedHashSet<RenderLayer>> renderTypes;

    public TranslucencyRenderOrderManager() {
        renderTypes = new EnumMap<>(TransparencyType.class);

        for (TransparencyType type : TransparencyType.values()) {
            renderTypes.put(type, new LinkedHashSet<>());
        }
    }

    public void begin(RenderLayer type) {
        renderTypes.get(type.getTransparencyType()).add(type);
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
        renderTypes.forEach((type, set) -> {
            set.clear();
        });
    }

    public Iterable<RenderLayer> getRenderOrder() {
        int layerCount = 0;

        for (LinkedHashSet<RenderLayer> set : renderTypes.values()) {
            layerCount += set.size();
        }

        List<RenderLayer> allRenderTypes = new ArrayList<>(layerCount);

        for (LinkedHashSet<RenderLayer> set : renderTypes.values()) {
            allRenderTypes.addAll(set);
        }

        return allRenderTypes;
    }
}

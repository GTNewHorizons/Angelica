package org.embeddedt.archaicfix.occlusion;

import net.minecraft.client.renderer.WorldRenderer;
import org.embeddedt.archaicfix.occlusion.interfaces.IRendererUpdateOrderProvider;

import java.util.List;

public class DefaultRendererUpdateOrderProvider implements IRendererUpdateOrderProvider {

    private int lastUpdatedIndex = 0;

    @Override
    public void prepare(List<WorldRenderer> worldRenderersToUpdateList) {
        lastUpdatedIndex = 0;
    }

    @Override
    public boolean hasNext(List<WorldRenderer> worldRenderersToUpdateList) {
        return lastUpdatedIndex < worldRenderersToUpdateList.size();
    }

    @Override
    public WorldRenderer next(List<WorldRenderer> worldRenderersToUpdateList) {
        return worldRenderersToUpdateList.get(lastUpdatedIndex++);
    }

    @Override
    public void cleanup(List<WorldRenderer> worldRenderersToUpdateList) {
        worldRenderersToUpdateList.subList(0, lastUpdatedIndex).clear();
    }

}

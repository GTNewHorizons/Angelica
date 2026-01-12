package me.jellysquid.mods.sodium.client.gui.options.storage;

import com.cardinalstar.cubicchunks.api.compat.CubicChunksVideoSettings;

public class CubicChunksOptionStorage implements OptionStorage<CubicChunksOptionStorage.CubicChunksOptions> {

    public static class CubicChunksOptions {
        public int verticalViewDistance = CubicChunksVideoSettings.getVerticalViewDistance();
    }

    private final CubicChunksOptions options = new CubicChunksOptions();

    @Override
    public CubicChunksOptions getData() {
        return options;
    }

    @Override
    public void save() {
        CubicChunksVideoSettings.setVerticalViewDistance(options.verticalViewDistance);
    }
}

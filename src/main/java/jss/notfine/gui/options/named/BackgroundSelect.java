package jss.notfine.gui.options.named;

import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;

public enum BackgroundSelect implements NamedState {
    DEFAULT("generator.default"),
    SAND("tile.sand.default.name"),
    MYCELIUM("tile.mycel.name"),
    STONEBRICK("tile.stonebricksmooth.name"),
    MOSSY_STONEBRICK("tile.stonebricksmooth.mossy.name"),
    OAK_PLANKS("tile.wood.oak.name"),
    BIRCH_PLANKS("tile.wood.birch.name");

    private final String name;

    BackgroundSelect(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

}


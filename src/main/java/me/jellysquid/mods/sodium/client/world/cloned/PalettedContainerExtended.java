package me.jellysquid.mods.sodium.client.world.cloned;



public interface PalettedContainerExtended<T> {
    @SuppressWarnings("unchecked")
    static <T> PalettedContainerExtended<T> cast(PalettedContainer<T> container) {
        return (PalettedContainerExtended<T>) container;
    }

    PackedIntegerArray getDataArray();

    Palette<T> getPalette();

    T getDefaultValue();

    int getPaletteSize();
}

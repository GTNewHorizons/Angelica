package org.embeddedt.archaicfix.interfaces;

import net.minecraft.item.Item;

import java.util.Set;

public interface IAcceleratedRecipe {
    Set<Item> getPotentialItems();

    void invalidatePotentialItems();
}

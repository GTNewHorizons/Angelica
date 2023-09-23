package org.embeddedt.archaicfix.recipe;

import com.google.common.cache.Weigher;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;

import java.util.Set;

public class RecipeWeigher implements Weigher<Set<Item>, IRecipe[]> {
    @Override
    public int weigh(Set<Item> key, IRecipe[] value) {
        return value.length;
    }
}

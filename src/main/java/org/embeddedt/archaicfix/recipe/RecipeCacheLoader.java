package org.embeddedt.archaicfix.recipe;

import com.google.common.cache.CacheLoader;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import org.embeddedt.archaicfix.ducks.IAcceleratedRecipe;

import java.util.List;
import java.util.Set;

/**
 * Helper class that determines the set of all crafting recipes that utilize a given set of items.
 */
public class RecipeCacheLoader extends CacheLoader<Set<Item>, IRecipe[]> {
    private static final IRecipe[] NO_RECIPES = new IRecipe[0];
    @Override
    public IRecipe[] load(Set<Item> key) throws Exception {
        List<IRecipe> recipes = CraftingManager.getInstance().getRecipeList();
        if (key.size() == 0) {
            return NO_RECIPES; /* no recipes that utilize no items */
        }
        return recipes.parallelStream()
                .filter(recipe -> {
                    if (!(recipe instanceof IAcceleratedRecipe))
                        return true;
                    Set<Item> potentialItems = ((IAcceleratedRecipe) recipe).getPotentialItems();
                    if (potentialItems == null) {
                        /* The recipe can accept an unbounded set of items */
                        return true;
                    }
                    for (Item item : key) {
                        /* If the recipe would never make use of an item we have, don't include it in the final list */
                        if (!potentialItems.contains(item))
                            return false;
                    }
                    return true;
                })
                .toArray(IRecipe[]::new);
    }
}

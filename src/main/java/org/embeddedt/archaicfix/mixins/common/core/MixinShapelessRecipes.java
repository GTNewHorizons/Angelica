package org.embeddedt.archaicfix.mixins.common.core;

import com.google.common.collect.ImmutableSet;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;
import org.embeddedt.archaicfix.ducks.IAcceleratedRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(ShapelessRecipes.class)
public class MixinShapelessRecipes implements IAcceleratedRecipe {
    @Shadow @Final public List recipeItems;

    private Set<Item> allPossibleItems;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void buildItemCache(ItemStack p_i1918_1_, List p_i1918_2_, CallbackInfo ci)
    {
        ImmutableSet.Builder<Item> builder = ImmutableSet.builder();
        for(Object stack : recipeItems) {
            if(stack != null && ((ItemStack)stack).getItem() != null)
                builder.add(((ItemStack)stack).getItem());
        }
        allPossibleItems = builder.build();
    }

    @Override
    public Set<Item> getPotentialItems() {
        return allPossibleItems;
    }

    @Override
    public void invalidatePotentialItems() {
        /* No-op, fixed set of items */
    }
}

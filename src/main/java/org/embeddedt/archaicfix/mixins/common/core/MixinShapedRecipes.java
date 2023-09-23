package org.embeddedt.archaicfix.mixins.common.core;

import com.google.common.collect.ImmutableSet;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import org.embeddedt.archaicfix.ducks.IAcceleratedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ShapedRecipes.class)
public class MixinShapedRecipes implements IAcceleratedRecipe {
    private Set<Item> allPossibleItems;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void collectItems(int w, int h, ItemStack[] stacks, ItemStack out, CallbackInfo ci) {
        ImmutableSet.Builder<Item> builder = ImmutableSet.builder();
        for(ItemStack stack : stacks) {
            if(stack != null && stack.getItem() != null)
                builder.add(stack.getItem());
        }
        allPossibleItems = builder.build();
    }
    @Override
    public Set<Item> getPotentialItems() {
        return allPossibleItems;
    }

    @Override
    public void invalidatePotentialItems() {
        /* No-op, the set of items is fixed for this recipe */
    }
}

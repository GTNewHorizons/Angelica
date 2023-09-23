package org.embeddedt.archaicfix.recipe;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

public class LastMatchedInfo {
    public final IRecipe recipe;
    int hash;
    ItemStack[] invItems;

    public LastMatchedInfo(IRecipe recipe, InventoryCrafting inventory) {
        invItems = new ItemStack[inventory.getSizeInventory()];
        for(int i = 0; i < invItems.length; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            invItems[i] = stack != null ? stack.copy() : null;
        }
        this.recipe = recipe;
        hash = getHash(inventory);
    }

    private boolean matchesSavedInventory(InventoryCrafting inventory) {
        if(invItems == null)
            return false;
        if(invItems.length != inventory.getSizeInventory())
            return false;
        for(int i = 0; i < invItems.length; i++) {
            ItemStack newStack = inventory.getStackInSlot(i);
            /* they definitely match */
            if(invItems[i] == null && newStack == null)
                continue;
            /* they don't match */
            if(invItems[i] == null || newStack == null)
                return false;
            /* now we know they are both non-null */
            if(!invItems[i].isItemEqual(newStack) || !ItemStack.areItemStackTagsEqual(invItems[i], newStack))
                return false;
        }
        return true;
    }

    public boolean matches(InventoryCrafting crafting) {
        if(getHash(crafting) != hash)
            return false;
        return matchesSavedInventory(crafting);
    }

    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        if(recipe != null)
            return recipe.getCraftingResult(inventory);
        return null;
    }

    private int getHash(InventoryCrafting inventory) {
        int result = 1;
        for(int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            int hashCode = 1;
            if(stack != null) {
                //hashCode = 31 * hashCode + stack.stackSize;
                hashCode = 31 * hashCode + Item.getIdFromItem(stack.getItem());
                hashCode = 31 * hashCode + stack.getItemDamage();
                hashCode = 31 * hashCode + (!stack.hasTagCompound() ? 0 : stack.getTagCompound().hashCode());
            }
            result = 17 * result + hashCode;
        }
        return result;
    }
}

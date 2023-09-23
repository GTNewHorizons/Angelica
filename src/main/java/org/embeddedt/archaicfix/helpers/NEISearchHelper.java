package org.embeddedt.archaicfix.helpers;

import codechicken.nei.api.ItemInfo;
import net.minecraft.item.ItemStack;

import java.util.function.Function;

public class NEISearchHelper implements Function<ItemStack, String> {
    @Override
    public String apply(ItemStack itemStack) {
        return ItemInfo.getSearchName(itemStack);
    }
}

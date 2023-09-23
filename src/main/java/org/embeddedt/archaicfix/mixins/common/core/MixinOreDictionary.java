package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

@Mixin(OreDictionary.class)
public class MixinOreDictionary {
    /*
    @Redirect(method = "registerOreImpl", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z"), remap = false)
    private static boolean addOreSorted(ArrayList<ItemStack> list, Object o) {
        ItemStack stack = (ItemStack)o;
        int low = 0;
        int high = list.size() - 1;
        int targetId = Item.getIdFromItem(stack.getItem());
        Integer finalIndex = null;
        while(low <= high) {
            int mid = (low + high) / 2;
            ItemStack option = list.get(mid);
            int id = Item.getIdFromItem(option.getItem());
            if(id < targetId)
                low = mid + 1;
            else if(id > targetId)
                high = mid - 1;
            else {
                finalIndex = mid;
                break;
            }
        }
        if(finalIndex == null)
            finalIndex = low;
        list.add(finalIndex, stack);
        return true;
    }
     */
}

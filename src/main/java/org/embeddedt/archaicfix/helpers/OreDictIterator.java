package org.embeddedt.archaicfix.helpers;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Provides a facility to iterate over a subset of the accelerated ore dictionary.
 */
public class OreDictIterator {
    private static final IdentityHashMap<List<ItemStack>, IdentityHashMap<Item, Pair<Integer, Integer>>> ITERATOR_CACHE = new IdentityHashMap<>();

    private static int findStartingIndex(List<ItemStack> oreList, Item target) {
        int low = 0;
        int high = oreList.size() - 1;
        int targetId = Item.getIdFromItem(target);
        while(low <= high) {
            int mid = (low + high) / 2;
            ItemStack option = oreList.get(mid);
            int comparisonResult;
            if(option.getItem() == target) {
                ItemStack previous = mid > 0 ? oreList.get(mid-1) : null;
                if(previous != null && previous.getItem() == target) {
                    comparisonResult = 1;
                } else {
                    return mid;
                }
            } else {
                int optionId = Item.getIdFromItem(option.getItem());
                if(optionId < targetId)
                    comparisonResult = -1;
                else if(optionId > targetId)
                    comparisonResult = 1;
                else
                    throw new IllegalStateException();
            }
            if (comparisonResult < 0)
                low = mid + 1;
            else
                high = mid - 1;
        }
        return low;
    }
    public static Item getItemFromStack(ItemStack stack) {
        if(stack == null)
            return null;
        else
            return stack.getItem();
    }
    public static Iterator<ItemStack> get(String oreName, Item target) {
        if(target == null)
            return Collections.emptyIterator();
        List<ItemStack> oreList = OreDictionary.getOres(oreName, false);
        if (oreList == null)
            return Collections.emptyIterator();
        if(oreList.size() < 10)
            return oreList.iterator();
        return get(oreList, target);
    }
    public static Iterator<ItemStack> get(List<ItemStack> oreList, Item target) {
        if(target == null)
            return Collections.emptyIterator();
        var targetMap = ITERATOR_CACHE.get(oreList);
        if(targetMap == null) {
            targetMap = new IdentityHashMap<>();
            ITERATOR_CACHE.put(oreList, targetMap);
        }
        int startIndex, endIndex;
        Pair<Integer, Integer> indices = targetMap.get(target);
        if(indices != null) {
            startIndex = indices.getLeft();
            endIndex = indices.getRight();
        } else {
            int potentialStartIndex = findStartingIndex(oreList, target);
            if(potentialStartIndex < 0 || potentialStartIndex >= oreList.size() || oreList.get(potentialStartIndex).getItem() != target) {
                startIndex = -1;
                endIndex = -1;
            } else {
                startIndex = potentialStartIndex;
                int end = -1;
                for(int i = startIndex; i < oreList.size(); i++) {
                    if(oreList.get(i).getItem() != target) {
                        end = i;
                        break;
                    }
                }
                if(end == -1)
                    end = oreList.size();
                endIndex = end;
            }
            targetMap.put(target, Pair.of(startIndex, endIndex));
        }
        if(startIndex == -1 || endIndex == -1)
            return Collections.emptyIterator();
        else {
            return oreList.subList(startIndex, endIndex).iterator();
        }
    }

    public static void clearCache(String oreName) {
        List<ItemStack> theList = OreDictionary.getOres(oreName, false);
        if(theList != null)
            ITERATOR_CACHE.remove(theList);
    }
}

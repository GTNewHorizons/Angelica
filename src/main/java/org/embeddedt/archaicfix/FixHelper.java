package org.embeddedt.archaicfix;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.oredict.OreDictionary;
import org.embeddedt.archaicfix.interfaces.IAcceleratedRecipe;
import org.embeddedt.archaicfix.helpers.OreDictIterator;

import java.util.ArrayList;

public class FixHelper {
    public static final ArrayList<IAcceleratedRecipe> recipesHoldingPotentialItems = new ArrayList<>();

    @SubscribeEvent
    public void onSizeUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.entityLiving;
        if(entity.worldObj.isRemote && entity instanceof EntitySlime slime && entity.getAge() <= 1) {
            float newSize = 0.6F * (float)slime.getSlimeSize();
            slime.width = newSize;
            slime.height = newSize;
            slime.setPosition(slime.posX, slime.posY, slime.posZ);
        }
    }

    @SubscribeEvent
    public void onOreRegister(OreDictionary.OreRegisterEvent event) {
        for(IAcceleratedRecipe recipe : recipesHoldingPotentialItems) {
            if(recipe != null)
                recipe.invalidatePotentialItems();
        }
        recipesHoldingPotentialItems.clear();
        OreDictIterator.clearCache(event.Name);
    }
}

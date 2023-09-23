package org.embeddedt.archaicfix;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.handshake.IHandshakeState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.oredict.OreDictionary;
import org.embeddedt.archaicfix.ducks.IAcceleratedRecipe;
import org.embeddedt.archaicfix.helpers.OreDictIterator;

import java.util.*;

public class FixHelper {
    public static ArrayList<IAcceleratedRecipe> recipesHoldingPotentialItems = new ArrayList<>();
    public static Set<Object> unmoddedNetHandlers = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public static Map<ChannelHandlerContext, Attribute<IHandshakeState<?>>> handshakeStateMap = Collections.synchronizedMap(new WeakHashMap<>());

    @SubscribeEvent
    public void onSizeUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.entityLiving;
        if(entity.worldObj.isRemote && entity instanceof EntitySlime && entity.getAge() <= 1) {
            EntitySlime slime = (EntitySlime)entity;
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

    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if(!event.connectionType.equals("MODDED")) {
            ArchaicLogger.LOGGER.info("Connected to vanilla server");
            ArchaicFix.IS_VANILLA_SERVER = true;
        }
    }

    @SubscribeEvent
    public void onNetworkRegister(FMLNetworkEvent.CustomPacketRegistrationEvent event) {
        if(event.operation.equals("REGISTER")) {
            boolean hasArchaic = event.registrations.contains("archaicfix");
            if(event.handler instanceof NetHandlerPlayServer) {
                if(!hasArchaic) {
                    ArchaicLogger.LOGGER.info("Player is connecting without ArchaicFix installed");
                    unmoddedNetHandlers.add(event.handler);
                }

            } else {
                ArchaicFix.IS_VANILLA_SERVER = !hasArchaic;
                if(!hasArchaic)
                    ArchaicLogger.LOGGER.info("Connecting to server without ArchaicFix installed");
            }
        }
    }
}

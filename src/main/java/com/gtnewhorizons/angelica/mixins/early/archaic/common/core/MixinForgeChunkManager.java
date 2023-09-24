package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import cpw.mods.fml.common.Loader;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import org.embeddedt.archaicfix.ArchaicLogger;
import org.embeddedt.archaicfix.helpers.ChickenChunkHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ForgeChunkManager.class)
public class MixinForgeChunkManager {
    @Inject(method = "loadWorld", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ArrayListMultimap;keySet()Ljava/util/Set;", ordinal = 0), cancellable = true, remap = false)
    private static void callCCLoadHandler(World world, CallbackInfo ci) {
        if(!world.isRemote && Loader.isModLoaded("ChickenChunks")) {
            try {
                ChickenChunkHelper.load((WorldServer)world);
            } catch (Exception e) {
                ArchaicLogger.LOGGER.error("An exception occured while loading CC data", e);
            }
        }
    }
}

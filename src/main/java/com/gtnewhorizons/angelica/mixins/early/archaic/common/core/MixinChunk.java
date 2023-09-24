package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.embeddedt.archaicfix.ArchaicLogger;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Mixin(value = Chunk.class, priority = 1100)
public class MixinChunk {

    @Shadow @Final private List<Entity>[] entityLists;
    @Shadow @Final private World worldObj;

    @Shadow @Final public int xPosition;

    @Shadow @Final public int zPosition;

    @Shadow public Map chunkTileEntityMap;

    @Inject(method = "onChunkUnload", at = @At("HEAD"))
    public void handlePlayerChunkUnload(CallbackInfo ci) {
        final List<EntityPlayer> players = new ArrayList<>();
        for (final List<Entity> list : entityLists) {
            for (final Entity entity : list) {
                if (entity instanceof EntityPlayer) players.add((EntityPlayer) entity);
            }
        }
        for (final EntityPlayer player : players) {
            worldObj.updateEntityWithOptionalForce(player, false);
        }
    }

    @Inject(method = "getBiomeGenForWorldCoords", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/WorldChunkManager;getBiomeGenAt(II)Lnet/minecraft/world/biome/BiomeGenBase;"), cancellable = true)
    private void avoidBiomeGenOnClient(int p_76591_1_, int p_76591_2_, WorldChunkManager p_76591_3_, CallbackInfoReturnable<BiomeGenBase> cir) {
        if (this.worldObj.isRemote) {
            cir.setReturnValue(BiomeGenBase.ocean);
        }
    }

    private static LinkedList<ChunkCoordIntPair> arch$populatingChunk = new LinkedList<>(); // keep track of cascading chunk generation during chunk population

    private void logCascadingWorldGeneration() {
        ModContainer activeModContainer = Loader.instance().activeModContainer();
        String format = "{} loaded a new chunk {} in dimension {} ({}) while populating chunk {}, causing cascading worldgen lag.";

        ChunkCoordIntPair pos = new ChunkCoordIntPair(this.xPosition, this.zPosition);

        if (activeModContainer == null) {
            ArchaicLogger.LOGGER.warn(format, "Minecraft", pos, this.worldObj.provider.dimensionId, this.worldObj.provider.getDimensionName(), arch$populatingChunk.peek());
        } else {
            ArchaicLogger.LOGGER.warn(format, activeModContainer.getName(), pos, this.worldObj.provider.dimensionId, this.worldObj.provider.getDimensionName(), arch$populatingChunk.peek());
            ArchaicLogger.LOGGER.warn("Please report this to the mod's issue tracker. This log can be disabled in the ArchaicFix config.");
        }

        if (ArchaicConfig.logCascadingWorldgenStacktrace) {
            ArchaicLogger.LOGGER.warn("Stacktrace", new Exception("Cascading world generation"));
        }
    }

    @Inject(method = "populateChunk", at = @At("HEAD"))
    private void savePopulatingChunk(IChunkProvider p_76624_1_, IChunkProvider p_76624_2_, int x, int z, CallbackInfo ci) {
        if (arch$populatingChunk.size() > 0 && ArchaicConfig.logCascadingWorldgen) logCascadingWorldGeneration();
        arch$populatingChunk.push(new ChunkCoordIntPair(x, z));
    }

    @Inject(method = "populateChunk", at = @At("TAIL"))
    private void restorePopulatingChunk(IChunkProvider p_76624_1_, IChunkProvider p_76624_2_, int p_76624_3_, int p_76624_4_, CallbackInfo ci) {
        arch$populatingChunk.pop();
    }

}

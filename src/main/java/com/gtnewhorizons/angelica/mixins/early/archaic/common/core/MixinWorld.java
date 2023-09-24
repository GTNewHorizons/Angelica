package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import com.google.common.collect.ImmutableSet;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.embeddedt.archaicfix.lighting.world.lighting.LightingEngineHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Set;

@Mixin(World.class)
public abstract class MixinWorld {
    @Shadow public boolean isRemote;

    @Shadow public List playerEntities;

    @Shadow protected IChunkProvider chunkProvider;

    @Redirect(method = "getBiomeGenForCoordsBody", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/WorldChunkManager;getBiomeGenAt(II)Lnet/minecraft/world/biome/BiomeGenBase;"))
    private BiomeGenBase skipBiomeGenOnClient(WorldChunkManager manager, int x, int z) {
        if(this.isRemote)
            return BiomeGenBase.ocean;
        else
            return manager.getBiomeGenAt(x, z);
    }

    private Set<String> entityOptimizationIgnoreSet = null;

    @Inject(method = "updateEntityWithOptionalForce", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;lastTickPosX:D", ordinal = 0), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void skipUpdateIfOptimizing(Entity entity, boolean force, CallbackInfo ci, int chunkX, int chunkZ, boolean isInForcedChunk) {
        if(!ArchaicConfig.optimizeEntityTicking)
            return;
        if (isRemote || isInForcedChunk || !(entity instanceof EntityLivingBase) || entity instanceof EntityPlayer || !entity.addedToChunk) {
            return;
        }
        if(entityOptimizationIgnoreSet == null)
            entityOptimizationIgnoreSet = ImmutableSet.copyOf(ArchaicConfig.optimizeEntityTickingIgnoreList);
        if(entityOptimizationIgnoreSet.contains(EntityList.getEntityString(entity)))
            return;
        double finalDist = Double.MAX_VALUE;
        for(EntityPlayer player : (List<EntityPlayer>)entity.worldObj.playerEntities) {
            finalDist = Math.min(finalDist, player.getDistanceSq(entity.posX, entity.posY, entity.posZ));
            if(finalDist <= ArchaicConfig.optimizeEntityTickingDistance)
                break;
        }
        if(((EntityLivingBase)entity).deathTime <= 0 && finalDist > ArchaicConfig.optimizeEntityTickingDistance) {
            if(entity instanceof EntityLiving && ((AccessorEntityLiving)entity).invokeCanDespawn()) {
                /* Despawn even if outside the ticking range */
                ((AccessorEntityLiving)entity).invokeDespawnEntity();
            }
            ci.cancel();
        }
    }

    @Inject(method = "setActivePlayerChunksAndCheckLight", at = @At("TAIL"))
    private void saveInactiveChunks(CallbackInfo ci) {
        if(this.isRemote || ArchaicConfig.optimizeBlockTickingDistance <= 0)
            return;
        int renderDistance = FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().getViewDistance();
        if(renderDistance <= ArchaicConfig.optimizeBlockTickingDistance)
            return;
        EntityPlayer entityplayer;
        int j;
        int k;

        int activeDistance = ArchaicConfig.optimizeBlockTickingDistance;

        for (Object playerEntity : this.playerEntities) {
            entityplayer = (EntityPlayer) playerEntity;
            j = MathHelper.floor_double(entityplayer.posX / 16.0D);
            k = MathHelper.floor_double(entityplayer.posZ / 16.0D);

            for (int offX = -renderDistance; offX <= renderDistance; ++offX) {
                for (int offZ = -renderDistance; offZ <= renderDistance; ++offZ) {
                    if (Math.abs(offX) <= activeDistance && Math.abs(offZ) <= activeDistance) continue;
                    Chunk chunk = LightingEngineHelpers.getLoadedChunk(this.chunkProvider, offX + j, offZ + k);
                    if (chunk != null && !chunk.func_150802_k()) {
                        chunk.func_150804_b(false);
                    }
                }
            }
        }
    }

}

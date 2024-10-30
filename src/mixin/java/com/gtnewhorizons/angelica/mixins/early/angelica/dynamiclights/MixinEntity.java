package com.gtnewhorizons.angelica.mixins.early.angelica.dynamiclights;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.blockpos.IBlockPos;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightSource;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity implements IDynamicLightSource {
    @Shadow
    public World worldObj;
    @Shadow
    public double posX;
    @Shadow
    public double posY;
    @Shadow
    public double posZ;
    @Shadow
    public int chunkCoordX;
    @Shadow
    public int chunkCoordZ;
    @Shadow
    public abstract float getEyeHeight();
    @Shadow
    public boolean isDead;

    @Unique
    protected int angelica$luminance = 0;
    @Unique
    private int angelica$lastLuminance = 0;
    @Unique
    private static long angelica$lastUpdate = 0;
    @Unique
    private double angelica$prevX;
    @Unique
    private double angelica$prevY;
    @Unique
    private double angelica$prevZ;
    @Unique
    private LongOpenHashSet angelica$trackedLitChunkPos = new LongOpenHashSet();


    @Override
    public double angelica$getDynamicLightX() {
        return posX;
    }

    @Override
    public double angelica$getDynamicLightY() {
        return posY + getEyeHeight();
    }

    @Override
    public double angelica$getDynamicLightZ() {
        return posZ;
    }

    @Override
    public void angelica$resetDynamicLight() {
        this.angelica$lastLuminance = 0;
    }


    @Inject(method = "onEntityUpdate", at = @At("TAIL"))
    public void angelica$onUpdate(CallbackInfo ci) {
        if (worldObj != null && worldObj.isRemote) {
            if (isDead) {
                angelica$setDynamicLightEnabled(false);
            } else {
                angelica$dynamicLightTick();
                DynamicLights.updateTracking(this);
            }
        }
    }

    @Override
    public void angelica$dynamicLightTick() {
        this.angelica$luminance = DynamicLights.getLuminanceFromEntity((Entity) (Object) this);
    }

    @Override
    public int angelica$getLuminance() {
        return this.angelica$luminance;
    }

    @Override
    public boolean angelica$updateDynamicLight(@NotNull SodiumWorldRenderer renderer) {
        double deltaX = this.posX - this.angelica$prevX;
        double deltaY = this.posY - this.angelica$prevY;
        double deltaZ = this.posZ - this.angelica$prevZ;

        int luminance = this.angelica$getLuminance();

        if (Math.abs(deltaX) > 0.1D || Math.abs(deltaY) > 0.1D || Math.abs(deltaZ) > 0.1D || luminance != this.angelica$lastLuminance) {
            this.angelica$prevX = this.posX;
            this.angelica$prevY = this.posY;
            this.angelica$prevZ = this.posZ;
            this.angelica$lastLuminance = luminance;

            var newPos = new LongOpenHashSet();

            if (luminance > 0) {
                IBlockPos chunkPos = new BlockPos(chunkCoordX, MathHelper.floor_double(posY + getEyeHeight()) >> 4, chunkCoordZ);

                DynamicLights.scheduleChunkRebuild(renderer, chunkPos);
                DynamicLights.updateTrackedChunks(chunkPos, this.angelica$trackedLitChunkPos, newPos);

                var directionX = ((MathHelper.floor_double(posX) & 15) >= 8) ? ForgeDirection.EAST : ForgeDirection.WEST;
                var directionY = ((MathHelper.floor_double(posY + getEyeHeight()) & 15) >= 8) ? ForgeDirection.UP : ForgeDirection.DOWN;
                var directionZ = ((MathHelper.floor_double(posZ) & 15) >= 8) ? ForgeDirection.SOUTH : ForgeDirection.NORTH;

                for (int i = 0; i < 7; i++) {
                    if (i % 4 == 0) {
                        chunkPos = chunkPos.offset(directionX); // X
                    } else if (i % 4 == 1) {
                        chunkPos = chunkPos.offset(directionZ); // XZ
                    } else if (i % 4 == 2) {
                        chunkPos = chunkPos.offset(directionX.getOpposite()); // Z
                    } else {
                        chunkPos = chunkPos.offset(directionZ.getOpposite()); // origin
                        chunkPos = chunkPos.offset(directionY); // Y
                    }
                    DynamicLights.scheduleChunkRebuild(renderer, chunkPos);
                    DynamicLights.updateTrackedChunks(chunkPos, this.angelica$trackedLitChunkPos, newPos);
                }
            }

            // Schedules the rebuild of removed chunks.
            this.angelica$scheduleTrackedChunksRebuild(renderer);
            // Update tracked lit chunks.
            this.angelica$trackedLitChunkPos = newPos;
            return true;
        }
        return false;
    }

    @Override
    public void angelica$scheduleTrackedChunksRebuild(@NotNull SodiumWorldRenderer renderer) {
        for (long pos : this.angelica$trackedLitChunkPos) {
            DynamicLights.scheduleChunkRebuild(renderer, pos);
        }
    }
}

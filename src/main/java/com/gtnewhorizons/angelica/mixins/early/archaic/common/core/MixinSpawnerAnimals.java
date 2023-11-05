package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import net.minecraft.entity.Entity;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

@Mixin(SpawnerAnimals.class)
public class MixinSpawnerAnimals {
    @Shadow private HashMap eligibleChunksForSpawning;

    @ModifyConstant(method = "findChunksForSpawning", constant = @Constant(doubleValue = 24.0D))
    private double lowerSpawnRange(double old) {
        return ArchaicConfig.fixMobSpawnsAtLowRenderDist ? 16 : old;
    }

    @Redirect(method = "performWorldGenSpawning", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntityInWorld(Lnet/minecraft/entity/Entity;)Z"))
    private static boolean checkForCollision(World world, Entity instance) {
        if(!ArchaicConfig.preventEntitySuffocationWorldgen || world.getCollidingBoundingBoxes(instance, instance.boundingBox).isEmpty()) {
            return world.spawnEntityInWorld(instance);
        }
        return false;
    }
}
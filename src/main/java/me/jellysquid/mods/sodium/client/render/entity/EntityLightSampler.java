package me.jellysquid.mods.sodium.client.render.entity;

import net.minecraft.entity.Entity;
import com.gtnewhorizons.angelica.compat.mojang.BlockPosImpl;

public interface EntityLightSampler<T extends Entity> {
    int bridge$getBlockLight(T entity, BlockPosImpl pos);

    int bridge$getSkyLight(T entity, BlockPosImpl pos);
}

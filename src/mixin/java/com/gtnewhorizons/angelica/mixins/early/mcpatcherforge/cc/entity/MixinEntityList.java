package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.cc.ColorizeItem;

@Mixin(EntityList.class)
public abstract class MixinEntityList {

    @Inject(method = "addMapping(Ljava/lang/Class;Ljava/lang/String;III)V", at = @At("HEAD"))
    private static void modifyAddMapping(Class<? extends Entity> entityClass, String entityName, int entityId,
        int shellColor, int spotColor, CallbackInfo ci) {
        ColorizeItem.setupSpawnerEgg(entityName, entityId, shellColor, spotColor);
    }
}

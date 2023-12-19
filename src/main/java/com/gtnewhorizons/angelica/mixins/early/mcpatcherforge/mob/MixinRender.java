package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.mob;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.mob.MobRandomizer;

@Mixin(Render.class)
public abstract class MixinRender {

    @Shadow
    protected abstract ResourceLocation getEntityTexture(Entity entity);

    @Redirect(
        method = "bindEntityTexture(Lnet/minecraft/entity/Entity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;getEntityTexture(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/ResourceLocation;"))
    private ResourceLocation modifyBindEntityTexture(Render instance, Entity entity) {
        return MobRandomizer.randomTexture(entity, getEntityTexture(entity));
    }
}

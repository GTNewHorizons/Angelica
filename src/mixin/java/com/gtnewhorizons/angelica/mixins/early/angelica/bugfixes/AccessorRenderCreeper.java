package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import net.minecraft.client.renderer.entity.RenderCreeper;
import net.minecraft.entity.monster.EntityCreeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderCreeper.class)
public interface AccessorRenderCreeper {

    @Invoker("shouldRenderPass")
    int invokeShouldRenderPass(EntityCreeper entity, int pass, float partialTick);
}

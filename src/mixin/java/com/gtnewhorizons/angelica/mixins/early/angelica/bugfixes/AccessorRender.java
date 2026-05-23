package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Render.class)
public interface AccessorRender {
    @Invoker("bindEntityTexture")
    void angelica$bindEntityTexture(Entity entity);
}

package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.EntityRenderScope;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderManager.class)
public class MixinRenderManagerDAPI {

    @Dynamic
    @Surround(
        method = "func_147939_a(Lnet/minecraft/entity/Entity;DDDFFZ)Z",
        at = @At(remap = false, value = "INVOKE", target = "LReika/DragonAPI/Instantiable/Event/Client/EntityRenderEvent;fire(Lnet/minecraft/client/renderer/entity/Render;Lnet/minecraft/entity/Entity;DDDFF)V"),
        require = 1 // Require this if DAPI is present, which should be the case when this mixin is applied.
    )
    private void iris$wrapDoRenderDragonAPI(Render render, Entity entity) {
        @Surround.Carry
        Class<?> prevRenderable = TesrAttribution.currentRenderable;
        @Surround.Carry("lightning")
        boolean lightning = EntityIdHelper.isLightningBolt(entity);
        @Surround.Carry("nested")
        boolean nested = EntityRenderScope.begin(entity, lightning);
    }

    @Surround.Finally
    private void iris$restoreDoRenderDragonAPI(@Surround.Carry Class<?> prevRenderable, @Surround.Carry("lightning") boolean lightning, @Surround.Carry("nested") boolean nested) {
        EntityRenderScope.end(prevRenderable, lightning, nested);
    }
}

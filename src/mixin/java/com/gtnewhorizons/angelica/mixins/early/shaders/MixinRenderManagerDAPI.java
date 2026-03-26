package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderManager.class)
public class MixinRenderManagerDAPI {

    @Dynamic
    @WrapOperation(
        method = "func_147939_a(Lnet/minecraft/entity/Entity;DDDFFZ)Z",
        at = @At(remap = false, value = "INVOKE", target = "LReika/DragonAPI/Instantiable/Event/Client/EntityRenderEvent;fire(Lnet/minecraft/client/renderer/entity/Render;Lnet/minecraft/entity/Entity;DDDFF)V"),
        require = 1 // Require this if DAPI is present, which should be the case when this mixin is applied.
    )
    private void iris$wrapDoRenderDragonAPI(Render render, Entity entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        int entityId = EntityIdHelper.getEntityId(entity);
        CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
        ItemIdManager.resetItemId();
        original.call(render, entity, x, y, z, entityYaw, partialTicks);
        CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
    }
}

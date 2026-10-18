package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Render.class)
public class MixinRender_EntityFireBatch {

    @Surround(method = "renderEntityOnFire")
    private void angelica$batchEntityFire(Entity entity, double x, double y, double z, float partialTicks) {
        ModelPartBatcher.INSTANCE.beginEntityFire();
    }

    @Surround.Finally
    private void angelica$endEntityFire() {
        ModelPartBatcher.INSTANCE.endEntityFire();
    }
}

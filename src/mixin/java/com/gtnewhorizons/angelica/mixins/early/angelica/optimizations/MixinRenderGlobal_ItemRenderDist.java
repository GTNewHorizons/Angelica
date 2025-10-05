package com.gtnewhorizons.angelica.mixins.early.angelica.optimizations;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_ItemRenderDist {

    @Unique private static final int[] sodium$entityItemCount = new int[256];
    @Unique private static int sodium$itemRenderDist = 255;
    @Unique private static int sodium$itemRenderedCount;
    @Unique private static int sodium$itemLimit = Integer.MAX_VALUE;

    @Inject(method = "renderEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;countEntitiesRendered:I", ordinal = 0))
    private void sodium$resetEntitycount(CallbackInfo ci) {
        sodium$itemRenderedCount = 0;
        int entityCount = 0;
        boolean reachedLimit = false;
        sodium$itemLimit = AngelicaConfig.droppedItemLimit == 2048 ? Integer.MAX_VALUE : AngelicaConfig.droppedItemLimit;
        for (int i = 0; i < sodium$entityItemCount.length; i++) {
            entityCount += sodium$entityItemCount[i];
            sodium$entityItemCount[i] = 0;
            if (!reachedLimit && entityCount > sodium$itemLimit) {
                reachedLimit = true;
                sodium$itemRenderDist = i == 0 ? 1 : i;
            }
        }
        if (!reachedLimit) {
            sodium$itemRenderDist = 255;
        }
    }

    @ModifyVariable(method = "renderEntities", at = @At(value = "STORE", ordinal = 0), ordinal = 0, name = "flag")
    private boolean sodium$renderEntityItems(boolean flag,
                                             @Local(ordinal = 0, name = "entity") Entity entity,
                                             @Local(ordinal = 0, name = "d0") double d0,
                                             @Local(ordinal = 1, name = "d1") double d1,
                                             @Local(ordinal = 2, name = "d2") double d2) {
        if (flag && entity instanceof EntityItem) {
            final int i = Math.min((int) (entity.getDistanceSq(d0, d1, d2) / 4.0D), 255);
            sodium$entityItemCount[i]++;
            final boolean doRender = i <= sodium$itemRenderDist && sodium$itemRenderedCount < sodium$itemLimit;
            if (doRender) sodium$itemRenderedCount++;
            return doRender;
        }
        return flag;
    }

}

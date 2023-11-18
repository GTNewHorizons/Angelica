package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// Let other mixins apply, and then overwrite them
@Mixin(value=WorldRenderer.class, priority = 2000)
public class MixinWorldRenderer {
    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void updateRenderer(EntityLivingBase e){
        // Do nothing
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void updateRendererSort(EntityLivingBase e){
        // Do nothing
    }


}

package com.gtnewhorizons.angelica.mixins.late.compat;

import net.minecraft.client.model.ModelRenderer;
import net.smart.render.ModelRotationRenderer;

import org.spongepowered.asm.mixin.Mixin;

import com.gtnewhorizons.angelica.mixins.early.renderer.MixinModelRenderer;

@Mixin(ModelRotationRenderer.class)
public class MixinModelRotationRenderer extends MixinModelRenderer {

    @Override
    public void resetDisplayList() {
        super.resetDisplayList();
        ((ModelRenderer) (Object) this).compiled = false;
        ((ModelRenderer) (Object) this).displayList = 0;
    }

}

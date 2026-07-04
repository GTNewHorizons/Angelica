package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelRenderer.class)
public abstract class MixinModelRenderer {

    @Redirect(method = {"render", "renderWithRotation"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false))
    private void angelica$batchPart(int list, float scale) {
        if (!ModelPartBatcher.partDraw((ModelRenderer) (Object) this, scale)) {
            GLStateManager.glCallList(list);
        }
    }
}

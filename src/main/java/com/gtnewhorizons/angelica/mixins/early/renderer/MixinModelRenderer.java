package com.gtnewhorizons.angelica.mixins.early.renderer;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GLAllocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.gtnewhorizons.angelica.mixins.interfaces.IModelRenderer;

@Mixin(ModelRenderer.class)
public class MixinModelRenderer implements IModelRenderer {

    @Shadow
    private boolean compiled;

    @Shadow
    private int displayList;

    public void resetDisplayList() {
        if (!compiled) {
            GLAllocation.deleteDisplayLists(displayList);
            displayList = 0;
            compiled = false;
        }
    }
}

package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GLAllocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.gtnewhorizons.angelica.mixins.interfaces.IModelRenderer;

@Mixin(ModelRenderer.class)
public class MixinModelRenderer implements IModelRenderer {
    // TODO: Rendering
    @Shadow
    private boolean compiled;

    @Shadow
    private int displayList;

    public void angelica$resetDisplayList() {
        if (!compiled && displayList != 0) {
            GLAllocation.deleteDisplayLists(displayList);
            displayList = 0;
            compiled = false;
        }
    }
}

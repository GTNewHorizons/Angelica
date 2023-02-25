package com.gtnewhorizons.angelica.mixins.early.accessors;

import net.minecraft.client.model.ModelRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelRenderer.class)
public interface ModelRendererAccessor {

    @Accessor()
    boolean getCompiled();

    @Accessor()
    int getDisplayList();

    @Accessor()
    void setCompiled(boolean compiled);

    @Accessor()
    void setDisplayList(int displayList);

}

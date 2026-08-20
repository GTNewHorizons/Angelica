package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.mixins.interfaces.ModelBoxQuads;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.TexturedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelBox.class)
public interface AccessorModelBox extends ModelBoxQuads {

    @Override
    @Accessor("quadList")
    TexturedQuad[] angelica$getQuadList();
}

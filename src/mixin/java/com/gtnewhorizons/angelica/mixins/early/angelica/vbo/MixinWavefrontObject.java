package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IModelCustomExt;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.client.model.obj.WavefrontObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = WavefrontObject.class, remap = false)
public abstract class MixinWavefrontObject  {
    /**
     * @author mitchej123
     * @reason Force all models to use VBOs
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    public void renderAll() {
        ((IModelCustomExt) (Object)this).renderAllVBO();
    }
}

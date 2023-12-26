package com.gtnewhorizons.angelica.mixins.late.client.jabba;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "mcp.mobius.betterbarrels.client.render.TileEntityBaseRenderer")
public class MixinTileEntityBaseRenderer {
    @Redirect(method="saveBoundTexture", at=@At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glGetInteger(I)I"), remap=false)
    private int glGetInteger(int pname) {
        if(pname == GL11.GL_TEXTURE_BINDING_2D) {
            return GLStateManager.getBoundTexture();
        } else {
            return GL11.glGetInteger(pname);
        }
    }
}

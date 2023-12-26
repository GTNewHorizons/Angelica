package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.FloatBuffer;

@Mixin(ClippingHelperImpl.class)
public class MixinClippingHelperImpl {
    @Redirect(method="init", at=@At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glGetFloat(ILjava/nio/FloatBuffer;)V"), remap = false)
    private void angelica$glGetFloat(int pname, FloatBuffer params) {
        if(pname == GL11.GL_MODELVIEW_MATRIX) {
            GLStateManager.getMatrixState().modelViewMatrix.get(0, params);
        } else if(pname == GL11.GL_PROJECTION_MATRIX) {
            GLStateManager.getMatrixState().projectionMatrix.get(0, params);
        } else {
            GL11.glGetFloat(pname, params);
        }
    }
}

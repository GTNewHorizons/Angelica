package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.minecraft.client.renderer.ActiveRenderInfo;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

@Mixin(ActiveRenderInfo.class)
public class MixinActiveRenderInfo {
    @Shadow private static FloatBuffer modelview;
    @Shadow private static FloatBuffer projection;
    @Inject(method = "updateRenderInfo", at = @At(value = "TAIL"))
    private static void angelica$onUpdateRenderInfo(CallbackInfo ci) {
        RenderingState.INSTANCE.setProjectionMatrix(projection);
        RenderingState.INSTANCE.setModelViewMatrix(modelview);
    }

    @Redirect(method = "updateRenderInfo", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetFloat(ILjava/nio/FloatBuffer;)V"), remap = false)
    private static void angelica$glGetFloat(int pname, FloatBuffer params) {
        if(pname == GL11.GL_MODELVIEW_MATRIX) {
            GLStateManager.getMatrixState().modelViewMatrix.get(0, params);
        } else if(pname == GL11.GL_PROJECTION_MATRIX) {
            GLStateManager.getMatrixState().projectionMatrix.get(0, params);
        } else {
            GL11.glGetFloat(pname, params);
        }
    }
}

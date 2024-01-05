package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizons.angelica.compat.mojang.DefaultVertexFormat;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.VBOManager;
import net.minecraft.client.model.ModelRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelRenderer.class)
public class MixinModelRenderer {
    @Shadow
    public int displayList;

    @Redirect(method = "compileDisplayList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GLAllocation;generateDisplayLists(I)I"))
    public int generateDisplayLists(int range) {
        return VBOManager.generateDisplayLists(range);
    }

    @Redirect(method = "compileDisplayList", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V", remap = false))
    public void startCapturingVBO(int list, int mode) {
        TessellatorManager.startCapturing();
    }

    @Redirect(method = "compileDisplayList", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEndList()V", remap = false))
    public void stopCapturingVBO() {
        VBOManager.registerVBO(this.displayList, TessellatorManager.stopCapturingToVBO(DefaultVertexFormat.POSITION_TEXTURE_NORMAL));
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false))
    public void renderVBO(int list) {
        VBOManager.get(list).render(GL11.GL_QUADS);
    }
    @Redirect(method = "renderWithRotation", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false))
    public void renderWithRotationVBO(int list) {
        VBOManager.get(list).render(GL11.GL_QUADS);
    }
}

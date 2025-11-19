package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;


import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VBOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import net.minecraft.client.model.ModelRenderer;
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
        VBOManager.registerVBO(this.displayList, TessellatorManager.stopCapturingToVAO(DefaultVertexFormat.POSITION_TEXTURE_NORMAL));
    }
}

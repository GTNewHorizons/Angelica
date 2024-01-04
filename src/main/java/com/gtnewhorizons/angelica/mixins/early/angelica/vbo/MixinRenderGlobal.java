package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizons.angelica.compat.mojang.DefaultVertexFormat;
import com.gtnewhorizons.angelica.compat.mojang.VertexBuffer;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalVBOCapture;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderGlobal.class, remap = false)
public class MixinRenderGlobal implements IRenderGlobalVBOCapture {
    @Unique private VertexBuffer starVBO;
    @Unique private VertexBuffer skyVBO;
    @Unique private VertexBuffer sky2VBO;

    private static final int MAGIC_NUMER = -1000;
    private static final int STARS = MAGIC_NUMER;
    private static final int SKY = MAGIC_NUMER + 1;
    private static final int SKY2 = MAGIC_NUMER + 2;
    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lnet/minecraft/client/renderer/GLAllocation;generateDisplayLists(I)I"))
    private int generateDisplayLists(int range) {
        // Don't allocate the display lists, we'll be making VBOs instead.  Return a magic number for identification below.
        return MAGIC_NUMER;
    }

    @Override
    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glNewList(II)V", ordinal = 0))
    public void startStarsVBO(int list, int mode) {
        TessellatorManager.startCapturing();
    }

    @Override
    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glEndList()V", ordinal = 0))
    public void finishStarsVBO() {
        this.starVBO = TessellatorManager.stopCapturingToVBO(DefaultVertexFormat.POSITION);
    }

    @Redirect(method="<init>", at = @At(value="FIELD", target="Lnet/minecraft/client/renderer/Tessellator;instance:Lnet/minecraft/client/renderer/Tessellator;"))
    private Tessellator redirectTessellator() {
        TessellatorManager.startCapturing();
        return TessellatorManager.get();
    }

    @Override
    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glNewList(II)V", ordinal = 1))
    public void startSkyVBO(int list, int mode) {
        // Do nothing, we'll be making a VBO instead.
    }

    @Override
    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glEndList()V", ordinal = 1))
    public void finishSkyVBO() {
        this.skyVBO = TessellatorManager.stopCapturingToVBO(DefaultVertexFormat.POSITION);
    }

    @Override
    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glNewList(II)V", ordinal = 2))
    public void startSky2VBO(int list, int mode) {
        TessellatorManager.startCapturing();
    }

    @Override
    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glEndList()V", ordinal = 2))
    public void finishSky2VBO() {
        this.sky2VBO = TessellatorManager.stopCapturingToVBO(DefaultVertexFormat.POSITION);
    }

    @Redirect(method="renderSky(F)V", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glCallList(I)V"))
    public void renderSky(int list) {
        VertexBuffer vbo = switch (list) {
            case STARS -> this.starVBO;
            case SKY -> this.skyVBO;
            case SKY2 -> this.sky2VBO;
            default -> throw new RuntimeException("Unexpected display list: " + list);
        };
        vbo.render(GL11.GL_QUADS);
    }
}

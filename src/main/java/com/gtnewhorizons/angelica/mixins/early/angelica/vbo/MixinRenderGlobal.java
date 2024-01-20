package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizons.angelica.compat.mojang.DefaultVertexFormat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.VBOManager;
import com.gtnewhorizons.angelica.render.CloudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.IRenderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderGlobal.class)
public class MixinRenderGlobal {
    @Shadow public int starGLCallList;
    @Shadow private int glSkyList;
    @Shadow private int glSkyList2;
    @Shadow public WorldClient theWorld;
    @Shadow public Minecraft mc;
    @Shadow private int cloudTickCounter;

    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lnet/minecraft/client/renderer/GLAllocation;generateDisplayLists(I)I", ordinal = 0))
    private int generateGLRenderListBaseDisplayLists(int range) {
        return AngelicaConfig.enableSodium ? -1 : GLAllocation.generateDisplayLists(range);
    }

    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lnet/minecraft/client/renderer/GLAllocation;generateDisplayLists(I)I", ordinal = 2), remap = false)
    private int generateDisplayLists(int range) {
        return VBOManager.generateDisplayLists(range);
    }

    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glNewList(II)V", ordinal = 0), remap = false)
    public void startStarsVBO(int list, int mode) {
        TessellatorManager.startCapturing();
    }
    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glEndList()V", ordinal = 0), remap = false)
    public void finishStarsVBO() {
        VBOManager.registerVBO(starGLCallList, TessellatorManager.stopCapturingToVBO(DefaultVertexFormat.POSITION));
    }

    @Redirect(method="<init>", at = @At(value="FIELD", target="Lnet/minecraft/client/renderer/Tessellator;instance:Lnet/minecraft/client/renderer/Tessellator;"))
    private Tessellator redirectTessellator() {
        TessellatorManager.startCapturing();
        return TessellatorManager.get();
    }

    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glNewList(II)V", ordinal = 1), remap = false)
    public void startSkyVBO(int list, int mode) {
        // Do nothing, we'll be making a VBO instead and already started the capturing above
    }

    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glEndList()V", ordinal = 1), remap = false)
    public void finishSkyVBO() {
        VBOManager.registerVBO(glSkyList, TessellatorManager.stopCapturingToVBO(DefaultVertexFormat.POSITION));
    }

    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glNewList(II)V", ordinal = 2), remap = false)
    public void startSky2VBO(int list, int mode) {
        TessellatorManager.startCapturing();
    }

    @Redirect(method="<init>", at = @At(value="INVOKE", target="Lorg/lwjgl/opengl/GL11;glEndList()V", ordinal = 2), remap = false)
    public void finishSky2VBO() {
        VBOManager.registerVBO(glSkyList2, TessellatorManager.stopCapturingToVBO(DefaultVertexFormat.POSITION));
    }

    /**
     * @author mitchej123
     * @reason VBO Clouds
     */
    @Overwrite
    public void renderClouds(float partialTicks) {
        IRenderHandler renderer;
        if((renderer = theWorld.provider.getCloudRenderer()) != null) {
            renderer.render(partialTicks, theWorld, mc);
            return;
        }
        if(mc.theWorld.provider.isSurfaceWorld()) {
            CloudRenderer.getCloudRenderer().render(cloudTickCounter, partialTicks);
        }
    }
}

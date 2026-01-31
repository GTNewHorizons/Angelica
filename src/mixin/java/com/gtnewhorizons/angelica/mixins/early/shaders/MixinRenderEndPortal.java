package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.Iris;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.RenderEndPortal;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderEndPortal.class)
public class MixinRenderEndPortal {

    @Unique
    private static final ResourceLocation iris$END_PORTAL_TEXTURE = new ResourceLocation("textures/entity/end_portal.png");

    @Unique private static final float iris$TOP = 0.75f;
    @Unique private static final float iris$BOTTOM = 0.375f;
    @Unique private static final float iris$RED = 0.075f;
    @Unique private static final float iris$GREEN = 0.15f;
    @Unique private static final float iris$BLUE = 0.2f;

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityEndPortal;DDDF)V", at = @At("HEAD"), cancellable = true)
    private void iris$renderShaderPortal(TileEntityEndPortal te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        if (!Iris.getCurrentPack().isPresent()) return;

        ci.cancel();

        float progress = (SystemTimeUniforms.TIMER.getFrameTimeCounter() * 0.01f) % 1.0f;
        float u0 = progress;
        float u1 = 0.2f + progress;
        float v0 = progress;
        float v1 = 0.2f + progress;

        float top = (float) y + iris$TOP;
        float bottom = (float) y + iris$BOTTOM;

        Minecraft.getMinecraft().getTextureManager().bindTexture(iris$END_PORTAL_TEXTURE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_F(iris$RED, iris$GREEN, iris$BLUE, 1.0f);
        tess.setBrightness(240 | (240 << 16));

        // UP face
        tess.setNormal(0, 1, 0);
        tess.addVertexWithUV(x,     top, z + 1, u0, v1);
        tess.addVertexWithUV(x + 1, top, z + 1, u1, v1);
        tess.addVertexWithUV(x + 1, top, z,     u1, v0);
        tess.addVertexWithUV(x,     top, z,     u0, v0);

        // DOWN face
        tess.setNormal(0, -1, 0);
        tess.addVertexWithUV(x,     bottom, z + 1, u0, v1);
        tess.addVertexWithUV(x,     bottom, z,     u0, v0);
        tess.addVertexWithUV(x + 1, bottom, z,     u1, v0);
        tess.addVertexWithUV(x + 1, bottom, z + 1, u1, v1);

        // NORTH face (z=0)
        tess.setNormal(0, 0, -1);
        tess.addVertexWithUV(x,     top,    z, u0, v0);
        tess.addVertexWithUV(x + 1, top,    z, u1, v0);
        tess.addVertexWithUV(x + 1, bottom, z, u1, v1);
        tess.addVertexWithUV(x,     bottom, z, u0, v1);

        // SOUTH face (z=1)
        tess.setNormal(0, 0, 1);
        tess.addVertexWithUV(x,     top,    z + 1, u0, v1);
        tess.addVertexWithUV(x,     bottom, z + 1, u0, v0);
        tess.addVertexWithUV(x + 1, bottom, z + 1, u1, v0);
        tess.addVertexWithUV(x + 1, top,    z + 1, u1, v1);

        // WEST face (x=0)
        tess.setNormal(-1, 0, 0);
        tess.addVertexWithUV(x, top,    z + 1, u0, v1);
        tess.addVertexWithUV(x, top,    z,     u0, v0);
        tess.addVertexWithUV(x, bottom, z,     u1, v0);
        tess.addVertexWithUV(x, bottom, z + 1, u1, v1);

        // EAST face (x=1)
        tess.setNormal(1, 0, 0);
        tess.addVertexWithUV(x + 1, top,    z + 1, u1, v1);
        tess.addVertexWithUV(x + 1, bottom, z + 1, u1, v0);
        tess.addVertexWithUV(x + 1, bottom, z,     u0, v0);
        tess.addVertexWithUV(x + 1, top,    z,     u0, v1);

        tess.draw();
    }
}

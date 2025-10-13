package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

@Mixin(Framebuffer.class)
public class MixinFramebuffer implements IRenderTargetExt {

    @Unique private int iris$depthBufferVersion;

    @Unique private int iris$colorBufferVersion;

    @Unique public int iris$depthTextureId = -1;

    @Shadow public boolean useDepth;


    @Inject(method = "deleteFramebuffer()V", at = @At(value="INVOKE", target="Lnet/minecraft/client/shader/Framebuffer;unbindFramebuffer()V", shift = At.Shift.AFTER))
    private void iris$onDestroyBuffers(CallbackInfo ci) {
        iris$depthBufferVersion++;
        iris$colorBufferVersion++;
    }

    @Override
    public int iris$getDepthBufferVersion() {
        return iris$depthBufferVersion;
    }

    @Override
    public int iris$getColorBufferVersion() {
        return iris$colorBufferVersion;
    }

    @Override
    public int iris$getDepthTextureId() {
        return iris$depthTextureId;
    }

    @Inject(method="deleteFramebuffer()V", at=@At(value="FIELD", target="Lnet/minecraft/client/shader/Framebuffer;depthBuffer:I", ordinal = 0), require = 1)
    private void iris$deleteDepthBuffer(CallbackInfo ci) {
        if(this.iris$depthTextureId > -1 ) {
            GLStateManager.glDeleteTextures(this.iris$depthTextureId);
            this.iris$depthTextureId = -1;
        }
    }

    // Prevent the creation of a depth buffer, it will get replaced by a depth texture.
    @Redirect(method = "createFramebuffer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/shader/Framebuffer;useDepth:Z"))
    private boolean iris$noopDepthBuffer(Framebuffer instance) {
        return false;
    }

    // Uses a depth texture instead of a depth buffer
    @Inject(method = "createFramebuffer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;framebufferClear()V", shift = At.Shift.BEFORE, ordinal = 1), require = 1)
    private void iris$createDepthTexture(int width, int height, CallbackInfo ci) {
        if(this.useDepth) {
            this.iris$depthTextureId = GL11.glGenTextures();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, this.iris$depthTextureId);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, 0);
            final boolean stencil = MinecraftForgeClient.getStencilBits() != 0;
            if (stencil) {
                GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, width, height, 0, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, (IntBuffer) null);
            } else {
                GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (IntBuffer) null);
            }
            OpenGlHelper.func_153188_a(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, this.iris$depthTextureId, 0);
            if (stencil) {
                OpenGlHelper.func_153188_a(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, this.iris$depthTextureId, 0);
            }
        }
    }

}

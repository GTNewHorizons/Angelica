package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjglx.opengl.GL11;
import org.lwjglx.opengl.GL14;
import org.lwjglx.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

@Mixin(Framebuffer.class)
public abstract class MixinFramebuffer implements IRenderTargetExt {
    private int iris$depthBufferVersion;

    private int iris$colorBufferVersion;

    @Getter public boolean iris$useDepth;
    @Getter public int iris$depthTextureId = -1;

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

    // Use a depth texture instead of a depth drawScreen buffer
    @Inject(method="Lnet/minecraft/client/shader/Framebuffer;createBindFramebuffer(II)V", at=@At(value="HEAD"))
    private void iris$useDepthTexture(int width, int height, CallbackInfo ci) {
        if(this.useDepth) {
            this.useDepth = false;
            this.iris$useDepth = true;
        }
    }

    @Inject(method="deleteFramebuffer()V", at=@At(value="FIELD", target="Lnet/minecraft/client/shader/Framebuffer;depthBuffer:I", shift = At.Shift.BEFORE, ordinal = 0), remap = false)
    private void iris$deleteDepthBuffer(CallbackInfo ci) {
        if(this.iris$depthTextureId > -1 ) {
            GLStateManager.glDeleteTextures(this.iris$depthTextureId);
            this.iris$depthTextureId = -1;
        }
    }

    @Inject(method="createFramebuffer(II)V", at=@At(value="FIELD", target="Lnet/minecraft/client/shader/Framebuffer;useDepth:Z", shift=At.Shift.BEFORE, ordinal = 0))
    private void iris$createDepthTextureID(int width, int height, CallbackInfo ci) {
        if (this.iris$useDepth) {
            this.iris$depthTextureId = GL11.glGenTextures();
        }
    }

    @Inject(method="createFramebuffer(II)V", at=@At(value="FIELD", target="Lnet/minecraft/client/shader/Framebuffer;useDepth:Z", shift=At.Shift.BEFORE, ordinal = 1))
    private void iris$createDepthTexture(int width, int height, CallbackInfo ci) {
        if(this.iris$useDepth) {
            if(this.iris$depthTextureId == -1) {
                this.iris$depthTextureId = GL11.glGenTextures();
            }
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, this.iris$depthTextureId);

            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, 0);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (IntBuffer) null);
            OpenGlHelper.func_153188_a/*glFramebufferTexture2D*/(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, this.iris$depthTextureId, 0);
        }
    }

}

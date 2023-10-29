package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpenGlHelper.class)
public class MixinOpenGlHelper {
    private static int iris$drawFramebuffer = 0;
    private static int iris$readFramebuffer = 0;

    @Inject(method="func_153171_g", at=@At("HEAD"), cancellable=true)
    private static void iris$avoidRedundantBind(int target, int framebuffer, CallbackInfo ci) {
        if (target == GL30.GL_FRAMEBUFFER) {
            if (iris$drawFramebuffer == target && iris$readFramebuffer == target) {
                ci.cancel();
            } else {
                iris$drawFramebuffer = framebuffer;
                iris$readFramebuffer = framebuffer;
            }
        } else if (target == GL30.GL_DRAW_FRAMEBUFFER) {
            if (iris$drawFramebuffer == target) {
                ci.cancel();
            } else {
                iris$drawFramebuffer = framebuffer;
            }
        } else if (target == GL30.GL_READ_FRAMEBUFFER) {
            if (iris$readFramebuffer == target) {
                ci.cancel();
            } else {
                iris$readFramebuffer = framebuffer;
            }
        } else {
            throw new IllegalStateException("Invalid framebuffer target: " + target);
        }
    }

    @Inject(method="func_153174_h", at=@At("HEAD"))
    private static void iris$trackFramebufferDelete(int framebuffer, CallbackInfo ci) {
        if (iris$drawFramebuffer == framebuffer) {
            iris$drawFramebuffer = 0;
        }

        if (iris$readFramebuffer == framebuffer) {
            iris$readFramebuffer = 0;
        }
    }

}

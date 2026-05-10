package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.stereo.StereoState;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the per-eye horizontal offset to the modelview matrix during
 * {@code EntityRenderer.setupCameraTransform}, mimicking vanilla 1.7.10's anaglyph code path.
 *
 * <p>This mixin runs <strong>before</strong> Angelica's existing
 * {@code MixinEntityRenderer.captureFov} and the matrix-capture in
 * {@code MixinEntityRenderer.angelica$captureCameraMatrix}, so that
 * {@link com.gtnewhorizons.angelica.rendering.RenderingState} sees the per-eye matrix and
 * downstream Iris shader uniforms ({@code gbufferModelView}, {@code gbufferProjection})
 * naturally pick up the eye offset for free.</p>
 *
 * <p>Injection point: after {@code gluPerspective}, when the matrix mode is GL_MODELVIEW
 * and the modelview has just been loaded with identity. We apply our X translation here so
 * subsequent vanilla code (hurt camera effect, view bobbing, look rotation, position
 * translation) layers on top correctly.</p>
 *
 * <p>Vanilla {@code setupCameraTransform} order:</p>
 * <pre>
 *   glMatrixMode(PROJECTION); loadIdentity();
 *   if (anaglyph) glTranslatef(-(field*2-1)*0.07f, 0, 0);   // projection offset
 *   gluPerspective(fov, aspect, 0.05f, far);
 *   glMatrixMode(MODELVIEW); loadIdentity();
 *   if (anaglyph) glTranslatef((field*2-1)*0.1f, 0, 0);     // modelview offset  &lt;-- WE INJECT EQUIVALENT
 *   hurtCameraEffect(); setupViewBobbing();
 *   glRotatef(pitch, 1, 0, 0); glRotatef(yaw+180, 0, 1, 0);
 *   glTranslatef(-cameraX, -cameraY, -cameraZ);
 * </pre>
 *
 * <p>We inject after {@code gluPerspective} returns and the GL_MODELVIEW matrix mode is set,
 * matching the location where vanilla anaglyph applies its modelview offset.</p>
 */
@Mixin(value = EntityRenderer.class, priority = 999)
public abstract class MixinEntityRenderer_StereoCamera {

    /**
     * Apply the projection-space eye offset BEFORE gluPerspective. The vanilla anaglyph code
     * does {@code glTranslatef(-(field*2-1)*0.07f, 0, 0)} on the projection matrix right
     * before calling gluPerspective; we do the same.
     *
     * <p>{@code gluPerspective} is the canonical anchor in {@code setupCameraTransform} (same
     * one used by Angelica's existing {@code captureFov} mixin and the notfine clouds mixin),
     * so injecting BEFORE it is the most reliable hook.</p>
     */
    @Inject(
        method = "setupCameraTransform",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V",
            ordinal = 0,
            shift = At.Shift.BEFORE,
            remap = false
        )
    )
    private void angelica$applyStereoProjectionOffset(float partialTicks, int pass, CallbackInfo ci) {
        // Disabled: parallel-axis stereo (modelview-only) avoids the asymmetric-frustum
        // gap between eyes. Re-enable later if we move to proper toed-in / asymmetric stereo.
        if (true) return;
        if (!StereoState.INSTANCE.isActive()) return;
        float dx = StereoState.INSTANCE.getEyeOffset();
        if (dx == 0f) return;
        GLStateManager.glTranslatef(-dx * 1.09f, 0f, 0f);
    }

    /**
     * Apply the modelview-space eye offset AFTER gluPerspective. At this point vanilla code
     * has done {@code glMatrixMode(GL_MODELVIEW); glLoadIdentity();} and is about to call
     * the anaglyph translate (which we replace), then hurtCameraEffect, then setupViewBobbing.
     *
     * <p>Anchor: the call to {@code Minecraft.getMinecraft()} or
     * {@code mc.entityRenderer.getEntityRenderer()} — but those are unreliable across
     * remap settings. Instead, anchor on {@code EntityRenderer.hurtCameraEffect} which is
     * the next vanilla call after the modelview is loaded. Inject BEFORE it.</p>
     *
     * <p>If this anchor doesn't match in your environment (mod conflicts, remap issues),
     * switch to {@code @At("INVOKE")} on {@code Lnet/minecraft/client/Minecraft;thePlayer:Lnet/minecraft/client/entity/EntityClientPlayerMP;}
     * — but the hurtCameraEffect anchor is the cleanest semantic location.</p>
     */
    @Inject(
        method = "setupCameraTransform",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;hurtCameraEffect(F)V",
            ordinal = 0,
            shift = At.Shift.BEFORE
        )
    )
    private void angelica$applyStereoModelviewOffset(float partialTicks, int pass, CallbackInfo ci) {
        if (!StereoState.INSTANCE.isActive()) return;
        float dx = StereoState.INSTANCE.getEyeOffset();
        if (dx == 0f) return;
        // GL_MODELVIEW is the active matrix mode by this point in vanilla setupCameraTransform.
        // Defensively re-set it.
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glTranslatef(dx, 0f, 0f);
    }
}

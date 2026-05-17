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
 * Mimics vanilla 1.7.10's anaglyph per-eye camera shift in {@code EntityRenderer.setupCameraTransform}.
 * Runs at priority 999 so it lands before Angelica's matrix-capture mixin, letting Iris shader
 * uniforms ({@code gbufferModelView}, {@code gbufferProjection}) pick up the eye offset for free.
 *
 * <p>Vanilla anaglyph order, for sign reference:
 * <pre>
 *   PROJECTION: loadIdentity; glTranslatef(-(field*2-1)*0.07f, 0, 0); gluPerspective(...)
 *   MODELVIEW:  loadIdentity; glTranslatef((field*2-1)*0.1f, 0, 0); hurtCameraEffect(); ...
 * </pre>
 */
@Mixin(value = EntityRenderer.class, priority = 999)
public abstract class MixinEntityRenderer_StereoCamera {

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
        // === DISABLED: parallel-axis stereo avoids the asymmetric-frustum gap between eyes ===
        // Re-enable if/when we move to proper toed-in / asymmetric-frustum stereo.
        if (true) return; // intentional disable — see fence above
        // === END DISABLED ===
        if (!StereoState.INSTANCE.isActive()) return;
        float dx = StereoState.INSTANCE.getEyeOffset();
        if (dx == 0f) return;
        // GL_PROJECTION is the active matrix mode here. 1.09 ≈ vanilla's 0.07/0.064 ratio
        // of projection-offset to IPD; preserves vanilla's projection-vs-modelview balance.
        GLStateManager.glTranslatef(-dx * 1.09f, 0f, 0f);
    }

    /**
     * Modelview offset anchored on {@code hurtCameraEffect} — the first vanilla call after the
     * modelview is loaded with identity. {@code Minecraft.getMinecraft()} would be earlier but
     * is unreliable across remap settings.
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
        // Defensively re-set matrix mode; vanilla left it on GL_MODELVIEW but mods can perturb.
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glTranslatef(dx, 0f, 0f);
    }
}

package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Defers entity overlay rendering (auras, armor effects, etc) to after all entities have been drawn.
 * This lets us fix z-fighting between overlay's own coplanar faces by disabling the depth mask,
 * and ensures the overlay composites correctly on top of all opaque geometry.
 *
 * Used by both the charged creeper aura and the Wither armor overlay.
 *
 * The deferred render replays shouldRenderPass on the original renderer instance so that any
 * mod mixins targeting that method still fire during the deferred pass.
 */
public class DeferredEntityOverlay {

    @FunctionalInterface
    public interface ShouldRenderPassFn {
        int invoke(EntityLivingBase entity, int pass, float partialTick);
    }

    private static final List<DeferredEntry> deferred = new ArrayList<>();
    private static final FloatBuffer MATRIX_BUF = BufferUtils.createFloatBuffer(16);

    // Set by the shouldRenderPass inject, consumed by the doRender WrapOperation.
    @Getter
    private static boolean overlayPassActive = false;

    // Set during deferred flush to prevent re-deferring when shouldRenderPass is replayed.
    @Getter
    private static boolean replaying = false;

    // Pending context from the shouldRenderPass inject, consumed by deferRender.
    private static ShouldRenderPassFn pendingShouldRenderPass;
    private static RendererLivingEntity pendingRenderer;
    private static EntityLivingBase pendingEntity;
    private static float pendingPartialTick;

    /**
     * Called from shouldRenderPass HEAD injects. Marks that the upcoming renderPassModel.render()
     * should be deferred. If a previous mark was not consumed (shouldRenderPass returned <= 0,
     * so render() was never called).
     */
    public static void markOverlayPass(ShouldRenderPassFn shouldRenderPass, RendererLivingEntity renderer,
                                    EntityLivingBase entity, float partialTick) {
        overlayPassActive = true;
        pendingShouldRenderPass = shouldRenderPass;
        pendingRenderer = renderer;
        pendingEntity = entity;
        pendingPartialTick = partialTick;
    }

    public static void clearStaleOverlayFlag() {
        overlayPassActive = false;
    }

    /**
     * Called from the WrapOperation on renderPassModel.render() in doRender.
     * Captures the modelview matrix and animation params, skipping the actual render.
     */
    public static void deferRender(float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float headYaw, float headPitch, float scale) {
        overlayPassActive = false;

        float[] matrix = new float[16];
        MATRIX_BUF.clear();
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX_BUF);
        MATRIX_BUF.get(matrix);

        deferred.add(new DeferredEntry(
            pendingShouldRenderPass, pendingRenderer, pendingEntity, pendingPartialTick, matrix,
            limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale
        ));

        pendingShouldRenderPass = null;
        pendingRenderer = null;
        pendingEntity = null;
    }

    public static void clear() {
        deferred.clear();
        overlayPassActive = false;
        replaying = false;
    }

    public static void renderAll() {
        if (deferred.isEmpty()) return;

        for (DeferredEntry entry : deferred) {
            int entityId = EntityIdHelper.getEntityId(entry.entity);
            CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
            GbufferPrograms.beginEntities();
            try {
                renderOverlay(entry);
            } finally {
                CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
                GbufferPrograms.endEntities();
            }
        }
        deferred.clear();
    }

    private static void renderOverlay(DeferredEntry entry) {
        EntityLivingBase entity = entry.entity;

        // Restore the saved modelview matrix
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        boolean depthMaskDisabled = false;
        try {
            MATRIX_BUF.clear();
            MATRIX_BUF.put(entry.matrix);
            MATRIX_BUF.flip();
            GLStateManager.glLoadMatrix(MATRIX_BUF);

            // Replay shouldRenderPass pass 1
            replaying = true;
            int result = entry.shouldRenderPass.invoke(entity, 1, entry.partialTick);
            if (result > 0) {
                // Disable depth writes so coplanar faces don't z-fight each other
                GLStateManager.glDepthMask(false);
                depthMaskDisabled = true;

                // Disable backface culling so both sides of the overlay shell are visible
                GLStateManager.glDisable(GL11.GL_CULL_FACE);

                // Render using the model set by shouldRenderPass
                entry.renderer.renderPassModel.setLivingAnimations(entity,
                    entry.limbSwing, entry.limbSwingAmount, entry.partialTick);

                entry.renderer.renderPassModel.render(entity,
                    entry.limbSwing, entry.limbSwingAmount, entry.ageInTicks,
                    entry.headYaw, entry.headPitch, entry.scale);
            }

            // Replay shouldRenderPass pass 2
            entry.shouldRenderPass.invoke(entity, 2, entry.partialTick);
        } finally {
            replaying = false;
            if (depthMaskDisabled) {
                GLStateManager.glDepthMask(true);
                GLStateManager.glEnable(GL11.GL_CULL_FACE);
            }
            GLStateManager.glPopMatrix();
        }
    }

    private record DeferredEntry(ShouldRenderPassFn shouldRenderPass, RendererLivingEntity renderer,
                                 EntityLivingBase entity, float partialTick, float[] matrix, float limbSwing,
                                 float limbSwingAmount, float ageInTicks, float headYaw, float headPitch, float scale) {
    }
}

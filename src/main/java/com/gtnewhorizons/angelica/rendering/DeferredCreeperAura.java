package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.monster.EntityCreeper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Defers charged creeper aura rendering to after all entities have been drawn.
 * This fixes z-fighting in multiple places. Mainly the aura with itself (4 legs and body),
 * and aura intersecting the head.
 *
 * The deferred render replays shouldRenderPass on the original renderer instance so that any
 * mod mixins targeting that method still fire during the deferred pass.
 */
public class DeferredCreeperAura {

    @FunctionalInterface
    public interface ShouldRenderPassFn {
        int invoke(EntityCreeper entity, int pass, float partialTick);
    }

    private static final List<DeferredEntry> deferred = new ArrayList<>();
    private static final FloatBuffer MATRIX_BUF = BufferUtils.createFloatBuffer(16);

    /** Set by the shouldRenderPass inject, consumed by the doRender WrapOperation. */
    @Getter
    private static boolean auraPassActive = false;

    /** Set during deferred flush to prevent re-deferring when shouldRenderPass is replayed. */
    @Getter
    private static boolean replaying = false;

    /** Pending context from the shouldRenderPass inject, consumed by deferRender. */
    private static ShouldRenderPassFn pendingShouldRenderPass;
    private static RendererLivingEntity pendingRenderer;
    private static EntityCreeper pendingCreeper;
    private static float pendingPartialTick;

    public static void markAuraPass(ShouldRenderPassFn shouldRenderPass, RendererLivingEntity renderer,
                                    EntityCreeper creeper, float partialTick) {
        auraPassActive = true;
        pendingShouldRenderPass = shouldRenderPass;
        pendingRenderer = renderer;
        pendingCreeper = creeper;
        pendingPartialTick = partialTick;
    }

    /**
     * Called from the WrapOperation on renderPassModel.render() in doRender.
     * Captures the modelview matrix and animation params, skipping the actual render.
     */
    public static void deferRender(float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float headYaw, float headPitch, float scale) {
        auraPassActive = false;

        float[] matrix = new float[16];
        MATRIX_BUF.clear();
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX_BUF);
        MATRIX_BUF.get(matrix);

        deferred.add(new DeferredEntry(
            pendingShouldRenderPass, pendingRenderer, pendingCreeper, pendingPartialTick, matrix,
            limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale
        ));
    }

    public static void clear() {
        deferred.clear();
        auraPassActive = false;
    }

    public static void renderAll() {
        if (deferred.isEmpty()) return;

        for (DeferredEntry entry : deferred) {
            int entityId = EntityIdHelper.getEntityId(entry.creeper);
            CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
            GbufferPrograms.beginEntities();
            try {
                renderAura(entry);
            } finally {
                CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
                GbufferPrograms.endEntities();
            }
        }
        deferred.clear();
    }

    private static void renderAura(DeferredEntry entry) {
        EntityCreeper creeper = entry.creeper;

        // Restore the saved modelview matrix
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        MATRIX_BUF.clear();
        MATRIX_BUF.put(entry.matrix);
        MATRIX_BUF.flip();
        GLStateManager.glLoadMatrix(MATRIX_BUF);

        // Replay shouldRenderPass pass 1
        replaying = true;
        int result = entry.shouldRenderPass.invoke(creeper, 1, entry.partialTick);

        if (result > 0) {
            // Disable depth writes so coplanar aura faces don't z-fight each other
            GLStateManager.glDepthMask(false);

            // Render using the model set by shouldRenderPass
            entry.renderer.renderPassModel.setLivingAnimations(creeper,
                entry.limbSwing, entry.limbSwingAmount, entry.partialTick);

            entry.renderer.renderPassModel.render(creeper,
                entry.limbSwing, entry.limbSwingAmount, entry.ageInTicks,
                entry.headYaw, entry.headPitch, entry.scale);
        }

        // Replay shouldRenderPass pass 2
        entry.shouldRenderPass.invoke(creeper, 2, entry.partialTick);
        replaying = false;

        // Restore depth writes
        GLStateManager.glDepthMask(true);

        GLStateManager.glPopMatrix();
    }

    private record DeferredEntry(ShouldRenderPassFn shouldRenderPass, RendererLivingEntity renderer,
                                 EntityCreeper creeper, float partialTick, float[] matrix, float limbSwing,
                                 float limbSwingAmount, float ageInTicks, float headYaw, float headPitch, float scale) {
    }
}

package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.GameModeUtil;
import com.gtnewhorizons.angelica.compat.mojang.InteractionHand;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import com.gtnewhorizons.angelica.stereo.StereoState;
import lombok.Getter;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.layer.GbufferPrograms;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

import java.util.Map;



public class HandRenderer {
    public static final HandRenderer INSTANCE = new HandRenderer();

    private boolean ACTIVE;
    private @Getter boolean renderingSolid;
    public static final float DEPTH = 0.125F;

    private void setupGlState(RenderGlobal gameRenderer, Camera camera, float tickDelta) {
        final Minecraft mc = Minecraft.getMinecraft();

        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        // We need to scale the matrix by 0.125 so the hand doesn't clip through blocks.
        GLStateManager.glScalef(1.0F, 1.0F, DEPTH);

        // Stereoscopic projection-space offset for the held item: disabled. The vanilla anaglyph
        // value (0.07f) was tuned for red/cyan glasses' subtle 3D pop, not for SBS stereo fusion —
        // combined with the modelview offset below and the hand's close camera distance, it
        // produced hundreds of pixels of per-eye disparity that the eyes can't converge on.
        // Leaving the hand at the same projection in both eyes makes it render at screen depth.

        if (mc.entityRenderer.cameraZoom != 1.0D) {
            GLStateManager.glTranslatef((float) mc.entityRenderer.cameraYaw, (float) (-mc.entityRenderer.cameraPitch), 0.0F);
            GLStateManager.glScaled(mc.entityRenderer.cameraZoom, mc.entityRenderer.cameraZoom, 1.0D);
        }

        Project.gluPerspective(mc.entityRenderer.getFOVModifier(tickDelta, false), (float) mc.displayWidth / (float) mc.displayHeight, 0.05F, mc.entityRenderer.farPlaneDistance * 2.0F);


        if (mc.playerController.enableEverythingIsScrewedUpMode()) {
            GLStateManager.glScalef(1.0F, 2 / 3f, 1.0F);
        }

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();

        // Stereoscopic modelview offset for the held item: use the WORLD's per-eye offset
        // (getEyeOffset, ipd/2) rather than vanilla anaglyph's hand-specific 0.1f. That keeps
        // the hand's disparity consistent with the world geometry around it — so an item the
        // player holds appears at its correct stereo depth instead of "flat at screen depth" or
        // (with vanilla's exaggerated magnitudes) impossible-to-converge.
        if (StereoState.INSTANCE.isActive()) {
            final float dx = StereoState.INSTANCE.getEyeOffset();
            if (dx != 0f) {
                GLStateManager.glTranslatef(dx, 0.0F, 0.0F);
            }
        }

        mc.entityRenderer.hurtCameraEffect(tickDelta);

        if (mc.gameSettings.viewBobbing) {
            mc.entityRenderer.setupViewBobbing(tickDelta);
        }
    }

    private boolean canRender(Camera camera, RenderGlobal gameRenderer) {
        Minecraft mc = Minecraft.getMinecraft();

        return mc.entityRenderer.debugViewDirection <= 0 &&
               mc.gameSettings.thirdPersonView == 0 &&
               !camera.isSleeping() &&
               !mc.gameSettings.hideGUI &&
               !GameModeUtil.isSpectator() &&
               !mc.playerController.enableEverythingIsScrewedUpMode();
    }

    public boolean isHandTranslucent(InteractionHand hand) {
        ItemStack heldItem = hand.getItemInHand(Minecraft.getMinecraft().thePlayer);

        if (heldItem == null) return false;
        final Item item = heldItem.getItem();

        if (item instanceof ItemBlock itemBlock) {
            final Map<Block, BlockRenderLayer> blockTypeIds = BlockRenderingSettings.INSTANCE.getBlockTypeIds();
            return blockTypeIds != null && blockTypeIds.get(itemBlock.field_150939_a) == BlockRenderLayer.TRANSLUCENT;
        }

        return false;
    }

    public boolean isAnyHandTranslucent() {
        return isHandTranslucent(InteractionHand.MAIN_HAND) || isHandTranslucent(InteractionHand.OFF_HAND);
    }

    public void renderSolid(float tickDelta, Camera camera, RenderGlobal gameRenderer, WorldRenderingPipeline pipeline) {
        if (!canRender(camera, gameRenderer) || !IrisApi.getInstance().isShaderPackInUse()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();

        ACTIVE = true;

        pipeline.setPhase(WorldRenderingPhase.HAND_SOLID);

        GLStateManager.glPushMatrix();
        GLStateManager.glDepthMask(true); // actually write to the depth buffer, it's normally disabled at this point

        mc.mcProfiler.startSection("iris_hand");

        setupGlState(gameRenderer, camera, tickDelta);

        GbufferPrograms.setBlockEntityDefaults();

        renderingSolid = true;

        mc.entityRenderer.enableLightmap(tickDelta);
        mc.entityRenderer.itemRenderer.renderItemInFirstPerson(tickDelta);
        mc.entityRenderer.disableLightmap(tickDelta);

        GLStateManager.defaultBlendFunc();
        GLStateManager.glDepthMask(false);
        GLStateManager.glPopMatrix();

        mc.mcProfiler.endSection();

        resetProjectionMatrix();

        renderingSolid = false;

        pipeline.setPhase(WorldRenderingPhase.NONE);

        ACTIVE = false;
    }

    // TODO: RenderType
    public void renderTranslucent(float tickDelta, Camera camera, RenderGlobal gameRenderer, WorldRenderingPipeline pipeline) {
        if (!canRender(camera, gameRenderer) || !isAnyHandTranslucent() || !IrisApi.getInstance().isShaderPackInUse()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();

        ACTIVE = true;

        pipeline.setPhase(WorldRenderingPhase.HAND_TRANSLUCENT);

        GLStateManager.glPushMatrix();

        mc.mcProfiler.startSection("iris_hand_translucent");

        setupGlState(gameRenderer, camera, tickDelta);

        GbufferPrograms.setBlockEntityDefaults();

        mc.entityRenderer.enableLightmap(tickDelta);
        mc.entityRenderer.itemRenderer.renderItemInFirstPerson(tickDelta);
        mc.entityRenderer.disableLightmap(tickDelta);

        GLStateManager.glPopMatrix();

        resetProjectionMatrix();

        Minecraft.getMinecraft().mcProfiler.endSection();

        pipeline.setPhase(WorldRenderingPhase.NONE);

        ACTIVE = false;
    }

    private void resetProjectionMatrix() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMultMatrix(RenderingState.INSTANCE.getProjectionBuffer());
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    public boolean isActive() {
        return ACTIVE;
    }
}

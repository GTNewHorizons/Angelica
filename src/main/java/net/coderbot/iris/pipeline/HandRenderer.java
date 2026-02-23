package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.GameModeUtil;
import com.gtnewhorizons.angelica.compat.mojang.InteractionHand;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
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
    private boolean renderingSolid;
    public static final float DEPTH = 0.125F;

    private void setupGlState(RenderGlobal gameRenderer, Camera camera, float tickDelta) {
        final Minecraft mc = Minecraft.getMinecraft();

        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        // We need to scale the matrix by 0.125 so the hand doesn't clip through blocks.
        GLStateManager.glScalef(1.0F, 1.0F, DEPTH);

        // TODO: Anaglyph
        /*if (this.mc.gameSettings.anaglyph) {
            GLStateManager.glTranslatef((float) (-(anaglyphChannel * 2 - 1)) * 0.07F, 0.0F, 0.0F);
        }*/

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

        // TODO: Anaglyph
        /*if (mc.gameSettings.anaglyph) {
            GL11.glTranslatef((float) (anaglyphChannel * 2 - 1) * 0.1F, 0.0F, 0.0F);
        }*/

        mc.entityRenderer.hurtCameraEffect(tickDelta);

        if (mc.gameSettings.viewBobbing) {
            mc.entityRenderer.setupViewBobbing(tickDelta);
        }
    }

    private boolean canRender(Camera camera, RenderGlobal gameRenderer) {
        Minecraft mc = Minecraft.getMinecraft();

        return mc.entityRenderer.debugViewDirection <= 0 &&
               mc.gameSettings.thirdPersonView == 0 &&
               !camera.getEntity().isPlayerSleeping() &&
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

    public boolean isRenderingSolid() {
        return renderingSolid;
    }
}

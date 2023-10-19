package net.coderbot.iris.pipeline;

import net.coderbot.batchedentityrendering.impl.FullyBufferedMultiBufferSource;
import net.coderbot.iris.compat.mojang.Camera;
import net.coderbot.iris.compat.mojang.GameRenderer;
import net.coderbot.iris.compat.mojang.InteractionHand;
import net.coderbot.iris.compat.mojang.PoseStack;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import org.joml.Matrix4f;

public class HandRenderer {
	public static final HandRenderer INSTANCE = new HandRenderer();

	private boolean ACTIVE;
	private boolean renderingSolid;
	private final FullyBufferedMultiBufferSource bufferSource = new FullyBufferedMultiBufferSource();

	public static final float DEPTH = 0.125F;

	private void setupGlState(GameRenderer gameRenderer, Camera camera, PoseStack poseStack, float tickDelta) {
        final PoseStack.Pose pose = poseStack.last();

		// We need to scale the matrix by 0.125 so the hand doesn't clip through blocks.
        Matrix4f scaleMatrix = new Matrix4f().scale(1F, 1F, DEPTH);
        // TODO: ProjectionMatrix
//        scaleMatrix.multiply(gameRenderer.getProjectionMatrix(camera, tickDelta, false));
//		scaleMatrix.mul(projectionMatrix);
//        RenderSystem.matrixMode(5889);
//        RenderSystem.loadIdentity();
//        RenderSystem.multMatrix(arg);
//        RenderSystem.matrixMode(5888);

		pose.pose().identity();
        pose.normal().identity();

		gameRenderer.invokeBobHurt(poseStack, tickDelta);

		if (Minecraft.getMinecraft().gameSettings.viewBobbing) {
			gameRenderer.invokeBobView(poseStack, tickDelta);
		}
	}

	private boolean canRender(Camera camera, GameRenderer gameRenderer) {
		return !(!gameRenderer.getRenderHand()
				|| camera.isDetached()
					|| !(camera.getEntity() instanceof EntityPlayer)
						|| gameRenderer.getPanoramicMode()
							|| Minecraft.getMinecraft().gameSettings.hideGUI
								|| (camera.getEntity() instanceof EntityLiving && ((EntityLiving)camera.getEntity()).isPlayerSleeping())
                                    // TODO: SPECTATOR
									/*|| Minecraft.getMinecraft().gameMode.getPlayerMode() == GameType.SPECTATOR*/);
	}

	public boolean isHandTranslucent(InteractionHand hand) {
        // TODO: Offhand
//        Item item = Minecraft.getMinecraft().thePlayer.getItemBySlot(hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND).getItem();
        Item item = Minecraft.getMinecraft().thePlayer.getHeldItem().getItem();

		if (item instanceof ItemBlock itemBlock) {
            // TODO: RenderType
//			return ItemBlockRenderTypes.getChunkRenderType(itemBlock.getBlock().defaultBlockState()) == RenderType.translucent();
            return false;
		}

		return false;
	}

	public boolean isAnyHandTranslucent() {
		return isHandTranslucent(InteractionHand.MAIN_HAND) || isHandTranslucent(InteractionHand.OFF_HAND);
	}

	public void renderSolid(PoseStack poseStack, float tickDelta, Camera camera, GameRenderer gameRenderer, WorldRenderingPipeline pipeline) {
		if (!canRender(camera, gameRenderer) || !IrisApi.getInstance().isShaderPackInUse()) {
			return;
		}

		ACTIVE = true;

		pipeline.setPhase(WorldRenderingPhase.HAND_SOLID);

		poseStack.pushPose();

		Minecraft.getMinecraft().mcProfiler.startSection("iris_hand");

		setupGlState(gameRenderer, camera, poseStack, tickDelta);

		renderingSolid = true;
        // TODO: Hand
//		Minecraft.getMinecraft().getItemInHandRenderer().renderHandsWithItems(tickDelta, poseStack, bufferSource, Minecraft.getMinecraft().thePlayer, Minecraft.getMinecraft().getEntityRenderDispatcher().getPackedLightCoords(camera.getEntity(), tickDelta));

		Minecraft.getMinecraft().mcProfiler.endSection();

        // TODO: ProjectionMatrix
//		gameRenderer.resetProjectionMatrix(CapturedRenderingState.INSTANCE.getGbufferProjection());

		poseStack.popPose();

		bufferSource.endBatch();

		renderingSolid = false;

		pipeline.setPhase(WorldRenderingPhase.NONE);

		ACTIVE = false;
	}

	public void renderTranslucent(PoseStack poseStack, float tickDelta, Camera camera, GameRenderer gameRenderer, WorldRenderingPipeline pipeline) {
		if (!canRender(camera, gameRenderer) || !isAnyHandTranslucent() || !IrisApi.getInstance().isShaderPackInUse()) {
			return;
		}

		ACTIVE = true;

		pipeline.setPhase(WorldRenderingPhase.HAND_TRANSLUCENT);

		poseStack.pushPose();

		Minecraft.getMinecraft().mcProfiler.startSection("iris_hand_translucent");

		setupGlState(gameRenderer, camera, poseStack, tickDelta);

        // TODO: Hand
//		Minecraft.getMinecraft().getItemInHandRenderer().renderHandsWithItems(tickDelta, poseStack, bufferSource, Minecraft.getMinecraft().thePlayer, Minecraft.getMinecraft().getEntityRenderDispatcher().getPackedLightCoords(camera.getEntity(), tickDelta));

		poseStack.popPose();

		Minecraft.getMinecraft().mcProfiler.endSection();

        // TODO: ProjectionMatrix
//		gameRenderer.resetProjectionMatrix(CapturedRenderingState.INSTANCE.getGbufferProjection());

		bufferSource.endBatch();

		pipeline.setPhase(WorldRenderingPhase.NONE);

		ACTIVE = false;
	}

	public boolean isActive() {
		return ACTIVE;
	}

	public boolean isRenderingSolid() {
		return renderingSolid;
	}

	public FullyBufferedMultiBufferSource getBufferSource() {
		return bufferSource;
	}
}

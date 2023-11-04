package net.coderbot.iris.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import com.gtnewhorizons.angelica.compat.mojang.InteractionHand;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.shaderpack.IdMap;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.api.v0.item.IrisItemLightProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public final class IdMapUniforms {

	private IdMapUniforms() {
	}

	public static void addIdMapUniforms(FrameUpdateNotifier notifier, DynamicUniformHolder uniforms, IdMap idMap, boolean isOldHandLight) {
		HeldItemSupplier mainHandSupplier = new HeldItemSupplier(InteractionHand.MAIN_HAND, idMap.getItemIdMap(), isOldHandLight);
		HeldItemSupplier offHandSupplier = new HeldItemSupplier(InteractionHand.OFF_HAND, idMap.getItemIdMap(), false);
		notifier.addListener(mainHandSupplier::update);
		notifier.addListener(offHandSupplier::update);

		uniforms
			.uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId", mainHandSupplier::getIntID)
			.uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId2", offHandSupplier::getIntID)
			.uniform1i(PER_FRAME, "heldBlockLightValue", mainHandSupplier::getLightValue)
			.uniform1i(PER_FRAME, "heldBlockLightValue2", offHandSupplier::getLightValue);
		// TODO: Figure out API.
			//.uniformVanilla3f(PER_FRAME, "heldBlockLightColor", mainHandSupplier::getLightColor)
			//.uniformVanilla3f(PER_FRAME, "heldBlockLightColor2", offHandSupplier::getLightColor);

		uniforms.uniform1i("entityId", CapturedRenderingState.INSTANCE::getCurrentRenderedEntity,
				CapturedRenderingState.INSTANCE.getEntityIdNotifier());

		uniforms.uniform1i("blockEntityId", CapturedRenderingState.INSTANCE::getCurrentRenderedBlockEntity,
				CapturedRenderingState.INSTANCE.getBlockEntityIdNotifier());
	}

	/**
	 * Provides the currently held item, and it's light value, in the given hand as a uniform. Uses the item.properties ID map to map the item
	 * to an integer, and the old hand light value to map offhand to main hand.
	 */
	private static class HeldItemSupplier {
		private final InteractionHand hand;
		private final Object2IntFunction<NamespacedId> itemIdMap;
		private final boolean applyOldHandLight;
		private int intID;
		private int lightValue;
		private Vector3f lightColor;

		HeldItemSupplier(InteractionHand hand, Object2IntFunction<NamespacedId> itemIdMap, boolean shouldApplyOldHandLight) {
			this.hand = hand;
			this.itemIdMap = itemIdMap;
			this.applyOldHandLight = shouldApplyOldHandLight && hand == InteractionHand.MAIN_HAND;
		}

		private void invalidate() {
			intID = -1;
			lightValue = 0;
			lightColor = IrisItemLightProvider.DEFAULT_LIGHT_COLOR;
		}

		public void update() {
			EntityPlayer player = Minecraft.getMinecraft().thePlayer;

			if (player == null) {
				// Not valid when the player doesn't exist
				invalidate();
				return;
			}
//			ItemStack heldStack = player.getItemInHand(hand);
            // TODO: Offhand
			ItemStack heldStack = player.getHeldItem();

			if (heldStack == null) {
				invalidate();
				return;
			}

			Item heldItem = heldStack.getItem();

			if (heldItem == null) {
				invalidate();
				return;
			}

			ResourceLocation heldItemId = new ResourceLocation(Item.itemRegistry.getNameForObject(heldItem));
			intID = itemIdMap.applyAsInt(new NamespacedId(heldItemId.getResourceDomain(), heldItemId.getResourcePath()));

			IrisItemLightProvider lightProvider = (IrisItemLightProvider) heldItem;
			lightValue = lightProvider.getLightEmission(Minecraft.getMinecraft().thePlayer, heldStack);

			if (applyOldHandLight) {
				lightProvider = applyOldHandLighting(player, lightProvider);
			}

			lightColor = lightProvider.getLightColor(Minecraft.getMinecraft().thePlayer, heldStack);
		}

		private IrisItemLightProvider applyOldHandLighting(@NotNull EntityPlayer player, IrisItemLightProvider existing) {
            // TODO: Offhand
//			ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
			ItemStack offHandStack = null;

			if (offHandStack == null) {
				return existing;
			}

			Item offHandItem = offHandStack.getItem();

			if (offHandItem == null) {
				return existing;
			}

			IrisItemLightProvider lightProvider = (IrisItemLightProvider) offHandItem;
			int newEmission = lightProvider.getLightEmission(Minecraft.getMinecraft().thePlayer,  offHandStack);

			if (lightValue < newEmission) {
				lightValue = newEmission;
				return lightProvider;
			}

			return existing;
		}

		public int getIntID() {
			return intID;
		}

		public int getLightValue() {
			return lightValue;
		}

		public Vector3f getLightColor() {
			return lightColor;
		}
	}
}

package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.compat.etfuturum.EtFuturumCompat;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.GameModeUtil;
import com.gtnewhorizons.angelica.render.CloudRenderer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public class IrisExclusiveUniforms {
	// Reusable vectors to avoid allocations every frame
	private static final Vector3d eyePositionCache = new Vector3d();
	private static final Vector3d relativeEyePositionCache = new Vector3d();
	private static final Vector3d playerLookCache = new Vector3d();
	private static final Vector3d playerBodyCache = new Vector3d();
	private static final Vector3d vehicleLookCache = new Vector3d();
	private static final Vector3d relativeVehiclePositionCache = new Vector3d();
	private static final Vector3f selectedBlockPosCache = new Vector3f();
	private static final Vector4f lightningBoltPositionCache = new Vector4f();
	private static final Vector4f ZERO_VECTOR_4f = new Vector4f(0, 0, 0, 0);

	private static int cachedSelectedFrame = -1;
	private static int cachedSelectedBlockId;

	public static void addIrisExclusiveUniforms(UniformHolder uniforms) {
		WorldInfoUniforms.addWorldInfoUniforms(uniforms);

		//All Iris-exclusive uniforms (uniforms which do not exist in either OptiFine or ShadersMod) should be registered here.
		uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "thunderStrength", IrisExclusiveUniforms::getThunderStrength);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHealth", IrisExclusiveUniforms::getCurrentHealth);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHealth", IrisExclusiveUniforms::getMaxHealth);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHunger", IrisExclusiveUniforms::getCurrentHunger);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHunger", () -> 20);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerAir", IrisExclusiveUniforms::getCurrentAir);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerAir", IrisExclusiveUniforms::getMaxAir);
		uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "firstPersonCamera", IrisExclusiveUniforms::isFirstPersonCamera);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isSpectator", IrisExclusiveUniforms::isSpectator);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isRightHanded", () -> true); // 1.7.10 doesn't support left-handed mode
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "eyePosition", IrisExclusiveUniforms::getEyePosition);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "relativeEyePosition", IrisExclusiveUniforms::getRelativeEyePosition);
		uniforms.uniform4f(UniformUpdateFrequency.PER_TICK, "lightningBoltPosition", IrisExclusiveUniforms::getLightningBoltPosition);
		uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "cloudTime", IrisExclusiveUniforms::getCloudTime);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "feetInWater", IrisExclusiveUniforms::isFeetInWater);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isRiding", IrisExclusiveUniforms::isRiding);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "vehicleInWater", IrisExclusiveUniforms::isVehicleInWater);
		uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "vehicleId", IrisExclusiveUniforms::getVehicleId);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "vehicleLookVector", IrisExclusiveUniforms::getVehicleLookVector);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "relativeVehiclePosition", IrisExclusiveUniforms::getRelativeVehiclePosition);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerArmor", IrisExclusiveUniforms::getCurrentArmor);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerArmor", () -> 50);
		uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "currentSelectedBlockId", IrisExclusiveUniforms::getCurrentSelectedBlockId);
		uniforms.uniform3f(UniformUpdateFrequency.PER_FRAME, "currentSelectedBlockPos", IrisExclusiveUniforms::getCurrentSelectedBlockPos);
		uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "seaLevel", IrisExclusiveUniforms::getSeaLevel);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "playerLookVector", IrisExclusiveUniforms::getPlayerLookVector);
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "playerBodyVector", IrisExclusiveUniforms::getPlayerBodyVector);
		// Target the Elytra from EFR
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isElytraFlying", IrisExclusiveUniforms::isElytraFlying);
	}

	private static float getCloudTime() {
		final WorldClient level = Minecraft.getMinecraft().theWorld;
		if (level == null) return 0f;
		final long cycle = (long) CloudRenderer.getCloudTextureWidth() * 400L * CloudRenderer.getScaleMult();
		final long t = level.getTotalWorldTime() % cycle;
		final float partial = Minecraft.getMinecraft().timer.renderPartialTicks;
		return (t + partial) * 0.03F;
	}

	private static float getThunderStrength() {
		// Note: Ensure this is in the range of 0 to 1 - some custom servers send out of range values.
		return Math.clamp(0.0F, 1.0F, Minecraft.getMinecraft().theWorld.thunderingStrength);
	}

	private static float getCurrentHealth() {
		if (Minecraft.getMinecraft().thePlayer == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

		return Minecraft.getMinecraft().thePlayer.getHealth() / Minecraft.getMinecraft().thePlayer.getMaxHealth();
	}

	private static float getCurrentHunger() {
		if (Minecraft.getMinecraft().thePlayer == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

		return Minecraft.getMinecraft().thePlayer.getFoodStats().getFoodLevel() / 20f;
	}

	private static float getCurrentAir() {
		if (Minecraft.getMinecraft().thePlayer == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

		return (float) Minecraft.getMinecraft().thePlayer.getAir() / (float) Minecraft.getMinecraft().thePlayer.getAir();
	}

	private static float getMaxAir() {
		if (Minecraft.getMinecraft().thePlayer == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

//		return Minecraft.getMinecraft().thePlayer.getMaxAirSupply();
		return 300.0F;
	}

	private static float getMaxHealth() {
		if (Minecraft.getMinecraft().thePlayer == null || !Minecraft.getMinecraft().playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}

		return Minecraft.getMinecraft().thePlayer.getMaxHealth();
	}

	private static boolean isFirstPersonCamera() {
		// If camera type is not explicitly third-person, assume it's first-person.
        return !Camera.INSTANCE.isThirdPerson();
	}

	private static boolean isSpectator() {
		return GameModeUtil.isSpectator();
	}

	private static Vector3d getEyePosition() {
        final EntityLivingBase eye = Minecraft.getMinecraft().renderViewEntity;
        return eyePositionCache.set(eye.posX, eye.posY + eye.getEyeHeight(), eye.posZ);
	}

	private static Vector3d getRelativeEyePosition() {
		final Vector3dc cameraPos = CameraUniforms.getUnshiftedCameraPosition();
		final Vector3d eyePos = getEyePosition();
		return relativeEyePositionCache.set(eyePos).sub(cameraPos);
	}

	private static Vector4f getLightningBoltPosition() {
		if (Minecraft.getMinecraft().theWorld != null) {
			final List<Entity> weatherEffects = Minecraft.getMinecraft().theWorld.weatherEffects;
			for (Entity entity : weatherEffects) {
				if (entity instanceof EntityLightningBolt bolt) {
                    final Vector3dc cameraPos = CameraUniforms.getUnshiftedCameraPosition();
					return lightningBoltPositionCache.set(
						(float)(bolt.posX - cameraPos.x()),
						(float)(bolt.posY - cameraPos.y()),
						(float)(bolt.posZ - cameraPos.z()),
						1.0f
					);
				}
			}
		}
		return ZERO_VECTOR_4f;
	}

	private static EntityLivingBase cameraEntity() {
		return Minecraft.getMinecraft().renderViewEntity;
	}

	private static Vector3d forwardVector(Vector3d dest, float yawDeg, float pitchDeg) {
		final double yr = -yawDeg * 0.017453292 - Math.PI;
		final double pr = -pitchDeg * 0.017453292;
		final double cy = Math.cos(yr);
		final double sy = Math.sin(yr);
		final double cp = -Math.cos(pr);
		final double sp = Math.sin(pr);
		return dest.set(sy * cp, sp, cy * cp);
	}

	private static boolean isFeetInWater() {
		final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		return player != null && player.isInWater();
	}

	private static boolean isElytraFlying() {
		return EtFuturumCompat.isElytraFlying(Minecraft.getMinecraft().thePlayer);
	}

	private static boolean isRiding() {
		final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		return player != null && player.ridingEntity != null;
	}

	private static boolean isVehicleInWater() {
		final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		return player != null && player.ridingEntity != null && player.ridingEntity.isInWater();
	}

	private static int getVehicleId() {
		final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null || player.ridingEntity == null) return 0;
		final int id = EntityIdHelper.getEntityId(player.ridingEntity);
		return Math.max(id, 0);
	}

	private static Vector3d getVehicleLookVector() {
		final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null || player.ridingEntity == null) return vehicleLookCache.set(0, 0, 0);
		final Entity v = player.ridingEntity;
		return forwardVector(vehicleLookCache, v.rotationYaw, v.rotationPitch);
	}

	private static Vector3d getRelativeVehiclePosition() {
		final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null || player.ridingEntity == null) return relativeVehiclePositionCache.set(0, 0, 0);
		final Entity v = player.ridingEntity;
		final float delta = CapturedRenderingState.INSTANCE.getTickDelta();
		final double vx = v.prevPosX + (v.posX - v.prevPosX) * delta;
		final double vy = v.prevPosY + (v.posY - v.prevPosY) * delta;
		final double vz = v.prevPosZ + (v.posZ - v.prevPosZ) * delta;
		// Match Angelica's relativeEyePosition convention (entity - camera).
		final Vector3dc cam = CameraUniforms.getUnshiftedCameraPosition();
		return relativeVehiclePositionCache.set(vx - cam.x(), vy - cam.y(), vz - cam.z());
	}

	private static float getCurrentArmor() {
		final Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || !mc.playerController.gameIsSurvivalOrAdventure()) {
			return -1;
		}
		return mc.thePlayer.getTotalArmorValue() / 50.0f;
	}

	private static Vector3d getPlayerLookVector() {
		final EntityLivingBase e = cameraEntity();
		if (e == null) return playerLookCache.set(0, 0, 0);
		final float delta = CapturedRenderingState.INSTANCE.getTickDelta();
		final float yaw = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * delta;
		final float pitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * delta;
		return forwardVector(playerLookCache, yaw, pitch);
	}

	private static Vector3d getPlayerBodyVector() {
		final EntityLivingBase e = cameraEntity();
		if (e == null) return playerBodyCache.set(0, 0, 0);
		return forwardVector(playerBodyCache, e.renderYawOffset, 0.0f);
	}

	private static int getSeaLevel() {
		// 1.7.10 has no per-world sea level accessor, best to guess 63?
		return 63;
	}

	private static void updateSelectedBlock() {
		final int frame = SystemTimeUniforms.COUNTER.getAsInt();
		if (frame == cachedSelectedFrame) {
			return;
		}
		cachedSelectedFrame = frame;

		final Minecraft mc = Minecraft.getMinecraft();
		final MovingObjectPosition hit = mc.objectMouseOver;
		if (mc.theWorld == null || hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
			cachedSelectedBlockId = 0;
			selectedBlockPosCache.set(-256.0f, -256.0f, -256.0f);
			return;
		}

		final Vector3dc cam = CameraUniforms.getUnshiftedCameraPosition();
		selectedBlockPosCache.set(
			(float) ((hit.blockX + 0.5) - cam.x()),
			(float) ((hit.blockY + 0.5) - cam.y()),
			(float) ((hit.blockZ + 0.5) - cam.z()));

		final Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
		if (block == null || block.isAir(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ)) {
			cachedSelectedBlockId = 0;
			return;
		}
		final Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches = BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
		if (blockMetaMatches == null) {
			cachedSelectedBlockId = 0;
			return;
		}
		final Int2IntMap metaMap = blockMetaMatches.get(block);
		if (metaMap == null) {
			cachedSelectedBlockId = 0;
			return;
		}
		final int meta = mc.theWorld.getBlockMetadata(hit.blockX, hit.blockY, hit.blockZ);
		cachedSelectedBlockId = Math.max(BlockMaterialMapping.resolveId(metaMap, meta), 0);
	}

	private static int getCurrentSelectedBlockId() {
		updateSelectedBlock();
		return cachedSelectedBlockId;
	}

	private static Vector3f getCurrentSelectedBlockPos() {
		updateSelectedBlock();
		return selectedBlockPosCache;
	}

	private static int worldHeight() {
		final WorldClient level = Minecraft.getMinecraft().theWorld;
		return (level != null && level.provider != null) ? level.provider.getHeight() : 256;
	}

	public static class WorldInfoUniforms {
		public static void addWorldInfoUniforms(UniformHolder uniforms) {
			uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "bedrockLevel", () -> 0);
            uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "cloudHeight", () -> {
                final WorldClient level = Minecraft.getMinecraft().theWorld;
                if (level != null && level.provider != null) {
                    return level.provider.getCloudHeight();
                } else {
                    return 192.0;
                }
            });
			uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "heightLimit", IrisExclusiveUniforms::worldHeight);
			uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "logicalHeightLimit", IrisExclusiveUniforms::worldHeight);
			uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasCeiling", () -> {
                final WorldClient level = Minecraft.getMinecraft().theWorld;
				if (level != null && level.provider != null) {
					return level.provider.hasNoSky;
				} else {
					return false;
				}
			});
			uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasSkylight", () -> {
                final WorldClient level = Minecraft.getMinecraft().theWorld;
				if (level != null && level.provider != null) {
					return !level.provider.hasNoSky;
				} else {
					return true;
				}
			});
			uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "ambientLight", () -> {
                final WorldClient level = Minecraft.getMinecraft().theWorld;
				if (level != null && level.provider != null) {
                    return level.provider.lightBrightnessTable[0];
				} else {
					return 0f;
				}
			});

		}
	}
}

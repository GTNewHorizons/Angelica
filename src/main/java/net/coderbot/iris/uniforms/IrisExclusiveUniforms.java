package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.GameModeUtil;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4f;

import java.util.List;

public class IrisExclusiveUniforms {
	// Reusable vectors to avoid allocations every frame
	private static final Vector3d eyePositionCache = new Vector3d();
	private static final Vector3d relativeEyePositionCache = new Vector3d();
	private static final Vector4f lightningBoltPositionCache = new Vector4f();
	private static final Vector4f ZERO_VECTOR_4f = new Vector4f(0, 0, 0, 0);

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

	public static class WorldInfoUniforms {
		public static void addWorldInfoUniforms(UniformHolder uniforms) {
			final WorldClient level = Minecraft.getMinecraft().theWorld;
			uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "bedrockLevel", () -> 0);
            uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "cloudHeight", () -> {
                if (level != null && level.provider != null) {
                    return level.provider.getCloudHeight();
                } else {
                    return 192.0;
                }
            });
			uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "heightLimit", () -> {
                if (level != null && level.provider != null) {
                    return level.provider.getHeight();
				} else {
					return 256;
				}
			});
			uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasCeiling", () -> {
				if (level != null && level.provider != null) {
					return level.provider.hasNoSky;
				} else {
					return false;
				}
			});
			uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasSkylight", () -> {
				if (level != null && level.provider != null) {
					return !level.provider.hasNoSky;
				} else {
					return true;
				}
			});
			uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "ambientLight", () -> {
				if (level != null && level.provider != null) {
                    return level.provider.lightBrightnessTable[0];
				} else {
					return 0f;
				}
			});

		}
	}
}

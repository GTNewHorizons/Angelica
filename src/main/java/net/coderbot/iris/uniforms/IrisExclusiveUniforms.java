package net.coderbot.iris.uniforms;

import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.EntityLivingBase;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector4f;

public class IrisExclusiveUniforms {
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
		uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "eyePosition", IrisExclusiveUniforms::getEyePosition);
        // TODO: Iris Shaders
//		Vector4f zero = new Vector4f(0, 0, 0, 0);
//		uniforms.uniform4f(UniformUpdateFrequency.PER_TICK, "lightningBoltPosition", () -> {
//			if (Minecraft.getMinecraft().theWorld != null) {
//				return StreamSupport.stream(Minecraft.getMinecraft().theWorld.entitiesForRendering().spliterator(), false).filter(bolt -> bolt instanceof LightningBolt).findAny().map(bolt -> {
//					Vector3d unshiftedCameraPosition = CameraUniforms.getUnshiftedCameraPosition();
//					Vec3 vec3 = bolt.getPosition(Minecraft.getMinecraft().getDeltaFrameTime());
//					return new Vector4f((float) (vec3.x - unshiftedCameraPosition.x), (float) (vec3.y - unshiftedCameraPosition.y), (float) (vec3.z - unshiftedCameraPosition.z), 1);
//				}).orElse(zero);
//			} else {
//				return zero;
//			}
//		});
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
        return (Minecraft.getMinecraft().gameSettings.thirdPersonView == 1);
	}

	private static boolean isSpectator() {
        final PlayerControllerMP controller = Minecraft.getMinecraft().playerController;
        if(controller == null)
            return false;
        return controller.currentGameType.getID() == 3;
	}

	private static Vector3d getEyePosition() {
//		Objects.requireNonNull(Minecraft.getMinecraft().getCameraEntity());
//		return new Vector3d(Minecraft.getMinecraft().getCameraEntity().getX(), Minecraft.getMinecraft().getCameraEntity().getEyeY(), Minecraft.getMinecraft().getCameraEntity().getZ());
        final EntityLivingBase eye = Minecraft.getMinecraft().renderViewEntity;
        return new Vector3d(eye.posX, eye.posY, eye.posZ);
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

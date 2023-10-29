package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.client.Shaders;
import com.gtnewhorizons.angelica.mixins.early.accessors.EntityRendererAccessor;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.shaderpack.IdMap;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.uniforms.transforms.SmoothedFloat;
import net.coderbot.iris.uniforms.transforms.SmoothedVec2f;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.Vec3;
import org.joml.Math;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.joml.Vector4i;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.ONCE;
import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;
import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_TICK;

public final class CommonUniforms {
	private static final Minecraft client = Minecraft.getMinecraft();
	private static final Vector2i ZERO_VECTOR_2i = new Vector2i();
	private static final Vector4i ZERO_VECTOR_4i = new Vector4i(0, 0, 0, 0);
	private static final Vector3d ZERO_VECTOR_3d = new Vector3d();

	private CommonUniforms() {
		// no construction allowed
	}

	// Needs to use a LocationalUniformHolder as we need it for the common uniforms
	public static void addCommonUniforms(DynamicUniformHolder uniforms, IdMap idMap, PackDirectives directives, FrameUpdateNotifier updateNotifier) {
		CameraUniforms.addCameraUniforms(uniforms, updateNotifier);
		ViewportUniforms.addViewportUniforms(uniforms);
		WorldTimeUniforms.addWorldTimeUniforms(uniforms);
		SystemTimeUniforms.addSystemTimeUniforms(uniforms);
		new CelestialUniforms(directives.getSunPathRotation()).addCelestialUniforms(uniforms);
		IdMapUniforms.addIdMapUniforms(updateNotifier, uniforms, idMap, directives.isOldHandLight());
		IrisExclusiveUniforms.addIrisExclusiveUniforms(uniforms);
		MatrixUniforms.addMatrixUniforms(uniforms, directives);
		HardcodedCustomUniforms.addHardcodedCustomUniforms(uniforms, updateNotifier);
		FogUniforms.addFogUniforms(uniforms);

		// TODO: OptiFine doesn't think that atlasSize is a "dynamic" uniform,
		//       but we do. How will custom uniforms depending on atlasSize work?
		uniforms.uniform2i("atlasSize", () -> {
            return new Vector2i(Shaders.atlasSizeX, Shaders.atlasSizeY);
//			int glId = GlStateManagerAccessor.getTEXTURES()[0].binding;
//
//			AbstractTexture texture = TextureTracker.INSTANCE.getTexture(glId);
//			if (texture instanceof TextureAtlas) {
//				TextureInfo info = TextureInfoCache.INSTANCE.getInfo(glId);
//				return new Vector2i(info.getWidth(), info.getHeight());
//			}
//
//			return ZERO_VECTOR_2i;
		}, StateUpdateNotifiers.bindTextureNotifier);

        // TODO: gTextureSize
//		uniforms.uniform2i("gtextureSize", () -> {
//			int glId = GlStateManagerAccessor.getTEXTURES()[0].binding;
//
//			TextureInfo info = TextureInfoCache.INSTANCE.getInfo(glId);
//			return new Vector2i(info.getWidth(), info.getHeight());
//
//		}, StateUpdateNotifiers.bindTextureNotifier);

		uniforms.uniform4i("blendFunc", () -> {
            if(CapturedRenderingState.INSTANCE.isBlendEnabled()) {
                return CapturedRenderingState.INSTANCE.getBlendFunc();
            }
            return ZERO_VECTOR_4i;
		}, StateUpdateNotifiers.blendFuncNotifier);

		uniforms.uniform1i("renderStage", () -> GbufferPrograms.getCurrentPhase().ordinal(), StateUpdateNotifiers.phaseChangeNotifier);

		CommonUniforms.generalCommonUniforms(uniforms, updateNotifier, directives);
	}

	public static void generalCommonUniforms(UniformHolder uniforms, FrameUpdateNotifier updateNotifier, PackDirectives directives) {
		ExternallyManagedUniforms.addExternallyManagedUniforms116(uniforms);

		SmoothedVec2f eyeBrightnessSmooth = new SmoothedVec2f(directives.getEyeBrightnessHalfLife(), directives.getEyeBrightnessHalfLife(), CommonUniforms::getEyeBrightness, updateNotifier);

        uniforms
			.uniform1b(PER_FRAME, "hideGUI", () -> client.gameSettings.hideGUI)
			.uniform1f(PER_FRAME, "eyeAltitude", Shaders::getEyePosY) // Objects.requireNonNull(client.getCameraEntity()).getEyeY())
			.uniform1i(PER_FRAME, "isEyeInWater", CommonUniforms::isEyeInWater)
			.uniform1f(PER_FRAME, "blindness", CommonUniforms::getBlindness)
			.uniform1f(PER_FRAME, "nightVision", CommonUniforms::getNightVision)
            .uniform1b(PER_FRAME, "is_sneaking", CommonUniforms::isSneaking)
            .uniform1b(PER_FRAME, "is_sprinting", CommonUniforms::isSprinting)
            .uniform1b(PER_FRAME, "is_hurt", CommonUniforms::isHurt)
            .uniform1b(PER_FRAME, "is_invisible", CommonUniforms::isInvisible)
            .uniform1b(PER_FRAME, "is_burning", CommonUniforms::isBurning)
            .uniform1b(PER_FRAME, "is_on_ground", CommonUniforms::isOnGround)
			// TODO: Do we need to clamp this to avoid fullbright breaking shaders? Or should shaders be able to detect
			//       that the player is trying to turn on fullbright?
			.uniform1f(PER_FRAME, "screenBrightness", () -> client.gameSettings.gammaSetting)
			// just a dummy value for shaders where entityColor isn't supplied through a vertex attribute (and thus is
			// not available) - suppresses warnings. See AttributeShaderTransformer for the actual entityColor code.
			.uniform4f(ONCE, "entityColor", Vector4f::new)
			.uniform1f(PER_TICK, "playerMood", CommonUniforms::getPlayerMood)
			.uniform2i(PER_FRAME, "eyeBrightness", CommonUniforms::getEyeBrightness)
			.uniform2i(PER_FRAME, "eyeBrightnessSmooth", () -> {
				Vector2f smoothed = eyeBrightnessSmooth.get();
				return new Vector2i((int) smoothed.x(),(int) smoothed.y());
			})
			.uniform1f(PER_TICK, "rainStrength", CommonUniforms::getRainStrength)
			.uniform1f(PER_TICK, "wetness", new SmoothedFloat(directives.getWetnessHalfLife(), directives.getDrynessHalfLife(), CommonUniforms::getRainStrength, updateNotifier))
			.uniform3d(PER_FRAME, "skyColor", CommonUniforms::getSkyColor)
			.uniform3d(PER_FRAME, "fogColor", CapturedRenderingState.INSTANCE::getFogColor);
	}

    private static boolean isOnGround() {
        return client.thePlayer != null && client.thePlayer.onGround;
    }

    private static boolean isHurt() {
        // Do not use isHurt, that's not what we want!
        return (client.thePlayer != null &&  client.thePlayer.hurtTime > 0);
    }

    private static boolean isInvisible() {
        return (client.thePlayer != null &&  client.thePlayer.isInvisible());
    }

    private static boolean isBurning() {
        // todo: thePlayer.fire > 0 && !thePlayer.fireResistance
        return false;
        //        if (client.thePlayer != null) {
        //            return client.thePlayer.isOnFire();
        //        } else {
        //            return false;
        //        }
    }

    private static boolean isSneaking() {
        return (client.thePlayer != null && client.thePlayer.isSneaking());
    }

    private static boolean isSprinting() {
        return (client.thePlayer != null && client.thePlayer.isSprinting());
    }

	private static Vector3d getSkyColor() {
        if (client.theWorld == null || client.renderViewEntity == null) {
			return ZERO_VECTOR_3d;
		}
        final Vec3 skyColor = client.theWorld.getSkyColor(client.renderViewEntity, CapturedRenderingState.INSTANCE.getTickDelta());
        return new Vector3d(skyColor.xCoord, skyColor.yCoord, skyColor.zCoord);
	}

	static float getBlindness() {
        EntityLivingBase cameraEntity = client.renderViewEntity;

        if (cameraEntity instanceof EntityLiving livingEntity && livingEntity.isPotionActive(Potion.blindness)) {
            final PotionEffect blindness = livingEntity.getActivePotionEffect(Potion.blindness);

			if (blindness != null) {
				// Guessing that this is what OF uses, based on how vanilla calculates the fog value in BackgroundRenderer
				// TODO: Add this to ShaderDoc
				return Math.clamp(0.0F, 1.0F, blindness.getDuration() / 20.0F);
			}
		}

		return 0.0F;
	}

	private static float getPlayerMood() {
        // TODO: What should this be?
        return 0.0F;
//		if (!(client.cameraEntity instanceof LocalPlayer)) {
//			return 0.0F;
//		}
//
//		// This should always be 0 to 1 anyways but just making sure
//		return Math.clamp(0.0F, 1.0F, ((LocalPlayer) client.cameraEntity).getCurrentMood());
	}

	static float getRainStrength() {
        if (client.theWorld == null) {
			return 0f;
		}

		// Note: Ensure this is in the range of 0 to 1 - some custom servers send out of range values.
        return Math.clamp(0.0F, 1.0F, client.theWorld.getRainStrength(CapturedRenderingState.INSTANCE.getTickDelta()));

	}

	private static Vector2i getEyeBrightness() {
        if (client.renderViewEntity == null || client.theWorld == null) {
			return ZERO_VECTOR_2i;
		}

        final int eyeBrightness = client.renderViewEntity.getBrightnessForRender(CapturedRenderingState.INSTANCE.getTickDelta());
        return new Vector2i((eyeBrightness & 0xffff), (eyeBrightness >> 16));

//		Vec3 feet = client.cameraEntity.position();
//		Vec3 eyes = new Vec3(feet.x, client.cameraEntity.getEyeY(), feet.z);
//		BlockPos eyeBlockPos = new BlockPos(eyes);
//
//		int blockLight = client.level.getBrightness(LightLayer.BLOCK, eyeBlockPos);
//		int skyLight = client.level.getBrightness(LightLayer.SKY, eyeBlockPos);
//
//		return new Vector2i(blockLight * 16, skyLight * 16);
	}

	private static float getNightVision() {
        Entity cameraEntity = client.renderViewEntity;

        if (cameraEntity instanceof EntityPlayer entityPlayer) {
            if (!entityPlayer.isPotionActive(Potion.nightVision)) {
                return 0.0F;
            }
            float nightVisionStrength = ((EntityRendererAccessor)client.entityRenderer).invokeGetNightVisionBrightness(entityPlayer, CapturedRenderingState.INSTANCE.getTickDelta());

			try {
				if (nightVisionStrength > 0) {
					// Just protecting against potential weird mod behavior
					return Math.clamp(0.0F, 1.0F, nightVisionStrength);
				}
			} catch (NullPointerException e) {
				return 0.0F;
			}
		}

		return 0.0F;
	}

	static int isEyeInWater() {
        if (client.gameSettings.thirdPersonView == 0 && !client.renderViewEntity.isPlayerSleeping()) {
            if (client.thePlayer.isInsideOfMaterial(Material.water))
			return 1;
            else if (client.thePlayer.isInsideOfMaterial(Material.lava))
			return 2;
        }
			return 0;
		}

	static {
		GbufferPrograms.init();
	}
}

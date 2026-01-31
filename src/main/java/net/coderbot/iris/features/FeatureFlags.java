package net.coderbot.iris.features;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.minecraft.client.resources.I18n;
import org.apache.commons.lang3.text.WordUtils;

import java.util.List;
import java.util.function.BooleanSupplier;

public enum FeatureFlags {
	SEPARATE_HARDWARE_SAMPLERS(() -> true, () -> true),
	PER_BUFFER_BLENDING(() -> true, RenderSystem::supportsBufferBlending),
	COMPUTE_SHADERS(() -> true, RenderSystem::supportsCompute),
	ENTITY_TRANSLUCENT(() -> true, () -> true),
	BLOCK_EMISSION_ATTRIBUTE(() -> true, () -> true),
	CUSTOM_IMAGES(() -> true, RenderSystem::supportsImageLoadStore),
	SSBO(() -> true, RenderSystem::supportsSSBO),
	HIGHER_SHADOWCOLOR(() -> true, () -> true),
	TESSELLATION_SHADERS(() -> true, RenderSystem::supportsTesselation),
	REVERSED_CULLING(() -> true, () -> true),
	CAN_DISABLE_WEATHER(() -> true, () -> true),
	UNKNOWN(() -> false, () -> false);

	private final BooleanSupplier irisRequirement;
	private final BooleanSupplier hardwareRequirement;

	FeatureFlags(BooleanSupplier irisRequirement, BooleanSupplier hardwareRequirement) {
		this.irisRequirement = irisRequirement;
		this.hardwareRequirement = hardwareRequirement;
	}

	public static String getInvalidStatus(List<FeatureFlags> invalidFeatureFlags) {
		boolean unsupportedHardware = false, unsupportedIris = false;
		FeatureFlags[] flags = invalidFeatureFlags.toArray(new FeatureFlags[0]);
		for (FeatureFlags flag : flags) {
			unsupportedIris |= !flag.irisRequirement.getAsBoolean();
			unsupportedHardware |= !flag.hardwareRequirement.getAsBoolean();
		}

		if (unsupportedIris) {
			if (unsupportedHardware) {
				return I18n.format("iris.unsupported.irisorpc");
			}

			return I18n.format("iris.unsupported.iris");
		} else if (unsupportedHardware) {
			return I18n.format("iris.unsupported.pc");
		} else {
			return null;
		}
	}

	public String getHumanReadableName() {
		return WordUtils.capitalize(name().replace("_", " ").toLowerCase());
	}

	public boolean isUsable() {
		return irisRequirement.getAsBoolean() && hardwareRequirement.getAsBoolean();
	}

	public static boolean isInvalid(String name) {
		FeatureFlags flag = getValue(name);
		return flag == UNKNOWN || !flag.isUsable();
	}

	public static FeatureFlags getValue(String value) {
		if (value.equalsIgnoreCase("TESSELATION_SHADERS")) {
			value = "TESSELLATION_SHADERS";
		}
		try {
			return FeatureFlags.valueOf(value.toUpperCase(java.util.Locale.US));
		} catch (IllegalArgumentException e) {
			return FeatureFlags.UNKNOWN;
		}
	}
}

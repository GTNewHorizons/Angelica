package com.gtnewhorizons.angelica.models.renderer;

import com.gtnewhorizons.angelica.models.material.MaterialFinder;
import com.gtnewhorizons.angelica.models.material.MaterialFinderImpl;
import com.gtnewhorizons.angelica.models.material.RenderMaterial;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;

/**
 * The Fabric default renderer implementation. Supports all
 * features defined in the API except shaders and offers no special materials.
 */
public class IndigoRenderer {
	public static final IndigoRenderer INSTANCE = new IndigoRenderer();

	public static final RenderMaterial MATERIAL_STANDARD = INSTANCE.materialFinder().find();

	static {
		INSTANCE.registerMaterial(RenderMaterial.MATERIAL_STANDARD, MATERIAL_STANDARD);
	}

	private final HashMap<ResourceLocation, RenderMaterial> materialMap = new HashMap<>();

	private IndigoRenderer() { }

    /**
     * Obtain a new {@link MaterialFinder} instance used to retrieve
     * standard {@link RenderMaterial} instances.
     *
     * <p>Renderer does not retain a reference to returned instances and they should be re-used for
     * multiple materials when possible to avoid memory allocation overhead.
     */
	public MaterialFinder materialFinder() {
		return new MaterialFinderImpl();
	}

    /**
     * Return a material previously registered via {@link #registerMaterial(ResourceLocation, RenderMaterial)}.
     * Will return null if no material was found matching the given identifier.
     */
	public RenderMaterial materialById(ResourceLocation id) {
		return materialMap.get(id);
	}

    /**
     * Register a material for re-use by other mods or models within a mod.
     * The registry does not persist registrations - mods must create and register
     * all materials at game initialization.
     *
     * <p>Returns false if a material with the given identifier is already present,
     * leaving the existing material intact.
     */
	public boolean registerMaterial(ResourceLocation id, RenderMaterial material) {
		if (materialMap.containsKey(id)) return false;

		// cast to prevent acceptance of impostor implementations
		materialMap.put(id, material);
		return true;
	}
}

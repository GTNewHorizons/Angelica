package net.coderbot.iris.shaderpack.loading;

import com.gtnewhorizons.angelica.glsm.states.BlendState;
import net.coderbot.iris.gl.blending.AlphaTest;
import net.coderbot.iris.gl.blending.AlphaTestFunction;
import net.coderbot.iris.gl.blending.AlphaTestOverride;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import org.lwjgl.opengl.GL11;

import java.util.Objects;
import java.util.Optional;

public enum ProgramId {
	Shadow(ProgramGroup.Shadow, ""),
	ShadowSolid(ProgramGroup.Shadow, "solid", Shadow),
	ShadowCutout(ProgramGroup.Shadow, "cutout", Shadow),
	ShadowWater(ProgramGroup.Shadow, "water", Shadow, BlendModeOverride.OFF),

	Basic(ProgramGroup.Gbuffers, "basic"),
	Line(ProgramGroup.Gbuffers, "line", Basic),

	Textured(ProgramGroup.Gbuffers, "textured", Basic),
	TexturedLit(ProgramGroup.Gbuffers, "textured_lit", Textured),
	SkyBasic(ProgramGroup.Gbuffers, "skybasic", Basic),
	SkyTextured(ProgramGroup.Gbuffers, "skytextured", Textured),
	Clouds(ProgramGroup.Gbuffers, "clouds", Textured),

	Terrain(ProgramGroup.Gbuffers, "terrain", TexturedLit),
	TerrainSolid(ProgramGroup.Gbuffers, "terrain_solid", Terrain),
	TerrainCutoutMip(ProgramGroup.Gbuffers, "terrain_cutout_mip", Terrain),
	TerrainCutout(ProgramGroup.Gbuffers, "terrain_cutout", Terrain),
	DamagedBlock(ProgramGroup.Gbuffers, "damagedblock", Terrain),

	Block(ProgramGroup.Gbuffers, "block", Terrain),
	BlockTrans(ProgramGroup.Gbuffers, "block_translucent", Block, BlendModeOverride.OFF),
	BeaconBeam(ProgramGroup.Gbuffers, "beaconbeam", Textured),
	Item(ProgramGroup.Gbuffers, "item", TexturedLit),

	Entities(ProgramGroup.Gbuffers, "entities", TexturedLit),
	EntitiesTrans(ProgramGroup.Gbuffers, "entities_translucent", Entities),
	EntitiesGlowing(ProgramGroup.Gbuffers, "entities_glowing", Entities),
	ArmorGlint(ProgramGroup.Gbuffers, "armor_glint", Textured),
	SpiderEyes(ProgramGroup.Gbuffers, "spidereyes", Textured,
		new BlendModeOverride(new BlendState(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE)),
		new AlphaTestOverride(new AlphaTest(AlphaTestFunction.GREATER, 0.0001F))),

	Hand(ProgramGroup.Gbuffers, "hand", TexturedLit),
	Weather(ProgramGroup.Gbuffers, "weather", TexturedLit),
	Water(ProgramGroup.Gbuffers, "water", Terrain),
	HandWater(ProgramGroup.Gbuffers, "hand_water", Hand),
	DhTerrain(ProgramGroup.Dh, "terrain"),
	DhWater(ProgramGroup.Dh, "water", DhTerrain),
	DhGeneric(ProgramGroup.Dh, "generic", DhTerrain),
	DhShadow(ProgramGroup.Dh, "shadow"),

	Final(ProgramGroup.Final, ""),
	;

	private final ProgramGroup group;
	private final String sourceName;
	private final ProgramId fallback;
	private final BlendModeOverride defaultBlendOverride;
	private final AlphaTestOverride defaultAlphaTestOverride;

	ProgramId(ProgramGroup group, String name) {
		this.group = group;
		this.sourceName = name.isEmpty() ? group.getBaseName() : group.getBaseName() + "_" + name;
		this.fallback = null;
		this.defaultBlendOverride = null;
		this.defaultAlphaTestOverride = null;
	}

	ProgramId(ProgramGroup group, String name, ProgramId fallback) {
		this.group = group;
		this.sourceName = name.isEmpty() ? group.getBaseName() : group.getBaseName() + "_" + name;
		this.fallback = Objects.requireNonNull(fallback);
		this.defaultBlendOverride = null;
		this.defaultAlphaTestOverride = null;
	}

	ProgramId(ProgramGroup group, String name, ProgramId fallback, BlendModeOverride defaultBlendOverride) {
		this.group = group;
		this.sourceName = name.isEmpty() ? group.getBaseName() : group.getBaseName() + "_" + name;
		this.fallback = Objects.requireNonNull(fallback);
		this.defaultBlendOverride = defaultBlendOverride;
		this.defaultAlphaTestOverride = null;
	}

	ProgramId(ProgramGroup group, String name, ProgramId fallback, BlendModeOverride defaultBlendOverride, AlphaTestOverride defaultAlphaTestOverride) {
		this.group = group;
		this.sourceName = name.isEmpty() ? group.getBaseName() : group.getBaseName() + "_" + name;
		this.fallback = Objects.requireNonNull(fallback);
		this.defaultBlendOverride = defaultBlendOverride;
		this.defaultAlphaTestOverride = defaultAlphaTestOverride;
	}

	public ProgramGroup getGroup() {
		return group;
	}

	public String getSourceName() {
		return sourceName;
	}

	public Optional<ProgramId> getFallback() {
		return Optional.ofNullable(fallback);
	}

	public BlendModeOverride getBlendModeOverride() {
		return defaultBlendOverride;
	}

	public AlphaTestOverride getDefaultAlphaTestOverride() {
		return defaultAlphaTestOverride;
	}
}

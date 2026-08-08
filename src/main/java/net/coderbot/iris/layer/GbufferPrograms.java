package net.coderbot.iris.layer;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import net.coderbot.iris.gl.shader.ProgramCreator;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.Arrays;

public class GbufferPrograms {
	private static boolean entities;
	private static boolean blockEntities;
	private static boolean particles;
	private static boolean outline;
	private static Runnable phaseChangeListener;

	static {
		StateUpdateNotifiers.phaseChangeNotifier = listener -> phaseChangeListener = listener;
	}

	private static void checkReentrancy() {
		if (entities || blockEntities || outline) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call begin function when entities = "
				+ entities + ", blockEntities = " + blockEntities + ", outline = " + outline);
		}
	}

	public static void beginEntities() {
		checkReentrancy();
		setPhase(WorldRenderingPhase.ENTITIES);
		setBlockEntityDefaults();
		entities = true;
	}

	public static void endEntities() {
		if (!entities) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call endEntities when entities = false");
		}

		entities = false;
		setPhase(particles ? WorldRenderingPhase.PARTICLES : WorldRenderingPhase.NONE);
	}

	public static void beginOutline() {
		checkReentrancy();
		setPhase(WorldRenderingPhase.OUTLINE);
		outline = true;
	}

	public static void endOutline() {
		if (!outline) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call endOutline when outline = false");
		}

		setPhase(WorldRenderingPhase.NONE);
		outline = false;
	}

	public static void beginParticles() {
		checkReentrancy();
		setPhase(WorldRenderingPhase.PARTICLES);
		particlesTranslucency = beginTranslucencyDeclaration(Boolean.FALSE);
		particles = true;
	}

	public static void endParticles() {
		if (!particles) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call endParticles when particles = false");
		}

		endTranslucencyDeclaration(particlesTranslucency);
		particlesTranslucency = null;
		setPhase(WorldRenderingPhase.NONE);
		particles = false;
	}

	private static Boolean particlesTranslucency;

	public static void beginBlockEntities() {
		checkReentrancy();
		setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
		setBlockEntityDefaults();
		blockEntities = true;
	}

	public static void endBlockEntities() {
		if (!blockEntities) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call endBlockEntities when blockEntities = false");
		}

		setPhase(WorldRenderingPhase.NONE);
		blockEntities = false;
	}

	public static void setCutoutDefaults() {
		GLStateManager.enableAlphaTest();
		GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);

		final int previousUnit = GLStateManager.getActiveTextureUnitForServerState();
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
		GLStateManager.enableTexture();
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + previousUnit);
	}

	public static long pushCutoutDefaults() {
		final long saved = packAlphaState();
		setCutoutDefaults();
		return saved;
	}

	public static void popCutoutDefaults(long saved) {
		if ((saved & ALPHA_ENABLED_BIT) != 0) {
			GLStateManager.enableAlphaTest();
		} else {
			GLStateManager.disableAlphaTest();
		}
		GLStateManager.glAlphaFunc((int) ((saved >>> 32) & 0xFFFF), Float.intBitsToFloat((int) saved));
	}

	public static int pushBlendState() {
		if (blendDepth == blendSaves.length) {
			blendSaves = Arrays.copyOf(blendSaves, blendDepth * 2);
			blendEnabledSaves = Arrays.copyOf(blendEnabledSaves, blendDepth * 2);
		}

		BlendState saved = blendSaves[blendDepth];
		if (saved == null) {
			saved = new BlendState();
			blendSaves[blendDepth] = saved;
		}

		GLStateManager.getEffectiveBlendState(saved);
		blendEnabledSaves[blendDepth] = GLStateManager.isEffectiveBlendEnabled();

		return blendDepth++;
	}
	public static void popBlendState(int depth) {
		blendDepth = depth;

		if (blendEnabledSaves[depth]) {
			GLStateManager.enableBlend();
		} else {
			GLStateManager.disableBlend();
		}

		final BlendState saved = blendSaves[depth];
		GLStateManager.tryBlendFuncSeparate(saved.getSrcRgb(), saved.getDstRgb(), saved.getSrcAlpha(), saved.getDstAlpha());
	}

	private static BlendState[] blendSaves = new BlendState[4];
	private static boolean[] blendEnabledSaves = new boolean[4];
	private static int blendDepth;

	private static final long ALPHA_ENABLED_BIT = 1L << 48;
	private static final AlphaState alphaScratch = new AlphaState();

	private static long packAlphaState() {
		GLStateManager.getEffectiveAlphaState(alphaScratch);

		return (Float.floatToRawIntBits(alphaScratch.getReference()) & 0xFFFFFFFFL)
			| ((long) (alphaScratch.getFunction() & 0xFFFF) << 32)
			| (GLStateManager.isEffectiveAlphaTestEnabled() ? ALPHA_ENABLED_BIT : 0L);
	}

	public static void setBlockEntityDefaults() {
		GLStateManager.glVertexAttrib2s(ProgramCreator.MC_ENTITY, (short)-1, (short)-1);
		GLStateManager.glVertexAttrib2f(ProgramCreator.MC_MID_TEX_COORD, 0.5f, 0.5f);
		GLStateManager.glVertexAttrib4f(ProgramCreator.AT_TANGENT, 1.0f, 0.0f, 0.0f, 1.0f);
		GLStateManager.glVertexAttrib4f(ProgramCreator.AT_MIDBLOCK, 0.0f, 0.0f, 0.0f, 0.0f);
	}

	public static WorldRenderingPhase getCurrentPhase() {
		final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			return pipeline.getPhase();
		} else {
			return WorldRenderingPhase.NONE;
		}
	}

	private static void setPhase(WorldRenderingPhase phase) {
		final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			pipeline.setPhase(phase);
		}
	}

	public static void setOverridePhase(WorldRenderingPhase phase) {
		final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			pipeline.setOverridePhase(phase);
		}
	}

	public static Boolean beginTranslucencyDeclaration(Boolean translucent) {
		final Boolean previous = declaredTranslucent;
		declaredTranslucent = translucent;
		applyTranslucencyDeclaration();
		return previous;
	}

	public static void endTranslucencyDeclaration(Boolean previous) {
		declaredTranslucent = previous;
		applyTranslucencyDeclaration();
	}

	public static void setTranslucencyDeclaration(Boolean translucent) {
		declaredTranslucent = translucent;
		applyTranslucencyDeclaration();
	}

	private static Boolean declaredTranslucent;

	private static void applyTranslucencyDeclaration() {
		final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			pipeline.setDeclaredTranslucency(declaredTranslucent);
		}
	}

	public static void setupSpecialRenderCondition(SpecialCondition override) {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setSpecialCondition(override));
	}

	public static void teardownSpecialRenderCondition() {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setSpecialCondition(null));
	}

	public static void runPhaseChangeNotifier() {
		if (phaseChangeListener != null) {
			phaseChangeListener.run();
		}
	}

	public static void init() {
		// Empty initializer to run static
	}
}

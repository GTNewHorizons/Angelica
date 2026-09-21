package net.coderbot.iris.layer;

import lombok.Getter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import com.gtnewhorizons.angelica.iris.IrisDisplayListState;
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
	private static boolean entityLoop;
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

	public static void beginEntityLoop() {
		entityLoop = true;
		setPhase(WorldRenderingPhase.ENTITIES);
		setBlockEntityDefaults();
	}

	public static void endEntityLoop() {
		entityLoop = false;
		setPhase(WorldRenderingPhase.NONE);
	}

	public static boolean isEntityLoopActive() {
		return entityLoop;
	}

	public static void onEntityRenderBoundary() {
		final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			pipeline.onEntityRenderBoundary();
		}
	}

	public static void endEntities() {
		if (!entities) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call endEntities when entities = false");
		}

		entities = false;
		endPhase();
	}

	private static void endPhase() {
		if (particles) {
			setPhase(WorldRenderingPhase.PARTICLES);
		} else if (entityLoop) {
			setPhase(WorldRenderingPhase.ENTITIES);
		} else {
			setPhase(WorldRenderingPhase.NONE);
		}
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

		outline = false;
		endPhase();
	}

	public static int beginParticles() {
		if (particleDepth == particlePhaseSaves.length) {
			particlePhaseSaves = Arrays.copyOf(particlePhaseSaves, particleDepth * 2);
			particleTranslucencySaves = Arrays.copyOf(particleTranslucencySaves, particleDepth * 2);
		}

		particlePhaseSaves[particleDepth] = getCurrentPhase();
		particleTranslucencySaves[particleDepth] = beginTranslucencyDeclaration(Boolean.FALSE);

		setPhase(WorldRenderingPhase.PARTICLES);
		particles = true;

		return particleDepth++;
	}

	public static void endParticles(int depth) {
		particleDepth = depth;

		endTranslucencyDeclaration(particleTranslucencySaves[depth]);
		particleTranslucencySaves[depth] = null;

		setPhase(particlePhaseSaves[depth]);
		particlePhaseSaves[depth] = null;

		particles = particleDepth > 0;
	}

	private static WorldRenderingPhase[] particlePhaseSaves = new WorldRenderingPhase[4];
	private static Boolean[] particleTranslucencySaves = new Boolean[4];
	private static int particleDepth;

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

		blockEntities = false;
		endPhase();
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
		IrisDisplayListState.recordCutoutScope(true);
		final long saved = packCutoutState();
		IrisDisplayListState.runUnrecorded(GbufferPrograms::setCutoutDefaults);
		return saved;
	}

	public static void popCutoutDefaults(long saved) {
		IrisDisplayListState.recordCutoutScope(false);
		if (DisplayListManager.isRecording()) IrisDisplayListState.runUnrecorded(() -> restoreCutoutState(saved));
		else restoreCutoutState(saved);
	}

	private static void restoreCutoutState(long saved) {
		if ((saved & ALPHA_ENABLED_BIT) != 0) {
			GLStateManager.enableAlphaTest();
		} else {
			GLStateManager.disableAlphaTest();
		}
		GLStateManager.glAlphaFunc((int) ((saved >>> 32) & 0xFFFF), Float.intBitsToFloat((int) saved));

		final int previousUnit = GLStateManager.getActiveTextureUnitForServerState();
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
		if ((saved & LIGHTMAP_ENABLED_BIT) != 0) GLStateManager.enableTexture();
		else GLStateManager.disableTexture();
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + previousUnit);
	}

	public static int pushBlendState() {
		IrisDisplayListState.recordBlendScope(true);
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
		IrisDisplayListState.recordBlendScope(false);
		blendDepth = depth;
		if (DisplayListManager.isRecording()) IrisDisplayListState.runUnrecorded(() -> restoreBlendState(depth));
		else restoreBlendState(depth);
	}

	private static void restoreBlendState(int depth) {
		final boolean enabled = blendEnabledSaves[depth];
		if (enabled != GLStateManager.isEffectiveBlendEnabled()) {
			if (enabled) {
				GLStateManager.enableBlend();
			} else {
				GLStateManager.disableBlend();
			}
		}

		final BlendState saved = blendSaves[depth];
		GLStateManager.getEffectiveBlendState(blendScratch);
		if (blendScratch.getSrcRgb() != saved.getSrcRgb() || blendScratch.getDstRgb() != saved.getDstRgb()
			|| blendScratch.getSrcAlpha() != saved.getSrcAlpha() || blendScratch.getDstAlpha() != saved.getDstAlpha()) {
			GLStateManager.tryBlendFuncSeparate(saved.getSrcRgb(), saved.getDstRgb(), saved.getSrcAlpha(), saved.getDstAlpha());
		}
		GLStateManager.glBlendEquationSeparate(saved.getEquationRgb(), saved.getEquationAlpha());
		GLStateManager.glBlendColor(saved.getBlendColorR(), saved.getBlendColorG(), saved.getBlendColorB(), saved.getBlendColorA());
	}

	public static void popBlendStateTop() {
		if (blendDepth > 0) {
			popBlendState(blendDepth - 1);
		}
	}

	private static BlendState[] blendSaves = new BlendState[4];
	private static boolean[] blendEnabledSaves = new boolean[4];
	private static int blendDepth;
	private static final BlendState blendScratch = new BlendState();

	private static final long ALPHA_ENABLED_BIT = 1L << 48;
	private static final long LIGHTMAP_ENABLED_BIT = 1L << 49;
	private static final AlphaState alphaScratch = new AlphaState();

	private static long packCutoutState() {
		GLStateManager.getEffectiveAlphaState(alphaScratch);

		return (Float.floatToRawIntBits(alphaScratch.getReference()) & 0xFFFFFFFFL)
			| ((long) (alphaScratch.getFunction() & 0xFFFF) << 32)
			| (GLStateManager.isEffectiveAlphaTestEnabled() ? ALPHA_ENABLED_BIT : 0L)
			| (GLStateManager.getTextures().getTextureUnitStates(1).isEnabled() ? LIGHTMAP_ENABLED_BIT : 0L);
	}

	public static void setBlockEntityDefaults() {
		GLStateManager.glVertexAttrib2s(ProgramCreator.MC_ENTITY, (short)-1, (short)-1);
		FfpExtendedAttribs.setNeutralCurrentValues();
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

	public static boolean beginNestedEntityPhase() {
		IrisDisplayListState.recordNestedEntityScope(true);
		if (getCurrentPhase() != WorldRenderingPhase.BLOCK_ENTITIES) {
			return false;
		}
		applyPushOverridePhase(WorldRenderingPhase.ENTITIES);
		return true;
	}

	public static void endNestedEntityPhase(boolean pushed) {
		IrisDisplayListState.recordNestedEntityScope(false);
		if (pushed) {
			applyPopOverridePhase();
		}
	}

	public static void setOverridePhase(WorldRenderingPhase phase) {
		IrisDisplayListState.recordOverridePhase(phase);
		applyOverridePhase(phase);
	}

	private static final ObjectArrayList<WorldRenderingPhase> phaseStack = new ObjectArrayList<>();

	public static void pushOverridePhase(WorldRenderingPhase phase) {
		IrisDisplayListState.recordPhaseScope(phase, true);
		applyPushOverridePhase(phase);
	}

	private static void applyPushOverridePhase(WorldRenderingPhase phase) {
		phaseStack.add(overridePhase);
		applyOverridePhase(phase);
	}

	public static void popOverridePhase() {
		IrisDisplayListState.recordPhaseScope(null, false);
		applyPopOverridePhase();
	}

	private static void applyPopOverridePhase() {
		applyOverridePhase(phaseStack.pop());
	}

	private static void applyOverridePhase(WorldRenderingPhase phase) {
		overridePhase = phase;

		final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			pipeline.setOverridePhase(phase);
		}
	}

    @Getter
    private static WorldRenderingPhase overridePhase;

    public static Boolean beginTranslucencyDeclaration(Boolean translucent) {
		IrisDisplayListState.recordTranslucencyScope(translucent, true);
		final Boolean previous = declaredTranslucent;
		declaredTranslucent = translucent;
		applyTranslucencyDeclaration();
		return previous;
	}

	public static void endTranslucencyDeclaration(Boolean previous) {
		IrisDisplayListState.recordTranslucencyScope(null, false);
		declaredTranslucent = previous;
		applyTranslucencyDeclaration();
	}

	public static void setTranslucencyDeclaration(Boolean translucent) {
		IrisDisplayListState.recordTranslucency(translucent);
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

	private static SpecialCondition currentSpecial;
	private static final ObjectArrayList<SpecialCondition> specialStack = new ObjectArrayList<>();

	public static SpecialCondition getSpecialCondition() {
		return currentSpecial;
	}

	public static Boolean getDeclaredTranslucency() {
		return declaredTranslucent;
	}

	public static void setupSpecialRenderCondition(SpecialCondition override) {
		IrisDisplayListState.recordSpecialCondition(override, true);
		specialStack.add(currentSpecial);
		applySpecialCondition(override);
	}

	private static void applySpecialCondition(SpecialCondition override) {
		currentSpecial = override;
		final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			pipeline.setSpecialCondition(override);
		}
	}

	public static void teardownSpecialRenderCondition() {
		IrisDisplayListState.recordSpecialCondition(null, false);
		applySpecialCondition(specialStack.pop());
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

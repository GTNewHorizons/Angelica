package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.AlphaTestFunction;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import com.gtnewhorizons.angelica.glsm.states.DepthState;
import net.coderbot.iris.gl.blending.AlphaTest;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import net.coderbot.iris.gl.uniform.FloatSupplier;
import net.coderbot.iris.gl.uniform.FloatUniform;
import net.coderbot.iris.gl.uniform.IntUniform;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.lang.reflect.Constructor;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class StateSetOverrideGLTest {

    private static Runnable listener;
    private int program;
    private int otherProgram;

    @BeforeAll
    static void registerAlphaListener() {
        GLSMHooks.ALPHA_STATE_CHANGE.addListener(event -> {
            if (listener != null) listener.run();
        });
    }

    @AfterEach
    void cleanup() {
        listener = null;
        BlendModeStorage.restoreBlend();
        AlphaTestStorage.restoreAlphaTest();
        DepthColorStorage.unlockDepthColor();
        VanillaLayerRig.clear();
        GLStateManager.disableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.disableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.disableCull();
        GLStateManager.glUseProgram(0);
        if (program != 0) GLStateManager.glDeleteProgram(program);
        if (otherProgram != 0) GLStateManager.glDeleteProgram(otherProgram);
    }

    @Test
    void blendBracketUnderOverrideRestoresVanillaShadowAndLeavesLiveStateUntouched() {
        VanillaLayerRig.installBlend();

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        BlendModeStorage.overrideBlend(new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE));

        final boolean liveEnabledBefore = GLStateManager.getBlendMode().isEnabled();
        final BlendState liveBefore = new BlendState().set(GLStateManager.getBlendState());

        final int d = GLStateManager.pushState(StateSet.BLEND);
        GLStateManager.disableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);
        GLStateManager.popStateTo(d);

        assertTrue(BlendModeStorage.ENABLE_LAYER.getVanilla(), "vanilla shadow enable restored to the pre-bracket value");
        final BlendState vanillaAfter = new BlendState();
        BlendModeStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(GL11.GL_SRC_ALPHA, vanillaAfter.getSrcRgb(), "vanilla shadow src rgb restored");
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, vanillaAfter.getDstRgb(), "vanilla shadow dst rgb restored");
        assertEquals(GL11.GL_ONE, vanillaAfter.getSrcAlpha(), "vanilla shadow src alpha restored");
        assertEquals(GL11.GL_ZERO, vanillaAfter.getDstAlpha(), "vanilla shadow dst alpha restored");

        assertEquals(liveEnabledBefore, GLStateManager.getBlendMode().isEnabled(), "live overridden enable left untouched by the bracket");
        assertEquals(liveBefore.getSrcRgb(), GLStateManager.getBlendState().getSrcRgb(), "live overridden src rgb left untouched");
        assertEquals(liveBefore.getDstRgb(), GLStateManager.getBlendState().getDstRgb(), "live overridden dst rgb left untouched");
        assertEquals(liveBefore.getSrcAlpha(), GLStateManager.getBlendState().getSrcAlpha(), "live overridden src alpha left untouched");
        assertEquals(liveBefore.getDstAlpha(), GLStateManager.getBlendState().getDstAlpha(), "live overridden dst alpha left untouched");
    }

    @Test
    void alphaBracketUnderOverrideRestoresVanillaShadowAndLeavesLiveStateUntouched() {
        VanillaLayerRig.installAlpha();

        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);

        AlphaTestStorage.overrideAlphaTest(new AlphaTest(AlphaTestFunction.LEQUAL, 0.25f));

        final boolean liveEnabledBefore = GLStateManager.getAlphaTest().isEnabled();
        final int liveFuncBefore = GLStateManager.getAlphaState().getFunction();
        final float liveRefBefore = GLStateManager.getAlphaState().getReference();

        final int d = GLStateManager.pushState(StateSet.ALPHA);
        GLStateManager.disableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.1f);
        GLStateManager.popStateTo(d);

        assertTrue(AlphaTestStorage.ENABLE_LAYER.getVanilla(), "vanilla shadow enable restored to the pre-bracket value");
        final AlphaState vanillaAfter = new AlphaState();
        AlphaTestStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(GL11.GL_GREATER, vanillaAfter.getFunction(), "vanilla shadow func restored");
        assertEquals(0.5f, vanillaAfter.getReference(), 0.0001f, "vanilla shadow ref restored");

        assertEquals(liveEnabledBefore, GLStateManager.getAlphaTest().isEnabled(), "live overridden enable left untouched by the bracket");
        assertEquals(liveFuncBefore, GLStateManager.getAlphaState().getFunction(), "live overridden func left untouched");
        assertEquals(liveRefBefore, GLStateManager.getAlphaState().getReference(), 0.0001f, "live overridden ref left untouched");
    }

    @Test
    void masksBracketUnderOverrideRestoresVanillaShadowAndLeavesLiveStateUntouched() {
        VanillaLayerRig.installDepthColor();

        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(true, false, true, false);

        DepthColorStorage.disableDepthColor();

        final boolean liveMaskBefore = GLStateManager.getDepthState().isEnabled();
        final ColorMask liveColorBefore = new ColorMask().set(GLStateManager.getColorMask());

        final int d = GLStateManager.pushState(StateSet.MASKS);
        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.popStateTo(d);

        final DepthState vanillaDepthAfter = new DepthState();
        DepthColorStorage.DEPTH_LAYER.readVanilla(vanillaDepthAfter);
        assertTrue(vanillaDepthAfter.isEnabled(), "vanilla shadow depth mask restored to the pre-bracket value");

        final ColorMask vanillaColorAfter = new ColorMask();
        DepthColorStorage.COLOR_LAYER.readVanilla(vanillaColorAfter);
        assertTrue(vanillaColorAfter.red, "vanilla shadow color red restored");
        assertFalse(vanillaColorAfter.green, "vanilla shadow color green restored");
        assertTrue(vanillaColorAfter.blue, "vanilla shadow color blue restored");
        assertFalse(vanillaColorAfter.alpha, "vanilla shadow color alpha restored");

        assertEquals(liveMaskBefore, GLStateManager.getDepthState().isEnabled(), "live overridden depth mask left untouched by the bracket");
        assertEquals(liveColorBefore.red, GLStateManager.getColorMask().red, "live overridden color red left untouched");
        assertEquals(liveColorBefore.green, GLStateManager.getColorMask().green, "live overridden color green left untouched");
        assertEquals(liveColorBefore.blue, GLStateManager.getColorMask().blue, "live overridden color blue left untouched");
        assertEquals(liveColorBefore.alpha, GLStateManager.getColorMask().alpha, "live overridden color alpha left untouched");
    }

    @Test
    void untouchedBracketUnderOverrideLeavesVanillaShadowIntact() {
        VanillaLayerRig.installBlend();

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        BlendModeStorage.overrideBlend(new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE));

        final boolean vanillaEnabledBefore = BlendModeStorage.ENABLE_LAYER.getVanilla();
        final BlendState vanillaBefore = new BlendState();
        BlendModeStorage.FUNC_LAYER.readVanilla(vanillaBefore);
        final boolean liveEnabledBefore = GLStateManager.getBlendMode().isEnabled();
        final BlendState liveBefore = new BlendState().set(GLStateManager.getBlendState());

        final int d = GLStateManager.pushState(StateSet.BLEND);
        GLStateManager.popStateTo(d);

        assertEquals(vanillaEnabledBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "untouched bracket leaves the vanilla enable shadow intact");
        final BlendState vanillaAfter = new BlendState();
        BlendModeStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(vanillaBefore.getSrcRgb(), vanillaAfter.getSrcRgb(), "untouched bracket leaves the vanilla func shadow intact");
        assertEquals(liveEnabledBefore, GLStateManager.getBlendMode().isEnabled(), "untouched bracket leaves the live overridden enable intact");
        assertEquals(liveBefore.getSrcRgb(), GLStateManager.getBlendState().getSrcRgb(), "untouched bracket leaves the live overridden func intact");
    }

    @Test
    void overrideAcquiredMidBracket() {
        VanillaLayerRig.installAlpha();

        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);

        final int d = GLStateManager.pushState(StateSet.ALPHA);
        AlphaTestStorage.overrideAlphaTest(new AlphaTest(AlphaTestFunction.LEQUAL, 0.25f));

        assertEquals(GL11.GL_LEQUAL, GLStateManager.getAlphaState().getFunction(), "the override value is visible live immediately after acquiring mid-bracket");

        GLStateManager.popStateTo(d);

        assertEquals(GL11.GL_LEQUAL, GLStateManager.getAlphaState().getFunction(), "the live cache still shows the override value after pop: the vanilla-layer-aware restore writes the pre-bracket baseline into the vanilla shadow, not the live cache, while the override is held");
        final AlphaState vanillaAfter = new AlphaState();
        AlphaTestStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(GL11.GL_GREATER, vanillaAfter.getFunction(), "the vanilla shadow is restored to the pre-bracket baseline by the bracket pop");
        assertEquals(0.5f, vanillaAfter.getReference(), 0.0001f, "the vanilla shadow reference is restored to the pre-bracket baseline");
        assertTrue(AlphaTestStorage.ENABLE_LAYER.getVanilla(), "the vanilla enable shadow still reflects the pre-bracket baseline captured at acquire");
    }

    @Test
    void overrideReleasedMidBracket() {
        VanillaLayerRig.installAlpha();

        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        AlphaTestStorage.overrideAlphaTest(new AlphaTest(AlphaTestFunction.LEQUAL, 0.25f));

        assertEquals(GL11.GL_LEQUAL, GLStateManager.getAlphaState().getFunction(), "the override value is visible live before the bracket opens");

        final int d = GLStateManager.pushState(StateSet.ALPHA);
        AlphaTestStorage.restoreAlphaTest();

        assertEquals(GL11.GL_GREATER, GLStateManager.getAlphaState().getFunction(), "releasing mid-bracket writes the vanilla value back through the live path immediately");

        GLStateManager.glAlphaFunc(GL11.GL_NEVER, 0.9f);

        GLStateManager.popStateTo(d);

        assertEquals(GL11.GL_GREATER, GLStateManager.getAlphaState().getFunction(), "once released, the bracket pop restores the live cache to the vanilla baseline captured at push time through the vanilla-layer-aware slot, proving the pop actually acted rather than the assertion coincidentally already holding");
    }

    @Test
    void glDepthMaskDeferBranchInsideBracketRestoredAtPop() {
        VanillaLayerRig.installDepthColor();

        GLStateManager.glDepthMask(true);
        DepthColorStorage.disableDepthColor();

        final int d = GLStateManager.pushState(StateSet.MASKS);
        final boolean liveBefore = GLStateManager.getDepthState().isEnabled();

        GLStateManager.glDepthMask(false);

        GLStateManager.popStateTo(d);

        assertEquals(liveBefore, GLStateManager.getDepthState().isEnabled(), "GLSM's own depth-mask cache is untouched by the defer branch, so the bracket pop leaves it exactly as it was");
        final DepthState vanillaAfter = new DepthState();
        DepthColorStorage.DEPTH_LAYER.readVanilla(vanillaAfter);
        assertTrue(vanillaAfter.isEnabled(), "the bracket pop reverts the vanilla depth-mask shadow to the pre-bracket baseline, undoing the defer branch's write");
    }

    @Test
    void glColorMaskDeferBranchInsideBracketRestoredAtPop() {
        VanillaLayerRig.installDepthColor();

        GLStateManager.glColorMask(true, true, true, true);
        DepthColorStorage.disableDepthColor();

        final int d = GLStateManager.pushState(StateSet.MASKS);
        final ColorMask liveBefore = new ColorMask().set(GLStateManager.getColorMask());

        GLStateManager.glColorMask(false, false, true, true);

        GLStateManager.popStateTo(d);

        assertEquals(liveBefore.red, GLStateManager.getColorMask().red, "GLSM's own color-mask cache is untouched by the defer branch, so the bracket pop leaves it exactly as it was");
        assertEquals(liveBefore.green, GLStateManager.getColorMask().green, "GLSM's own color-mask cache green channel is untouched by the defer branch");
        final ColorMask vanillaAfter = new ColorMask();
        DepthColorStorage.COLOR_LAYER.readVanilla(vanillaAfter);
        assertTrue(vanillaAfter.red && vanillaAfter.green, "the bracket pop reverts the vanilla color-mask shadow to the pre-bracket baseline, undoing the defer branch's write");
    }

    @Test
    void glAlphaFuncDeferBranchInsideBracketRestoredAtPop() {
        VanillaLayerRig.installAlpha();

        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        AlphaTestStorage.overrideAlphaTest(new AlphaTest(AlphaTestFunction.LEQUAL, 0.25f));

        final int d = GLStateManager.pushState(StateSet.ALPHA);
        final int liveFuncBefore = GLStateManager.getAlphaState().getFunction();

        GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.1f);

        GLStateManager.popStateTo(d);

        assertEquals(liveFuncBefore, GLStateManager.getAlphaState().getFunction(), "GLSM's own alpha-func cache is untouched by the defer branch, so the bracket pop leaves it exactly as it was");
        final AlphaState vanillaAfter = new AlphaState();
        AlphaTestStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(GL11.GL_GREATER, vanillaAfter.getFunction(), "the bracket pop reverts the vanilla alpha-func shadow to the pre-bracket baseline, undoing the defer branch's write");
        assertEquals(0.5f, vanillaAfter.getReference(), 0.0001f, "the bracket pop reverts the vanilla alpha-ref shadow to the pre-bracket baseline");
    }

    @Test
    void glBlendFuncDeferBranchInsideBracketRestoredAtPop() {
        VanillaLayerRig.installBlend();

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        BlendModeStorage.overrideBlend(new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE));

        final int d = GLStateManager.pushState(StateSet.BLEND);
        final int liveSrcBefore = GLStateManager.getBlendState().getSrcRgb();

        GLStateManager.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO);

        GLStateManager.popStateTo(d);

        assertEquals(liveSrcBefore, GLStateManager.getBlendState().getSrcRgb(), "GLSM's own blend-func cache is untouched by the defer branch, so the bracket pop leaves it exactly as it was");
        final BlendState vanillaAfter = new BlendState();
        BlendModeStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(GL11.GL_SRC_ALPHA, vanillaAfter.getSrcRgb(), "the bracket pop reverts the vanilla blend-func shadow to the pre-bracket baseline, undoing the defer branch's write");
    }

    @Test
    void tryBlendFuncSeparateDeferBranchInsideBracketRestoredAtPop() {
        VanillaLayerRig.installBlend();

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        BlendModeStorage.overrideBlend(new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE));

        final int d = GLStateManager.pushState(StateSet.BLEND);
        final int liveSrcBefore = GLStateManager.getBlendState().getSrcRgb();

        GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO);

        GLStateManager.popStateTo(d);

        assertEquals(liveSrcBefore, GLStateManager.getBlendState().getSrcRgb(), "GLSM's own blend-func cache is untouched by the defer branch, so the bracket pop leaves it exactly as it was");
        final BlendState vanillaAfter = new BlendState();
        BlendModeStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(GL11.GL_SRC_ALPHA, vanillaAfter.getSrcRgb(), "the bracket pop reverts the vanilla blend-func shadow to the pre-bracket baseline, undoing the defer branch's write");
    }

    @Test
    void enableBlendDeferBranchInsideBracketRestoredAtPop() {
        VanillaLayerRig.installBlend();

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        BlendModeStorage.overrideBlend(new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE));

        final int d = GLStateManager.pushState(StateSet.BLEND);
        final boolean vanillaBefore = BlendModeStorage.ENABLE_LAYER.getVanilla();

        GLStateManager.disableBlend();
        assertNotEquals(vanillaBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "the defer branch changes the vanilla shadow immediately");

        GLStateManager.popStateTo(d);

        assertEquals(vanillaBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "the boolean defer branch is tracked by beforeModify, so the bracket pop reverts the vanilla shadow to the pre-bracket baseline");
    }

    @Test
    void enableAlphaTestDeferBranchInsideBracketRestoredAtPop() {
        VanillaLayerRig.installAlpha();

        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        AlphaTestStorage.overrideAlphaTest(new AlphaTest(AlphaTestFunction.LEQUAL, 0.25f));

        final int d = GLStateManager.pushState(StateSet.ALPHA);
        final boolean vanillaBefore = AlphaTestStorage.ENABLE_LAYER.getVanilla();

        GLStateManager.disableAlphaTest();

        GLStateManager.popStateTo(d);

        assertEquals(vanillaBefore, AlphaTestStorage.ENABLE_LAYER.getVanilla(), "the boolean defer branch is tracked by beforeModify, so the bracket pop reverts the vanilla shadow to the pre-bracket baseline");
    }

    @Test
    void nonMemberBracketUnderOverrideCarriesToParent() {
        VanillaLayerRig.installBlend();

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        BlendModeStorage.overrideBlend(new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE));

        final boolean vanillaBefore = BlendModeStorage.ENABLE_LAYER.getVanilla();

        final int outer = GLStateManager.pushState(StateSet.BLEND);
        GLStateManager.pushState(StateSet.CULL);

        GLStateManager.disableBlend();
        assertNotEquals(vanillaBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "the defer branch changes the vanilla shadow immediately");

        GLStateManager.popState();

        assertNotEquals(vanillaBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "a non-member bracket pop does not restore the change, it carries to the parent");

        GLStateManager.popStateTo(outer);

        assertEquals(vanillaBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "the enclosing member bracket pop restores the change that carried through the non-member pop");
    }

    @Test
    void functionEnableAndCombinedRestorationPostExactlyOnce() {
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        for (int changes = 1; changes <= 3; changes++) {
            final int depth = GLStateManager.pushState(StateSet.ALPHA);
            if ((changes & 1) != 0) GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.25f);
            if ((changes & 2) != 0) GLStateManager.disableAlphaTest();
            final int[] calls = { 0 };
            listener = () -> {
                calls[0]++;
                assertFalse(GLStateManager.isPoppingAttributes());
                assertEquals(depth, GLStateManager.getAttribDepth());
                assertTrue(GLStateManager.getAlphaTest().isEnabled());
                assertEquals(GL11.GL_GREATER, GLStateManager.getAlphaState().getFunction());
                assertEquals(0.5f, GLStateManager.getAlphaState().getReference());
                final int nested = GLStateManager.pushState(StateSet.ALPHA);
                GLStateManager.popStateTo(nested);
            };
            GLStateManager.popStateTo(depth);
            assertEquals(1, calls[0]);
            listener = null;
        }
    }

    @Test
    void unchangedAndNonmemberPopsDoNotPostAndOuterMemberDoes() {
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        final int[] calls = { 0 };
        listener = () -> calls[0]++;
        final int outer = GLStateManager.pushState(StateSet.ALPHA);
        final int unchanged = GLStateManager.pushState(StateSet.ALPHA);
        GLStateManager.popStateTo(unchanged);
        assertEquals(0, calls[0]);
        final int inner = GLStateManager.pushState(StateSet.CULL);
        GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.25f);
        calls[0] = 0;
        GLStateManager.popStateTo(inner);
        assertEquals(0, calls[0]);
        GLStateManager.popStateTo(outer);
        assertEquals(1, calls[0]);
    }

    @Test
    void lockedOverrideRestoresEffectiveStateBeforeNotification() {
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        VanillaLayerRig.installAlpha();
        AlphaTestStorage.overrideAlphaTest(new AlphaTest(AlphaTestFunction.LEQUAL, 0.75f));
        final int depth = GLStateManager.pushState(StateSet.ALPHA);
        GLStateManager.getAlphaState().beforeModify();
        final AlphaState shadow = new AlphaState();
        shadow.setFunction(GL11.GL_LESS);
        shadow.setReference(0.25f);
        AlphaTestStorage.FUNC_LAYER.writeVanilla(shadow);
        GLStateManager.getAlphaTest().beforeModify();
        AlphaTestStorage.ENABLE_LAYER.setVanilla(false);
        final int[] calls = { 0 };
        listener = () -> {
            calls[0]++;
            assertEquals(GL11.GL_LEQUAL, GLStateManager.getAlphaState().getFunction());
            assertEquals(0.75f, GLStateManager.getAlphaState().getReference());
            assertTrue(GLStateManager.isEffectiveAlphaTestEnabled());
            assertEquals(GL11.GL_GREATER, GLStateManager.getEffectiveAlphaState(shadow).getFunction());
            assertEquals(0.5f, shadow.getReference());
        };
        GLStateManager.popStateTo(depth);
        assertEquals(1, calls[0]);
    }

    @Test
    void dynamicUniformsUpdateWithoutRebindingAndAfterProgramRestoration() {
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        program = createProgram();
        otherProgram = createProgram();
        GLStateManager.glUseProgram(program);
        final int referenceLocation = GLStateManager.glGetUniformLocation(program, "alphaReference");
        final int functionLocation = GLStateManager.glGetUniformLocation(program, "alphaFunction");
        final Runnable[] updates = new Runnable[2];
        final FloatUniform reference = newFloatUniform(referenceLocation, () -> GLStateManager.getAlphaState().getReference(), update -> updates[0] = update);
        final IntUniform function = newIntUniform(functionLocation, () -> GLStateManager.getAlphaState().getFunction(), update -> updates[1] = update);
        reference.update();
        function.update();
        listener = () -> {
            assertEquals(program, GLStateManager.getActiveProgram());
            assertEquals(program, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            assertFalse(GLStateManager.isPoppingAttributes());
            updates[0].run();
            updates[1].run();
        };
        final int depth = GLStateManager.pushState(StateSet.ALPHA);
        GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.25f);
        assertUniforms(referenceLocation, functionLocation, 0.25f, GL11.GL_LESS);
        GLStateManager.popStateTo(depth);
        assertUniforms(referenceLocation, functionLocation, 0.5f, GL11.GL_GREATER);
        final int saved = GLStateManager.pushState(StateSet.FONT);
        GLStateManager.disableAlphaTest();
        GLStateManager.glUseProgram(otherProgram);
        GLStateManager.popStateTo(saved);
        assertTrue(GLStateManager.getAlphaTest().isEnabled());
        assertEquals(program, GLStateManager.getActiveProgram());
    }

    private void assertUniforms(int referenceLocation, int functionLocation, float reference, int function) {
        final FloatBuffer floats = BufferUtils.createFloatBuffer(1);
        final IntBuffer ints = BufferUtils.createIntBuffer(1);
        GL20.glGetUniform(program, referenceLocation, floats);
        GL20.glGetUniform(program, functionLocation, ints);
        assertEquals(reference, floats.get(0));
        assertEquals(function, ints.get(0));
    }

    private static FloatUniform newFloatUniform(int location, FloatSupplier value, ValueUpdateNotifier notifier) {
        try {
            final Constructor<FloatUniform> ctor = FloatUniform.class.getDeclaredConstructor(int.class, FloatSupplier.class, ValueUpdateNotifier.class);
            ctor.setAccessible(true);
            return ctor.newInstance(location, value, notifier);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static IntUniform newIntUniform(int location, IntSupplier value, ValueUpdateNotifier notifier) {
        try {
            final Constructor<IntUniform> ctor = IntUniform.class.getDeclaredConstructor(int.class, IntSupplier.class, ValueUpdateNotifier.class);
            ctor.setAccessible(true);
            return ctor.newInstance(location, value, notifier);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static int createProgram() {
        final int vertex = compile(GL20.GL_VERTEX_SHADER, "alphaRestoreTest.vsh");
        final int fragment = compile(GL20.GL_FRAGMENT_SHADER, "alphaRestoreTest.fsh");
        final int result = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(result, vertex);
        GLStateManager.glAttachShader(result, fragment);
        GLStateManager.glLinkProgram(result);
        assertEquals(GL11.GL_TRUE, GLStateManager.glGetProgrami(result, GL20.GL_LINK_STATUS));
        GLStateManager.glDeleteShader(vertex);
        GLStateManager.glDeleteShader(fragment);
        return result;
    }

    private static int compile(int type, String name) {
        final int shader = GLStateManager.glCreateShader(type);
        GLStateManager.glShaderSource(shader, ShaderLoader.getShaderSource("angelica:" + name));
        GLStateManager.glCompileShader(shader);
        assertEquals(GL11.GL_TRUE, GLStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS));
        return shader;
    }
}

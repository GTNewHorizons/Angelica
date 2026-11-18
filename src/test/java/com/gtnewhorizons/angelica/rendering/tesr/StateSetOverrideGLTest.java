package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.AlphaTestFunction;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import com.gtnewhorizons.angelica.glsm.states.DepthState;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.coderbot.iris.gl.blending.AlphaTest;
import net.coderbot.iris.gl.blending.AlphaTestStorage;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import net.coderbot.iris.gl.uniform.FloatSupplier;
import net.coderbot.iris.gl.uniform.FloatUniform;
import net.coderbot.iris.gl.uniform.IntUniform;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        ProgramUniforms.clearActiveUniforms();
        BlendModeStorage.restoreBlend();
        AlphaTestStorage.restoreAlphaTest();
        DepthColorStorage.unlockDepthColor();
        VanillaLayerRig.clear();
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMRasterizer.disableCull();
        GLSMPrograms.glUseProgram(0);
        if (program != 0) GLSMPrograms.glDeleteProgram(program);
        if (otherProgram != 0) GLSMPrograms.glDeleteProgram(otherProgram);
    }

    private static int[] driverBlendFunc() {
        return new int[] {GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
            GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)};
    }

    private static boolean[] driverColorMask() {
        final ByteBuffer mask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, mask);
        return new boolean[] {mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0};
    }

    /** Vanilla blend is enabled SRC_ALPHA/ONE_MINUS_SRC_ALPHA/ONE/ZERO under an Iris override. */
    private static void overrideVanillaBlend() {
        VanillaLayerRig.installBlend();

        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        BlendModeStorage.overrideBlend(new BlendState(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE));
    }

    /** Vanilla alpha test is enabled GREATER 0.5 under an Iris override. */
    private static void overrideVanillaAlpha() {
        VanillaLayerRig.installAlpha();

        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        AlphaTestStorage.overrideAlphaTest(new AlphaTest(AlphaTestFunction.LEQUAL, 0.25f));
    }

    static Stream<Arguments> blendFuncSetters() {
        return Stream.of(
            Arguments.of("glBlendFunc", (Runnable) () -> GLSMBlend.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO)),
            Arguments.of("tryBlendFuncSeparate", (Runnable) () -> GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_DST_COLOR, GL11.GL_ZERO)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blendFuncSetters")
    void blendBracketUnderOverrideRestoresVanillaShadowAndLeavesLiveStateUntouched(String name, Runnable setBlendFunc) {
        overrideVanillaBlend();

        final boolean liveEnabledBefore = GLSMBlend.getBlendMode().isEnabled();
        final BlendState liveBefore = new BlendState().set(GLSMBlend.getBlendState());
        final boolean driverEnabledBefore = GL11.glIsEnabled(GL11.GL_BLEND);
        final int[] driverFuncBefore = driverBlendFunc();
        assertTrue(driverEnabledBefore, "driver blend enable carries the override");
        assertArrayEquals(new int[] {GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE}, driverFuncBefore, "driver blend func carries the override");

        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        final boolean vanillaEnabledBefore = BlendModeStorage.ENABLE_LAYER.getVanilla();
        GLSMBlend.disableBlend();
        assertNotEquals(vanillaEnabledBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "the defer branch changes the vanilla shadow immediately");
        setBlendFunc.run();
        GLSMAttribStack.popStateTo(d);

        assertEquals(driverEnabledBefore, GL11.glIsEnabled(GL11.GL_BLEND), "driver blend enable left untouched by the bracket");
        assertArrayEquals(driverFuncBefore, driverBlendFunc(), "driver blend func left untouched by the bracket");

        assertTrue(BlendModeStorage.ENABLE_LAYER.getVanilla(), "vanilla shadow enable restored to the pre-bracket value");
        final BlendState vanillaAfter = new BlendState();
        BlendModeStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(GL11.GL_SRC_ALPHA, vanillaAfter.getSrcRgb(), "vanilla shadow src rgb restored");
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, vanillaAfter.getDstRgb(), "vanilla shadow dst rgb restored");
        assertEquals(GL11.GL_ONE, vanillaAfter.getSrcAlpha(), "vanilla shadow src alpha restored");
        assertEquals(GL11.GL_ZERO, vanillaAfter.getDstAlpha(), "vanilla shadow dst alpha restored");

        assertEquals(liveEnabledBefore, GLSMBlend.getBlendMode().isEnabled(), "live overridden enable left untouched by the bracket");
        assertEquals(liveBefore.getSrcRgb(), GLSMBlend.getBlendState().getSrcRgb(), "live overridden src rgb left untouched");
        assertEquals(liveBefore.getDstRgb(), GLSMBlend.getBlendState().getDstRgb(), "live overridden dst rgb left untouched");
        assertEquals(liveBefore.getSrcAlpha(), GLSMBlend.getBlendState().getSrcAlpha(), "live overridden src alpha left untouched");
        assertEquals(liveBefore.getDstAlpha(), GLSMBlend.getBlendState().getDstAlpha(), "live overridden dst alpha left untouched");
    }

    @Test
    void alphaBracketUnderOverrideRestoresVanillaShadowAndLeavesLiveStateUntouched() {
        overrideVanillaAlpha();

        final boolean liveEnabledBefore = GLSMDepthStencilAlpha.getAlphaTest().isEnabled();
        final int liveFuncBefore = GLSMDepthStencilAlpha.getAlphaState().getFunction();
        final float liveRefBefore = GLSMDepthStencilAlpha.getAlphaState().getReference();

        final int d = GLSMAttribStack.pushState(StateSet.ALPHA);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.1f);
        final int[] calls = { 0 };
        final AlphaState shadow = new AlphaState();
        listener = () -> {
            calls[0]++;
            assertEquals(GL11.GL_LEQUAL, GLSMDepthStencilAlpha.getAlphaState().getFunction());
            assertEquals(0.25f, GLSMDepthStencilAlpha.getAlphaState().getReference());
            assertTrue(GLSMDepthStencilAlpha.isEffectiveAlphaTestEnabled());
            assertEquals(GL11.GL_GREATER, GLSMDepthStencilAlpha.getEffectiveAlphaState(shadow).getFunction());
            assertEquals(0.5f, shadow.getReference());
        };
        GLSMAttribStack.popStateTo(d);
        assertEquals(1, calls[0]);

        assertTrue(AlphaTestStorage.ENABLE_LAYER.getVanilla(), "vanilla shadow enable restored to the pre-bracket value");
        final AlphaState vanillaAfter = new AlphaState();
        AlphaTestStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(GL11.GL_GREATER, vanillaAfter.getFunction(), "vanilla shadow func restored");
        assertEquals(0.5f, vanillaAfter.getReference(), 0.0001f, "vanilla shadow ref restored");

        assertEquals(liveEnabledBefore, GLSMDepthStencilAlpha.getAlphaTest().isEnabled(), "live overridden enable left untouched by the bracket");
        assertEquals(liveFuncBefore, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "live overridden func left untouched");
        assertEquals(liveRefBefore, GLSMDepthStencilAlpha.getAlphaState().getReference(), 0.0001f, "live overridden ref left untouched");
    }

    @Test
    void masksBracketUnderOverrideRestoresVanillaShadowAndLeavesLiveStateUntouched() {
        VanillaLayerRig.installDepthColor();

        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glColorMask(true, false, true, false);

        DepthColorStorage.disableDepthColor();

        final boolean liveMaskBefore = GLSMDepthStencilAlpha.getDepthState().isEnabled();
        final ColorMask liveColorBefore = new ColorMask().set(GLSMBlend.getColorMask());
        final boolean driverDepthBefore = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final boolean[] driverColorBefore = driverColorMask();
        assertFalse(driverDepthBefore, "driver depth mask carries the held-off value");
        assertFalse(driverColorBefore[0] || driverColorBefore[1] || driverColorBefore[2] || driverColorBefore[3], "driver color mask carries the held-off value");

        final int d = GLSMAttribStack.pushState(StateSet.MASKS);
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMAttribStack.popStateTo(d);

        assertEquals(driverDepthBefore, GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "driver depth mask left untouched by the bracket");
        assertArrayEquals(driverColorBefore, driverColorMask(), "driver color mask left untouched by the bracket");

        final DepthState vanillaDepthAfter = new DepthState();
        DepthColorStorage.DEPTH_LAYER.readVanilla(vanillaDepthAfter);
        assertTrue(vanillaDepthAfter.isEnabled(), "vanilla shadow depth mask restored to the pre-bracket value");

        final ColorMask vanillaColorAfter = new ColorMask();
        DepthColorStorage.COLOR_LAYER.readVanilla(vanillaColorAfter);
        assertTrue(vanillaColorAfter.red, "vanilla shadow color red restored");
        assertFalse(vanillaColorAfter.green, "vanilla shadow color green restored");
        assertTrue(vanillaColorAfter.blue, "vanilla shadow color blue restored");
        assertFalse(vanillaColorAfter.alpha, "vanilla shadow color alpha restored");

        assertEquals(liveMaskBefore, GLSMDepthStencilAlpha.getDepthState().isEnabled(), "live overridden depth mask left untouched by the bracket");
        assertEquals(liveColorBefore.red, GLSMBlend.getColorMask().red, "live overridden color red left untouched");
        assertEquals(liveColorBefore.green, GLSMBlend.getColorMask().green, "live overridden color green left untouched");
        assertEquals(liveColorBefore.blue, GLSMBlend.getColorMask().blue, "live overridden color blue left untouched");
        assertEquals(liveColorBefore.alpha, GLSMBlend.getColorMask().alpha, "live overridden color alpha left untouched");
    }

    @Test
    void untouchedBracketUnderOverrideLeavesVanillaShadowIntact() {
        overrideVanillaBlend();

        final boolean vanillaEnabledBefore = BlendModeStorage.ENABLE_LAYER.getVanilla();
        final BlendState vanillaBefore = new BlendState();
        BlendModeStorage.FUNC_LAYER.readVanilla(vanillaBefore);
        final boolean liveEnabledBefore = GLSMBlend.getBlendMode().isEnabled();
        final BlendState liveBefore = new BlendState().set(GLSMBlend.getBlendState());

        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMAttribStack.popStateTo(d);

        assertEquals(vanillaEnabledBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "untouched bracket leaves the vanilla enable shadow intact");
        final BlendState vanillaAfter = new BlendState();
        BlendModeStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(vanillaBefore.getSrcRgb(), vanillaAfter.getSrcRgb(), "untouched bracket leaves the vanilla func shadow intact");
        assertEquals(liveEnabledBefore, GLSMBlend.getBlendMode().isEnabled(), "untouched bracket leaves the live overridden enable intact");
        assertEquals(liveBefore.getSrcRgb(), GLSMBlend.getBlendState().getSrcRgb(), "untouched bracket leaves the live overridden func intact");
    }

    @Test
    void overrideAcquiredMidBracket() {
        VanillaLayerRig.installAlpha();

        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);

        final int d = GLSMAttribStack.pushState(StateSet.ALPHA);
        AlphaTestStorage.overrideAlphaTest(new AlphaTest(AlphaTestFunction.LEQUAL, 0.25f));

        assertEquals(GL11.GL_LEQUAL, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "the override value is visible live immediately after acquiring mid-bracket");

        GLSMAttribStack.popStateTo(d);

        assertEquals(GL11.GL_LEQUAL, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "the live cache still shows the override value after pop: the vanilla-layer-aware restore writes the pre-bracket baseline into the vanilla shadow, not the live cache, while the override is held");
        final AlphaState vanillaAfter = new AlphaState();
        AlphaTestStorage.FUNC_LAYER.readVanilla(vanillaAfter);
        assertEquals(GL11.GL_GREATER, vanillaAfter.getFunction(), "the vanilla shadow is restored to the pre-bracket baseline by the bracket pop");
        assertEquals(0.5f, vanillaAfter.getReference(), 0.0001f, "the vanilla shadow reference is restored to the pre-bracket baseline");
        assertTrue(AlphaTestStorage.ENABLE_LAYER.getVanilla(), "the vanilla enable shadow still reflects the pre-bracket baseline captured at acquire");
    }

    @Test
    void overrideReleasedMidBracket() {
        overrideVanillaAlpha();

        assertEquals(GL11.GL_LEQUAL, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "the override value is visible live before the bracket opens");

        final int d = GLSMAttribStack.pushState(StateSet.ALPHA);
        AlphaTestStorage.restoreAlphaTest();

        assertEquals(GL11.GL_GREATER, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "releasing mid-bracket writes the vanilla value back through the live path immediately");

        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_NEVER, 0.9f);

        GLSMAttribStack.popStateTo(d);

        assertEquals(GL11.GL_GREATER, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "once released, the bracket pop restores the live cache to the vanilla baseline captured at push time through the vanilla-layer-aware slot, proving the pop actually acted rather than the assertion coincidentally already holding");
    }

    @Test
    void nonMemberBracketUnderOverrideCarriesToParent() {
        overrideVanillaBlend();

        final boolean vanillaBefore = BlendModeStorage.ENABLE_LAYER.getVanilla();

        final int outer = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMAttribStack.pushState(StateSet.CULL);

        GLSMBlend.disableBlend();
        assertNotEquals(vanillaBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "the defer branch changes the vanilla shadow immediately");

        GLSMAttribStack.popState();

        assertNotEquals(vanillaBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "a non-member bracket pop does not restore the change, it carries to the parent");

        GLSMAttribStack.popStateTo(outer);

        assertEquals(vanillaBefore, BlendModeStorage.ENABLE_LAYER.getVanilla(), "the enclosing member bracket pop restores the change that carried through the non-member pop");
    }

    @Test
    void functionEnableAndCombinedRestorationPostExactlyOnce() {
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        for (int changes = 1; changes <= 3; changes++) {
            final int depth = GLSMAttribStack.pushState(StateSet.ALPHA);
            if ((changes & 1) != 0) GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.25f);
            if ((changes & 2) != 0) GLSMDepthStencilAlpha.disableAlphaTest();
            final int[] calls = { 0 };
            listener = () -> {
                calls[0]++;
                assertFalse(GLSMAttribStack.isPoppingAttributes());
                assertEquals(depth, GLSMAttribStack.getAttribDepth());
                assertTrue(GLSMDepthStencilAlpha.getAlphaTest().isEnabled());
                assertEquals(GL11.GL_GREATER, GLSMDepthStencilAlpha.getAlphaState().getFunction());
                assertEquals(0.5f, GLSMDepthStencilAlpha.getAlphaState().getReference());
                final int nested = GLSMAttribStack.pushState(StateSet.ALPHA);
                GLSMAttribStack.popStateTo(nested);
            };
            GLSMAttribStack.popStateTo(depth);
            assertEquals(1, calls[0]);
            listener = null;
        }
    }

    @Test
    void unchangedAndNonmemberPopsDoNotPostAndOuterMemberDoes() {
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        final int[] calls = { 0 };
        listener = () -> calls[0]++;
        final int outer = GLSMAttribStack.pushState(StateSet.ALPHA);
        final int unchanged = GLSMAttribStack.pushState(StateSet.ALPHA);
        GLSMAttribStack.popStateTo(unchanged);
        assertEquals(0, calls[0]);
        final int inner = GLSMAttribStack.pushState(StateSet.CULL);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.25f);
        calls[0] = 0;
        GLSMAttribStack.popStateTo(inner);
        assertEquals(0, calls[0]);
        GLSMAttribStack.popStateTo(outer);
        assertEquals(1, calls[0]);
    }

    @Test
    void dynamicUniformsUpdateWithoutRebindingAndAfterProgramRestoration() {
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        program = createProgram();
        otherProgram = createProgram();
        GLSMPrograms.glUseProgram(program);
        final int referenceLocation = GLSMPrograms.glGetUniformLocation(program, "alphaReference");
        final int functionLocation = GLSMPrograms.glGetUniformLocation(program, "alphaFunction");
        Reflect.setStatic(ProgramUniforms.class, "active", ProgramUniforms.builder("alpha", program).buildUniforms());
        final Runnable[] updates = new Runnable[2];
        final FloatUniform reference = newFloatUniform(referenceLocation, () -> GLSMDepthStencilAlpha.getAlphaState().getReference(), update -> updates[0] = update);
        final IntUniform function = newIntUniform(functionLocation, () -> GLSMDepthStencilAlpha.getAlphaState().getFunction(), update -> updates[1] = update);
        reference.update();
        function.update();
        listener = () -> {
            assertEquals(program, GLSMPrograms.getActiveProgram());
            assertEquals(program, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            assertFalse(GLSMAttribStack.isPoppingAttributes());
            updates[0].run();
            updates[1].run();
        };
        final int depth = GLSMAttribStack.pushState(StateSet.ALPHA);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.25f);
        assertUniforms(referenceLocation, functionLocation, 0.25f, GL11.GL_LESS);
        GLSMAttribStack.popStateTo(depth);
        assertUniforms(referenceLocation, functionLocation, 0.5f, GL11.GL_GREATER);
        final int saved = GLSMAttribStack.pushState(StateSet.FONT);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMPrograms.glUseProgram(otherProgram);
        GLSMAttribStack.popStateTo(saved);
        assertTrue(GLSMDepthStencilAlpha.getAlphaTest().isEnabled());
        assertEquals(program, GLSMPrograms.getActiveProgram());
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
        return GlTestSupport.program(ShaderLoader.getShaderSource("angelica:alphaRestoreTest.vsh"), ShaderLoader.getShaderSource("angelica:alphaRestoreTest.fsh"));
    }
}

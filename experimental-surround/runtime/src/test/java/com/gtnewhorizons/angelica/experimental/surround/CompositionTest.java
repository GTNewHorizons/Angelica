package com.gtnewhorizons.angelica.experimental.surround;

import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;
import com.llamalad7.mixinextras.utils.MixinInternals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.WRAP;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.of;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.scenario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionTest {

    private static final Set<String> RUN_BEFORE = Set.of("LateInjectionApplicatorExtension", "SugarPostProcessingExtension", "WrapMethodApplicatorExtension");
    private static final String CHECK_CLASS = "ExtensionCheckClass";

    @BeforeEach
    void resetTrace() {
        Trace.reset();
    }

    private static Object wrap(String name, Object... args) throws Exception {
        return MixinTestBootstrap.call(WRAP, name, args);
    }

    static Stream<Arguments> mixinExtras() {
        return Stream.of(
            scenario(WRAP, "the surround is outside @WrapMethod", "wrapped", of(3), of(6), "entry 3", "wrap-before 3", "body 3", "wrap-after 6", "finally"),
            scenario(WRAP, "the entry sees the operand before @WrapOperation shifts it", "originalOperands", of(5), of(210), "enter 5", "wrap 5", "twice 105", "exit 5"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mixinExtras")
    @Tag("mixinextras")
    void mixinExtras(String method, String target, Object[] inputs, Object[] outputs, String[] events) throws Exception {
        MixinTestBootstrap.assertScenario(target, method, inputs, outputs, events);
    }

    @Test
    @Tag("mixinextras")
    void aLocalFollowsTheBodyWhenAShareShiftsItsSlot() throws Exception {
        assertEquals(10, wrap("shifted", 3));
        Trace.assertEvents("wrap 3", "enterShifted 4 4 L4", "twice 4", "exitShifted 4 L4");
        MethodNode body = null;
        for (MethodNode method : MixinTestBootstrap.transformedNode(WRAP).methods) {
            body = method.name.startsWith("shifted$") ? method : body;
        }
        assertTrue(body != null && body.desc.startsWith("(IL"), "the body did not move and gain the share");
    }

    @Test
    @Tag("mixinextras")
    void ourExtensionRunsAfterMixinExtrasAndBeforeCheckClass() throws Exception {
        MixinTestBootstrap.transformer();
        final SurroundApplicatorExtension ours = SurroundApplicatorExtension.instance();
        MixinTestBootstrap.transform(WRAP);
        final Extensions extensions = MixinInternals.getExtensions();
        for (List<IExtension> list : List.of(extensions.getExtensions(), extensions.getActiveExtensions())) {
            final int at = assertSlot(list, ours);
            for (String name : RUN_BEFORE) {
                final int index = names(list).indexOf(name);
                assertTrue(index >= 0 && index < at, names(list).toString());
                assertTrue(list.get(index).getClass().getName().startsWith("com.llamalad7.mixinextras."));
            }
        }
        assertCheckClassActiveUnderVerify(extensions);
    }

    @Test
    void anUnrenamedSurroundHandlerIsFatal() {
        final String mixin = "com.example.mixin.MixinLate";
        final MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, "surround$enter", "()V", null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.visitAnnotation(Type.getDescriptor(Surround.class), true).visitEnd();
        method.visitAnnotation(Type.getDescriptor(MixinMerged.class), true).visit("mixin", mixin);
        final ClassNode target = new ClassNode();
        target.version = Opcodes.V1_8;
        target.access = Opcodes.ACC_PUBLIC;
        target.name = "com/example/Target";
        target.superName = "java/lang/Object";
        target.methods.add(method);

        final String message = assertThrows(IllegalStateException.class, () -> SurroundApplicatorExtension.checkHandlersRenamed(target, Set.of())).getMessage();
        assertTrue(message.startsWith("surround$enter()V in com/example/Target (merged from " + mixin + ") carries @Surround but was never parsed as an injector"), message);
        assertTrue(message.contains("SurroundBootstrap.init() must run before"), message);
    }

    private static void assertCheckClassActiveUnderVerify(Extensions extensions) {
        final List<String> active = names(extensions.getActiveExtensions());
        assertEquals(Boolean.getBoolean("mixin.debug.verify"), active.contains(CHECK_CLASS), active.toString());
    }

    private static List<String> names(List<IExtension> list) {
        final List<String> names = new ArrayList<>(list.size());
        for (IExtension extension : list) {
            names.add(extension.getClass().getSimpleName());
        }
        return names;
    }

    private static int assertSlot(List<IExtension> list, IExtension ours) {
        final List<String> names = names(list);
        assertEquals(list.indexOf(ours), list.lastIndexOf(ours), "registered twice: " + names);
        final int at = list.indexOf(ours);
        assertTrue(at >= 0, "missing: " + names);
        for (String after : names.subList(at + 1, names.size())) {
            assertFalse(RUN_BEFORE.contains(after), after + " runs after us: " + names);
        }
        final int check = names.indexOf(CHECK_CLASS);
        assertTrue(check < 0 || check > at, CHECK_CLASS + " runs before us: " + names);
        return at;
    }
}

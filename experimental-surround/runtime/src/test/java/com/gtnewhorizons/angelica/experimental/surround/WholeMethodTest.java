package com.gtnewhorizons.angelica.experimental.surround;

import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.LineNumberNode;
import org.spongepowered.asm.lib.tree.LocalVariableNode;
import org.spongepowered.asm.lib.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.WHOLE;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WholeMethodTest {

    @BeforeEach
    void resetTrace() {
        Trace.reset();
    }

    @Test
    void oneEntryHandlerCanSurroundSeveralTargets() throws Exception {
        call(WHOLE, "alpha");
        call(WHOLE, "beta");
        Trace.assertEvents("enterBoth 1", "alpha", "exitBoth 1", "enterBoth 2", "beta", "exitBoth 2");
    }

    @Test
    void theWrapperKeepsTheEntryHandlersLinesAndTheBodyKeepsItsOwn() {
        final ClassNode merged = MixinTestBootstrap.transformedNode(WHOLE);
        final List<Integer> entryLines = lines(bySuffix(merged, "$surround$enterBranching"));
        assertFalse(entryLines.isEmpty(), "fixture compiled without line numbers");
        assertEquals(entryLines, lines(MixinTestBootstrap.method(merged, "branching")));
        assertEquals(lines(MixinTestBootstrap.method(MixinTestBootstrap.originalNode(WHOLE), "branching")), lines(MixinTestBootstrap.method(merged, "branching$surround")));
    }

    @Test
    void aReusedSlotNamesTheCarriedLocal() {
        final MethodNode wrapper = MixinTestBootstrap.method(MixinTestBootstrap.transformedNode(WHOLE), "reused");
        LabelNode last = null;
        for (AbstractInsnNode insn = wrapper.instructions.getLast(); last == null; insn = insn.getPrevious()) {
            last = insn instanceof LabelNode label ? label : null;
        }
        final List<String> toEnd = new ArrayList<>();
        for (LocalVariableNode local : wrapper.localVariables) {
            if (local.end == last) {
                toEnd.add(local.name);
            }
        }
        assertTrue(toEnd.contains("prev") && !toEnd.contains("tmp"), "locals live to the end: " + toEnd);
    }

    @Test
    void theWrapperMatchesTheGoldens() {
        MixinTestBootstrap.assertGolden(WHOLE, "skipAndReturn", "whole-skip-return");
    }

    @Test
    @EnabledIfSystemProperty(named = "surround.test.obf", matches = "true")
    void theLaunchClassLoaderTransformersSeeTheMixinClass() throws Exception {
        MixinTestBootstrap.transform(WHOLE);
        final Set<?> seen = (Set<?>) Launch.classLoader.loadClass(LocalSortingTransformer.class.getName()).getField("SEEN").get(null);
        assertTrue(seen.contains("com.gtnewhorizons.angelica.experimental.surround.integration.mixin.MixinWholeTarget"), seen.toString());
    }

    private static MethodNode bySuffix(ClassNode node, String suffix) {
        for (MethodNode method : node.methods) {
            if (method.name.endsWith(suffix)) {
                return method;
            }
        }
        final List<String> names = new ArrayList<>();
        for (MethodNode method : node.methods) {
            names.add(method.name);
        }
        throw new AssertionError("no method ending " + suffix + " in " + names);
    }

    private static List<Integer> lines(MethodNode method) {
        final List<Integer> lines = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LineNumberNode ln) {
                lines.add(ln.line);
            }
        }
        return lines;
    }
}

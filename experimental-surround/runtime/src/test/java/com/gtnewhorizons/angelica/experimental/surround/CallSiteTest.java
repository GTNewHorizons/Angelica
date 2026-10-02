package com.gtnewhorizons.angelica.experimental.surround;

import com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.Entity;
import com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.ReportedException;
import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.TryCatchBlockNode;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.CALL;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.call;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.method;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.transformedNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallSiteTest {

    @BeforeEach
    void resetTrace() {
        Trace.reset();
    }

    @Test
    void aRedirectedCallInsideJavacTryCatchesRethrowsInsideTheEnclosingTry() throws Exception {
        assertEquals(true, call(CALL, "render", new Entity("pig"), 1.0d, 2.0d, 3.0d, 0.5f, 0.25f, true));
        final Entity creeper = new Entity("creeper");
        creeper.explode = true;
        final InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> call(CALL, "render", creeper, 1.0d, 2.0d, 3.0d, 0.5f, 0.25f, false));

        final ReportedException outer = (ReportedException) thrown.getCause();
        assertEquals("Rendering entity (outer)", outer.getMessage());
        assertEquals("Rendering entity in world", outer.getCause().getMessage());
        assertSame(RuntimeException.class, outer.getCause().getCause().getClass());
        assertEquals("gpu", outer.getCause().getCause().getMessage());
        Trace.assertEvents("enterDoRender pig 1", "redirect pig", "doRender pig 1.0 0.25", "exitDoRender pig 1", "shadow pig", "boundingBox pig", "enterDoRender creeper 2", "redirect creeper", "doRender creeper 1.0 0.25", "caughtDoRender creeper gpu 2", "exitDoRender creeper 2");
    }

    @Test
    void theSurroundedCallMatchesTheGoldens() {
        MixinTestBootstrap.assertGolden(CALL, "valueSkip", "call-skip");
        MixinTestBootstrap.assertGolden(CALL, "valueBelow", "call-handle");
    }

    @Test
    void nestedSurroundsShareOneCallAndOrderTheirTryRangesInnerFirst() {
        final MethodNode target = method(transformedNode(CALL), "innerSkipOuterReturn");
        int calls = 0;
        for (AbstractInsnNode insn : target.instructions.toArray()) {
            calls += insn instanceof MethodInsnNode m && m.name.equals("twice") ? 1 : 0;
        }
        assertEquals(1, calls);
        final List<TryCatchBlockNode> blocks = target.tryCatchBlocks;
        boolean nests = false;
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = 0; j < blocks.size(); j++) {
                final boolean encloses = encloses(target, blocks.get(i), blocks.get(j));
                nests |= encloses;
                assertTrue(!encloses || i > j, "block " + i + " encloses block " + j);
            }
        }
        assertTrue(nests, "no try block encloses another");
    }

    private static boolean encloses(MethodNode target, TryCatchBlockNode outer, TryCatchBlockNode inner) {
        final int outerStart = target.instructions.indexOf(outer.start);
        final int outerEnd = target.instructions.indexOf(outer.end);
        final int innerStart = target.instructions.indexOf(inner.start);
        final int innerEnd = target.instructions.indexOf(inner.end);
        return outerStart <= innerStart && innerEnd <= outerEnd && (outerStart < innerStart || innerEnd < outerEnd);
    }
}

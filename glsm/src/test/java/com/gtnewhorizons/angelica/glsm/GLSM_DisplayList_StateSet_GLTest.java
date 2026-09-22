package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyIsEnabled;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@GLCoreTest
public class GLSM_DisplayList_StateSet_GLTest {

    private final List<Integer> listsToDelete = new ArrayList<>();

    @AfterEach
    void cleanup() {
        DisplayListManager.abortCompilation();
        while (GLStateManager.getAttribDepth() > 0) {
            GLStateManager.popState();
        }
        for (int id : listsToDelete) {
            GLStateManager.glDeleteLists(id, 1);
        }
        listsToDelete.clear();
        GLStateManager.disableBlend();
        GLStateManager.disableCull();
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.disableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.disableTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private int newList() {
        final int id = GLStateManager.glGenLists(1);
        listsToDelete.add(id);
        return id;
    }

    private static Int2IntMap commandCounts(int listId) {
        final CompiledDisplayList compiled = DisplayListManager.getDisplayList(listId);
        assertNotNull(compiled, "compiled display list must exist for id " + listId);
        return compiled.getCommandCounts();
    }

    @Test
    void compileBracketDoesNotThrowAndLeavesLiveStateUntouched() {
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

        final int func0 = GLStateManager.getAlphaState().getFunction();
        final float ref0 = GLStateManager.getAlphaState().getReference();
        final int unit0 = GLStateManager.getActiveTextureUnit();
        final int depth0 = GLStateManager.getAttribDepth();

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        final int d = GLStateManager.pushState(StateSet.CUTOUT);
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.enableTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.popStateTo(d);
        GLStateManager.glEndList();

        verifyIsEnabled(GL11.GL_ALPHA_TEST, true, "cache alpha test enable unchanged after COMPILE-only bracket");
        assertEquals(func0, GLStateManager.getAlphaState().getFunction(), "cache alpha func unchanged after COMPILE-only bracket");
        assertEquals(ref0, GLStateManager.getAlphaState().getReference(), 0.0001f, "cache alpha ref unchanged after COMPILE-only bracket");
        assertEquals(unit0, GLStateManager.getActiveTextureUnit(), "cache active unit unchanged after COMPILE-only bracket");
        assertEquals(depth0, GLStateManager.getAttribDepth(), "live attrib depth unchanged after COMPILE-only bracket");
        assertEquals(2, commandCounts(list).getOrDefault(GLCommand.COMPLEX_REF, 0), "push and pop each record one COMPLEX_REF");
    }

    @Test
    void compiledBracketReplayRestoresState() {
        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        final int d = GLStateManager.pushState(StateSet.CUTOUT);
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.enableTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.popStateTo(d);
        GLStateManager.glEndList();

        GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.9f);
        GLStateManager.disableAlphaTest();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        final int depth0 = GLStateManager.getAttribDepth();

        GLStateManager.glCallList(list);

        assertEquals(GL11.GL_LESS, GLStateManager.getAlphaState().getFunction(), "cache alpha func restored by replay");
        assertEquals(0.9f, GLStateManager.getAlphaState().getReference(), 0.0001f, "cache alpha ref restored by replay");
        verifyIsEnabled(GL11.GL_ALPHA_TEST, false, "cache alpha test restored disabled by replay");
        assertEquals(0, GLStateManager.getActiveTextureUnit(), "cache active unit restored by replay");
        assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "driver active unit restored by replay");
        assertEquals(depth0, GLStateManager.getAttribDepth(), "live attrib depth unchanged by balanced replay");
    }

    @Test
    void compileAndExecuteBracketIsBalanced() {
        GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.9f);
        GLStateManager.disableAlphaTest();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        final int depth0 = GLStateManager.getAttribDepth();

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        final int d = GLStateManager.pushState(StateSet.CUTOUT);
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.enableTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.popStateTo(d);
        GLStateManager.glEndList();

        assertEquals(GL11.GL_LESS, GLStateManager.getAlphaState().getFunction(), "cache alpha func restored after GL_COMPILE_AND_EXECUTE bracket");
        assertEquals(0.9f, GLStateManager.getAlphaState().getReference(), 0.0001f, "cache alpha ref restored after GL_COMPILE_AND_EXECUTE bracket");
        verifyIsEnabled(GL11.GL_ALPHA_TEST, false, "cache alpha test restored disabled after GL_COMPILE_AND_EXECUTE bracket");
        assertEquals(0, GLStateManager.getActiveTextureUnit(), "cache active unit restored after GL_COMPILE_AND_EXECUTE bracket");
        assertEquals(depth0, GLStateManager.getAttribDepth(), "live attrib depth unchanged after balanced GL_COMPILE_AND_EXECUTE bracket");

        GLStateManager.glCallList(list);
        GLStateManager.glCallList(list);

        assertEquals(GL11.GL_LESS, GLStateManager.getAlphaState().getFunction(), "cache alpha func restored after replay");
        assertEquals(0.9f, GLStateManager.getAlphaState().getReference(), 0.0001f, "cache alpha ref restored after replay");
        verifyIsEnabled(GL11.GL_ALPHA_TEST, false, "cache alpha test restored disabled after replay");
        assertEquals(0, GLStateManager.getActiveTextureUnit(), "cache active unit restored after replay");
        assertEquals(depth0, GLStateManager.getAttribDepth(), "live attrib depth unchanged after replay");
    }

    @Test
    void nestedListsPushInBothLevels() {
        GLStateManager.enableBlend();
        GLStateManager.enableCull();
        GLStateManager.glCullFace(GL11.GL_BACK);
        final boolean cull0 = GLStateManager.glIsEnabled(GL11.GL_CULL_FACE);
        final int depth0 = GLStateManager.getAttribDepth();

        final int parent = newList();
        final int child = newList();

        GLStateManager.glNewList(parent, GL11.GL_COMPILE);
        final int dParent = GLStateManager.pushState(StateSet.BLEND);
        GLStateManager.glNewList(child, GL11.GL_COMPILE);
        final int dChild = GLStateManager.pushState(StateSet.CULL);
        if (cull0) {
            GLStateManager.disableCull();
        } else {
            GLStateManager.enableCull();
        }
        GLStateManager.popStateTo(dChild);
        GLStateManager.glEndList();
        GLStateManager.disableBlend();
        GLStateManager.popStateTo(dParent);
        GLStateManager.glEndList();

        assertEquals(depth0, GLStateManager.getAttribDepth(), "live attrib depth unchanged by nested COMPILE-only bracket");

        GLStateManager.glCallList(parent);
        verifyIsEnabled(GL11.GL_BLEND, true, "blend enable restored by the parent BLEND bracket after parent replay");
        verifyIsEnabled(GL11.GL_CULL_FACE, cull0, "cull enable restored after parent replay");

        GLStateManager.glCallList(child);
        verifyIsEnabled(GL11.GL_CULL_FACE, cull0, "cull enable restored after child replay");
    }

    @Test
    void nestedCompileAndExecuteChildInsideCompileParent() {
        GLStateManager.enableCull();
        GLStateManager.glCullFace(GL11.GL_BACK);
        final boolean cull0 = GLStateManager.glIsEnabled(GL11.GL_CULL_FACE);
        final int depth0 = GLStateManager.getAttribDepth();

        final int parent = newList();
        final int child = newList();

        GLStateManager.glNewList(parent, GL11.GL_COMPILE);
        final int dParent = GLStateManager.pushState(StateSet.BLEND);

        GLStateManager.glNewList(child, GL11.GL_COMPILE_AND_EXECUTE);
        final int dChild = GLStateManager.pushState(StateSet.CULL);
        if (cull0) {
            GLStateManager.disableCull();
        } else {
            GLStateManager.enableCull();
        }
        GLStateManager.popStateTo(dChild);
        GLStateManager.glEndList();

        verifyIsEnabled(GL11.GL_CULL_FACE, cull0, "cull enable restored live immediately after the COMPILE_AND_EXECUTE child bracket closes");

        GLStateManager.popStateTo(dParent);
        GLStateManager.glEndList();

        assertEquals(depth0, GLStateManager.getAttribDepth(), "live attrib depth unchanged after balanced nested bracket");
        assertEquals(2, commandCounts(parent).getOrDefault(GLCommand.COMPLEX_REF, 0), "parent list records exactly the BLEND push and pop");
        assertEquals(2, commandCounts(child).getOrDefault(GLCommand.COMPLEX_REF, 0), "child list records exactly the CULL push and pop");
    }

    @Test
    void unbalancedBracketIsClosedAtEndList() {
        GLStateManager.enableBlend();
        final boolean blend0 = GLStateManager.glIsEnabled(GL11.GL_BLEND);
        final int depth0 = GLStateManager.getAttribDepth();

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.pushState(StateSet.BATCH);
        GLStateManager.disableBlend();
        GLStateManager.glEndList();

        assertEquals(2, commandCounts(list).getOrDefault(GLCommand.COMPLEX_REF, 0), "glEndList closes the unbalanced push, recording a matching pop");

        GLStateManager.glCallList(list);
        assertEquals(depth0, GLStateManager.getAttribDepth(), "live attrib depth unchanged after replaying an auto-closed bracket");
        verifyIsEnabled(GL11.GL_BLEND, blend0, "blend enable unchanged after replaying an auto-closed bracket");

        final int other = newList();
        assertDoesNotThrow(() -> {
            GLStateManager.glNewList(other, GL11.GL_COMPILE);
            GLStateManager.glEndList();
        }, "an unrelated list must compile normally after an auto-closed bracket");
    }

    @Test
    void glPushAttribRecordsExactlyOnce() {
        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
        GLStateManager.glPopAttrib();
        GLStateManager.glEndList();

        final Int2IntMap counts = commandCounts(list);
        assertEquals(1, counts.getOrDefault(GLCommand.PUSH_ATTRIB, 0), "glPushAttrib records exactly one PUSH_ATTRIB opcode");
        assertEquals(1, counts.getOrDefault(GLCommand.POP_ATTRIB, 0), "glPopAttrib records exactly one POP_ATTRIB opcode");
        assertEquals(0, counts.getOrDefault(GLCommand.COMPLEX_REF, 0), "glPushAttrib/glPopAttrib never record a COMPLEX_REF");
    }

    @Test
    void livePushStraddlesOpenList() {
        GLStateManager.enableBlend();
        final int d = GLStateManager.pushState(StateSet.BLEND);
        GLStateManager.disableBlend();

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glEndList();

        GLStateManager.popStateTo(d);

        assertEquals(d, GLStateManager.getAttribDepth(), "live attrib depth restored to the pre-bracket value");
        verifyIsEnabled(GL11.GL_BLEND, true, "blend enable restored by the live pop that straddled the open-then-closed list");
    }

    @Test
    void livePopInsideOpenListPopsLiveAndRecordsNothing() {
        GLStateManager.enableBlend();
        final int d = GLStateManager.pushState(StateSet.BLEND);
        GLStateManager.disableBlend();

        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.popStateTo(d);
        GLStateManager.glEndList();

        assertEquals(d, GLStateManager.getAttribDepth(), "live attrib depth restored by the live pop performed inside the open list");
        verifyIsEnabled(GL11.GL_BLEND, true, "blend enable restored live by the pop of a pre-list push");
        assertEquals(0, commandCounts(list).getOrDefault(GLCommand.COMPLEX_REF, 0), "popping a pre-existing live push records nothing into the list");
    }

    @Test
    void virtualDepthOverflowThrowsAtRecordTime() {
        final int startDepth = GLStateManager.getAttribDepth();
        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);

        final int pushes = GLStateManager.MAX_ATTRIB_STACK_DEPTH - startDepth;
        for (int i = 0; i < pushes; i++) {
            GLStateManager.pushState(StateSet.CUTOUT);
        }
        assertThrows(IllegalStateException.class, () -> GLStateManager.pushState(StateSet.CUTOUT), "virtual depth overflow beyond MAX_ATTRIB_STACK_DEPTH must throw at record time");

        DisplayListManager.abortCompilation();

        assertEquals(startDepth, GLStateManager.getAttribDepth(), "aborting the overflowed compilation leaves live attrib depth untouched");
    }
}

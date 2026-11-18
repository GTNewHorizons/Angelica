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
        while (GLSMAttribStack.getAttribDepth() > 0) {
            GLSMAttribStack.popState();
        }
        for (int id : listsToDelete) {
            GLSMDraws.glDeleteLists(id, 1);
        }
        listsToDelete.clear();
        GLSMBlend.disableBlend();
        GLSMRasterizer.disableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.disableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private int newList() {
        final int id = GLSMDraws.glGenLists(1);
        listsToDelete.add(id);
        return id;
    }

    private int recordCutoutBracket(int mode) {
        final int list = newList();
        GLSMDraws.glNewList(list, mode);
        final int d = GLSMAttribStack.pushState(StateSet.CUTOUT);
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.enableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMAttribStack.popStateTo(d);
        GLSMDraws.glEndList();
        return list;
    }

    private static Int2IntMap commandCounts(int listId) {
        final CompiledDisplayList compiled = DisplayListManager.getDisplayList(listId);
        assertNotNull(compiled, "compiled display list must exist for id " + listId);
        return compiled.getCommandCounts();
    }

    @Test
    void compileBracketDoesNotThrowAndLeavesLiveStateUntouched() {
        GLSMDepthStencilAlpha.enableAlphaTest();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);

        final int func0 = GLSMDepthStencilAlpha.getAlphaState().getFunction();
        final float ref0 = GLSMDepthStencilAlpha.getAlphaState().getReference();
        final int unit0 = GLSMTextures.getActiveTextureUnit();
        final int depth0 = GLSMAttribStack.getAttribDepth();

        final int list = recordCutoutBracket(GL11.GL_COMPILE);

        verifyIsEnabled(GL11.GL_ALPHA_TEST, true, "cache alpha test enable unchanged after COMPILE-only bracket");
        assertEquals(func0, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "cache alpha func unchanged after COMPILE-only bracket");
        assertEquals(ref0, GLSMDepthStencilAlpha.getAlphaState().getReference(), 0.0001f, "cache alpha ref unchanged after COMPILE-only bracket");
        assertEquals(unit0, GLSMTextures.getActiveTextureUnit(), "cache active unit unchanged after COMPILE-only bracket");
        assertEquals(depth0, GLSMAttribStack.getAttribDepth(), "live attrib depth unchanged after COMPILE-only bracket");
        assertEquals(2, commandCounts(list).getOrDefault(GLCommand.COMPLEX_REF, 0), "push and pop each record one COMPLEX_REF");
    }

    @Test
    void compiledBracketReplayRestoresState() {
        final int list = recordCutoutBracket(GL11.GL_COMPILE);

        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.9f);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        final int depth0 = GLSMAttribStack.getAttribDepth();

        GLSMDraws.glCallList(list);

        assertEquals(GL11.GL_LESS, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "cache alpha func restored by replay");
        assertEquals(0.9f, GLSMDepthStencilAlpha.getAlphaState().getReference(), 0.0001f, "cache alpha ref restored by replay");
        verifyIsEnabled(GL11.GL_ALPHA_TEST, false, "cache alpha test restored disabled by replay");
        assertEquals(0, GLSMTextures.getActiveTextureUnit(), "cache active unit restored by replay");
        assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "driver active unit restored by replay");
        assertEquals(depth0, GLSMAttribStack.getAttribDepth(), "live attrib depth unchanged by balanced replay");
    }

    @Test
    void compileAndExecuteBracketIsBalanced() {
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.9f);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        final int depth0 = GLSMAttribStack.getAttribDepth();

        final int list = recordCutoutBracket(GL11.GL_COMPILE_AND_EXECUTE);

        assertEquals(GL11.GL_LESS, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "cache alpha func restored after GL_COMPILE_AND_EXECUTE bracket");
        assertEquals(0.9f, GLSMDepthStencilAlpha.getAlphaState().getReference(), 0.0001f, "cache alpha ref restored after GL_COMPILE_AND_EXECUTE bracket");
        verifyIsEnabled(GL11.GL_ALPHA_TEST, false, "cache alpha test restored disabled after GL_COMPILE_AND_EXECUTE bracket");
        assertEquals(0, GLSMTextures.getActiveTextureUnit(), "cache active unit restored after GL_COMPILE_AND_EXECUTE bracket");
        assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "driver active unit restored after GL_COMPILE_AND_EXECUTE bracket");
        assertEquals(depth0, GLSMAttribStack.getAttribDepth(), "live attrib depth unchanged after balanced GL_COMPILE_AND_EXECUTE bracket");

        GLSMDraws.glCallList(list);
        GLSMDraws.glCallList(list);

        assertEquals(GL11.GL_LESS, GLSMDepthStencilAlpha.getAlphaState().getFunction(), "cache alpha func restored after replay");
        assertEquals(0.9f, GLSMDepthStencilAlpha.getAlphaState().getReference(), 0.0001f, "cache alpha ref restored after replay");
        verifyIsEnabled(GL11.GL_ALPHA_TEST, false, "cache alpha test restored disabled after replay");
        assertEquals(0, GLSMTextures.getActiveTextureUnit(), "cache active unit restored after replay");
        assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "driver active unit restored after replay");
        assertEquals(depth0, GLSMAttribStack.getAttribDepth(), "live attrib depth unchanged after replay");
    }

    @Test
    void nestedListsPushInBothLevels() {
        GLSMBlend.enableBlend();
        GLSMRasterizer.enableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        final boolean cull0 = GLSMEnable.glIsEnabled(GL11.GL_CULL_FACE);
        final int depth0 = GLSMAttribStack.getAttribDepth();

        final int parent = newList();
        final int child = newList();

        GLSMDraws.glNewList(parent, GL11.GL_COMPILE);
        final int dParent = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMDraws.glNewList(child, GL11.GL_COMPILE);
        final int dChild = GLSMAttribStack.pushState(StateSet.CULL);
        if (cull0) {
            GLSMRasterizer.disableCull();
        } else {
            GLSMRasterizer.enableCull();
        }
        GLSMAttribStack.popStateTo(dChild);
        GLSMDraws.glEndList();
        GLSMBlend.disableBlend();
        GLSMAttribStack.popStateTo(dParent);
        GLSMDraws.glEndList();

        assertEquals(depth0, GLSMAttribStack.getAttribDepth(), "live attrib depth unchanged by nested COMPILE-only bracket");

        GLSMDraws.glCallList(parent);
        verifyIsEnabled(GL11.GL_BLEND, true, "blend enable restored by the parent BLEND bracket after parent replay");
        verifyIsEnabled(GL11.GL_CULL_FACE, cull0, "cull enable restored after parent replay");

        GLSMDraws.glCallList(child);
        verifyIsEnabled(GL11.GL_CULL_FACE, cull0, "cull enable restored after child replay");
    }

    @Test
    void nestedCompileAndExecuteChildInsideCompileParent() {
        GLSMRasterizer.enableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        final boolean cull0 = GLSMEnable.glIsEnabled(GL11.GL_CULL_FACE);
        final int depth0 = GLSMAttribStack.getAttribDepth();

        final int parent = newList();
        final int child = newList();

        GLSMDraws.glNewList(parent, GL11.GL_COMPILE);
        final int dParent = GLSMAttribStack.pushState(StateSet.BLEND);

        GLSMDraws.glNewList(child, GL11.GL_COMPILE_AND_EXECUTE);
        final int dChild = GLSMAttribStack.pushState(StateSet.CULL);
        if (cull0) {
            GLSMRasterizer.disableCull();
        } else {
            GLSMRasterizer.enableCull();
        }
        GLSMAttribStack.popStateTo(dChild);
        GLSMDraws.glEndList();

        verifyIsEnabled(GL11.GL_CULL_FACE, cull0, "cull enable restored live immediately after the COMPILE_AND_EXECUTE child bracket closes");

        GLSMAttribStack.popStateTo(dParent);
        GLSMDraws.glEndList();

        assertEquals(depth0, GLSMAttribStack.getAttribDepth(), "live attrib depth unchanged after balanced nested bracket");
        assertEquals(2, commandCounts(parent).getOrDefault(GLCommand.COMPLEX_REF, 0), "parent list records exactly the BLEND push and pop");
        assertEquals(2, commandCounts(child).getOrDefault(GLCommand.COMPLEX_REF, 0), "child list records exactly the CULL push and pop");
    }

    @Test
    void unbalancedBracketIsClosedAtEndList() {
        GLSMBlend.enableBlend();
        final boolean blend0 = GLSMEnable.glIsEnabled(GL11.GL_BLEND);
        final int depth0 = GLSMAttribStack.getAttribDepth();

        final int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMAttribStack.pushState(StateSet.BATCH);
        GLSMBlend.disableBlend();
        GLSMDraws.glEndList();

        assertEquals(2, commandCounts(list).getOrDefault(GLCommand.COMPLEX_REF, 0), "glEndList closes the unbalanced push, recording a matching pop");

        GLSMDraws.glCallList(list);
        assertEquals(depth0, GLSMAttribStack.getAttribDepth(), "live attrib depth unchanged after replaying an auto-closed bracket");
        verifyIsEnabled(GL11.GL_BLEND, blend0, "blend enable unchanged after replaying an auto-closed bracket");

        final int other = newList();
        assertDoesNotThrow(() -> {
            GLSMDraws.glNewList(other, GL11.GL_COMPILE);
            GLSMDraws.glEndList();
        }, "an unrelated list must compile normally after an auto-closed bracket");
    }

    @Test
    void glPushAttribRecordsExactlyOnce() {
        final int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
        GLSMAttribStack.glPopAttrib();
        GLSMDraws.glEndList();

        final Int2IntMap counts = commandCounts(list);
        assertEquals(1, counts.getOrDefault(GLCommand.PUSH_ATTRIB, 0), "glPushAttrib records exactly one PUSH_ATTRIB opcode");
        assertEquals(1, counts.getOrDefault(GLCommand.POP_ATTRIB, 0), "glPopAttrib records exactly one POP_ATTRIB opcode");
        assertEquals(0, counts.getOrDefault(GLCommand.COMPLEX_REF, 0), "glPushAttrib/glPopAttrib never record a COMPLEX_REF");
    }

    @Test
    void livePushStraddlesOpenList() {
        GLSMBlend.enableBlend();
        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMBlend.disableBlend();

        final int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMDraws.glEndList();

        GLSMAttribStack.popStateTo(d);

        assertEquals(d, GLSMAttribStack.getAttribDepth(), "live attrib depth restored to the pre-bracket value");
        verifyIsEnabled(GL11.GL_BLEND, true, "blend enable restored by the live pop that straddled the open-then-closed list");
    }

    @Test
    void livePopInsideOpenListPopsLiveAndRecordsNothing() {
        GLSMBlend.enableBlend();
        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMBlend.disableBlend();

        final int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMAttribStack.popStateTo(d);
        GLSMDraws.glEndList();

        assertEquals(d, GLSMAttribStack.getAttribDepth(), "live attrib depth restored by the live pop performed inside the open list");
        verifyIsEnabled(GL11.GL_BLEND, true, "blend enable restored live by the pop of a pre-list push");
        assertEquals(0, commandCounts(list).getOrDefault(GLCommand.COMPLEX_REF, 0), "popping a pre-existing live push records nothing into the list");
    }

    @Test
    void virtualDepthOverflowThrowsAtRecordTime() {
        final int startDepth = GLSMAttribStack.getAttribDepth();
        final int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);

        final int pushes = GLStateManager.MAX_ATTRIB_STACK_DEPTH - startDepth;
        for (int i = 0; i < pushes; i++) {
            GLSMAttribStack.pushState(StateSet.CUTOUT);
        }
        assertThrows(IllegalStateException.class, () -> GLSMAttribStack.pushState(StateSet.CUTOUT), "virtual depth overflow beyond MAX_ATTRIB_STACK_DEPTH must throw at record time");

        DisplayListManager.abortCompilation();

        assertEquals(startDepth, GLSMAttribStack.getAttribDepth(), "aborting the overflowed compilation leaves live attrib depth untouched");
    }
}

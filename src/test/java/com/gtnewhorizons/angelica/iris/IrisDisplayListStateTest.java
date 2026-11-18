package com.gtnewhorizons.angelica.iris;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.block_rendering.NbtConditionalIdMap;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.uniform.Uniform;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.pipeline.PipelineManager;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.CapturedStateScope;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.coderbot.iris.uniforms.ItemMaterialHelper;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@GLCoreTest
class IrisDisplayListStateTest {
    private final List<Integer> lists = new ArrayList<>();
    private final List<Integer> programs = new ArrayList<>();
    private int selectedProgram;

    @AfterEach
    void cleanup() {
        ProgramUniforms.clearActiveUniforms();
        if (DisplayListManager.isRecording()) GLSMDraws.glEndList();
        GLSMPrograms.glUseProgram(0);
        for (int list : lists) GLSMDraws.glDeleteLists(list, 1);
        for (int program : programs) GLSMPrograms.glDeleteProgram(program);
    }

    private int newList() {
        int list = GLSMDraws.glGenLists(1);
        lists.add(list);
        return list;
    }

    private int program() {
        int program = GlTestSupport.program(GlTestSupport.TRIVIAL_VS,
            "#version 330 core\nuniform int itemId; out vec4 color; void main() { color = vec4(float(itemId)); }");
        programs.add(program);
        return program;
    }

    private void selectProgram() {
        IrisDisplayListState.runProgramTransition(() -> GLSMPrograms.glUseProgram(selectedProgram));
    }

    @Test
    void cutoutScopesRestorePlaybackAlphaAndLightmapWithoutChangingItsActiveUnit() {
        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            for (int mode : new int[] {GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE}) {
                GLSMDepthStencilAlpha.disableAlphaTest();
                GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_LESS, 0.2f);
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
                GLSMTextures.disableTexture();
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE3);
                final int list = newList();
                GLSMDraws.glNewList(list, mode);
                final int outer = GLSMAttribStack.pushState(StateSet.CUTOUT);
                GbufferPrograms.setCutoutDefaults();
                final int inner = GLSMAttribStack.pushState(StateSet.CUTOUT);
                GbufferPrograms.setCutoutDefaults();
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
                GLSMTextures.disableTexture();
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
                GLSMAttribStack.popStateTo(inner);
                DisplayListManager.recordStateCommand(() -> assertTrue(GLSMTextures.getTextures().getTextureUnitStates(1).isEnabled()));
                GLSMAttribStack.popStateTo(outer);
                GLSMDraws.glEndList();

                for (boolean lightmap : new boolean[] {true, false}) {
                    GLSMDepthStencilAlpha.enableAlphaTest();
                    GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GEQUAL, 0.7f);
                    GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
                    if (lightmap) GLSMTextures.enableTexture(); else GLSMTextures.disableTexture();
                    GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
                    GLSMDraws.glCallList(list);
                    assertTrue(GLSMDepthStencilAlpha.isEffectiveAlphaTestEnabled());
                    assertEquals(GL11.GL_GEQUAL, GLSMDepthStencilAlpha.getAlphaState().getFunction());
                    assertEquals(0.7f, GLSMDepthStencilAlpha.getAlphaState().getReference());
                    assertEquals(lightmap, GLSMTextures.getTextures().getTextureUnitStates(1).isEnabled());
                    assertEquals(2, GLSMTextures.getActiveTextureUnit(), "scope restores must preserve the list's explicit unit selection");
                }
            }
        } finally {
            GLSMAttribStack.glPopAttrib();
        }
    }

    @Test
    void cutoutDefaultsOutsideListEnableLightmapWithoutSwitchingUnit() {
        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GLSMDepthStencilAlpha.enableAlphaTest();
            GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMTextures.disableTexture();
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE3);
            final int d = GLSMAttribStack.pushState(StateSet.CUTOUT);
            try {
                final long savedBefore = GLSMAttribStack.attribSlotsSaved;
                GbufferPrograms.setCutoutDefaults();
                assertEquals(1, GLSMAttribStack.attribSlotsSaved - savedBefore);
                assertTrue(GLSMTextures.getTextures().getTextureUnitStates(1).isEnabled());
                assertEquals(3, GLSMTextures.getActiveTextureUnit());
            } finally {
                GLSMAttribStack.popStateTo(d);
            }
            assertFalse(GLSMTextures.getTextures().getTextureUnitStates(1).isEnabled());
        } finally {
            GLSMAttribStack.glPopAttrib();
        }
    }

    @Test
    void nestedBlendScopesRestorePlaybackValuesEvenIfCompileTimePopWasANoop() {
        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            for (int mode : new int[] {GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE}) {
                GLSMBlend.disableBlend();
                GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
                final int child = newList();
                GLSMDraws.glNewList(child, mode);
                final int scope = GLSMAttribStack.pushState(StateSet.BLEND);
                GLSMBlend.disableBlend();
                GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
                GLSMAttribStack.popStateTo(scope);
                GLSMDraws.glEndList();
                final int parent = newList();
                GLSMDraws.glNewList(parent, mode);
                final int parentScope = GLSMAttribStack.pushState(StateSet.BLEND);
                GLSMDraws.glCallList(child);
                GLSMAttribStack.popStateTo(parentScope);
                GLSMDraws.glEndList();

                GLSMBlend.enableBlend();
                GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                final int caller = GLSMAttribStack.pushState(StateSet.BLEND);
                try {
                    GLSMDraws.glCallList(parent);
                    assertTrue(GLSMBlend.isEffectiveBlendEnabled());
                    assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "driver blend enable");
                    assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb");
                    assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), "driver blend dst rgb");
                    assertEquals(GL11.GL_ZERO, GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), "driver blend src alpha");
                    assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA), "driver blend dst alpha");
                    final var blend = GLSMBlend.getEffectiveBlendState(new com.gtnewhorizons.angelica.glsm.states.BlendState());
                    assertEquals(GL11.GL_SRC_ALPHA, blend.getSrcRgb());
                    assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, blend.getDstRgb());
                    assertEquals(GL11.GL_ZERO, blend.getSrcAlpha());
                    assertEquals(GL11.GL_ONE, blend.getDstAlpha());
                    assertEquals(caller + 1, GLSMAttribStack.getAttribDepth());
                } finally {
                    GLSMAttribStack.popStateTo(caller);
                }
            }
        } finally {
            GLSMAttribStack.glPopAttrib();
        }
    }

    @Test
    void idsAndColorReplayInsideNestedScopesAndRestoreThePlaybackCaller() {
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        try (CapturedStateScope ignored = new CapturedStateScope()) {
            for (int mode : new int[] {GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE}) {
                state.setCurrentEntityAndItem(10, 11);
                state.setCurrentBlockEntity(12);
                state.setCurrentEntityColor(1, 0, 0, 0.5f);
                final int child = newList();
                GLSMDraws.glNewList(child, mode);
                try (CapturedStateScope nestedIgnored = new CapturedStateScope()) {
                    state.setCurrentEntityAndItem(45010, 45011);
                    state.setCurrentBlockEntity(45012);
                    state.setCurrentEntityColor(0, 1, 0, 0.25f);
                    DisplayListManager.recordStateCommand(() -> {
                        assertEquals(45010, state.getCurrentRenderedEntity());
                        assertEquals(45011, state.getCurrentRenderedItem());
                        assertEquals(45012, state.getCurrentRenderedBlockEntity());
                        assertTrue(state.getCurrentEntityColor().equals(0, 1, 0, 0.25f));
                    });
                }
                GLSMDraws.glEndList();

                final int parent = newList();
                GLSMDraws.glNewList(parent, mode);
                state.pushCurrentBlockEntity();
                state.setCurrentBlockEntity(99);
                GLSMDraws.glCallList(child);
                DisplayListManager.recordStateCommand(() -> assertEquals(99, state.getCurrentRenderedBlockEntity()));
                state.popCurrentBlockEntity();
                GLSMDraws.glEndList();

                for (int caller : new int[] {33, 44}) {
                    state.setCurrentEntityAndItem(caller, caller + 1);
                    state.setCurrentBlockEntity(caller + 2);
                    state.setCurrentEntityColor(0, 0, 1, 0.75f);
                    GLSMDraws.glCallList(parent);
                    assertEquals(caller, state.getCurrentRenderedEntity());
                    assertEquals(caller + 1, state.getCurrentRenderedItem());
                    assertEquals(caller + 2, state.getCurrentRenderedBlockEntity());
                    assertTrue(state.getCurrentEntityColor().equals(0, 0, 1, 0.75f));
                }
            }
        }
    }

    @Test
    void explicitStateRecordsEvenWhenUnchangedAndOnlyDeduplicatesAdjacentCommands() {
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        try (CapturedStateScope ignored = new CapturedStateScope()) {
            state.setCurrentEntityAndItem(7, 8);
            state.setCurrentBlockEntity(9);
            state.setCurrentEntityColor(1, 0, 0, 0.5f);
            final int list = newList();
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            for (int i = 0; i < 100; i++) state.setCurrentEntityAndItem(7, 8);
            for (int i = 0; i < 100; i++) state.setCurrentBlockEntity(9);
            for (int i = 0; i < 100; i++) state.setCurrentEntityColor(1, 0, 0, 0.5f);
            GLSMDraws.glEndList();
            assertEquals(3, DisplayListManager.getDisplayList(list).getCommandCounts().get(GLCommand.COMPLEX_REF));
            state.setCurrentEntityAndItem(70, 80);
            state.setCurrentBlockEntity(90);
            state.setCurrentEntityColor(0, 1, 0, 1);
            GLSMDraws.glCallList(list);
            assertEquals(7, state.getCurrentRenderedEntity());
            assertEquals(8, state.getCurrentRenderedItem());
            assertEquals(9, state.getCurrentRenderedBlockEntity());
            assertTrue(state.getCurrentEntityColor().equals(1, 0, 0, 0.5f));
        }
    }

    @Test
    void namedEffectsResolveTheCurrentPackOnReplay() {
        final var settings = BlockRenderingSettings.INSTANCE;
        final var originalIds = settings.getEntityIds();
        final var originalItemIds = settings.getItemIds();
        final var state = CapturedRenderingState.INSTANCE;
        final NamespacedId name = new NamespacedId("minecraft", "name_tag");
        state.pushCurrentEntityAndItem();
        try {
            settings.setEntityIds(null);
            settings.setItemIds(null);
            final int list = newList();
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            state.setCurrentNamedEntity(name);
            state.setCurrentNamedItem(name);
            GLSMDraws.glEndList();
            for (int id : new int[] {45001, 45002, -1}) {
                settings.setEntityIds(key -> name.equals(key) ? id : -1);
                settings.setItemIds(key -> name.equals(key) ? id + 4 : 0);
                GLSMDraws.glCallList(list);
                assertEquals(id, state.getCurrentRenderedEntity());
                assertEquals(id + 4, state.getCurrentRenderedItem());
            }
        } finally {
            settings.setEntityIds(originalIds);
            settings.setItemIds(originalItemIds);
            state.popCurrentEntityAndItem();
        }
    }

    @Test
    void blockIdentityResolvesAfterPackReplacementAndBlockUniformDefersDuringCompile() {
        final var settings = BlockRenderingSettings.INSTANCE;
        final var previousMatches = settings.getBlockMetaMatches();
        final var block = mock(net.minecraft.block.Block.class);
        final var state = CapturedRenderingState.INSTANCE;
        state.pushCurrentBlockEntity();
        final int program = program();
        GLSMPrograms.glUseProgram(program);
        state.setCurrentBlockEntity(111);
        final var builder = ProgramUniforms.builder("block identity", program);
        builder.uniform1i("itemId", state::getCurrentRenderedBlockEntity, state.getBlockEntityIdNotifier());
        final var uniforms = builder.buildUniforms();
        Reflect.setStatic(ProgramUniforms.class, "active", uniforms);
        Reflect.<List<Uniform>>get(uniforms, "dynamic").get(0).update();
        try {
            settings.setBlockMetaMatches(null);
            final int list = newList();
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            state.setCurrentBlockEntity(block, 3);
            uniforms.update();
            assertEquals(111, itemUniform(program), "compile-only notifications must not change the live uniform");
            GLSMDraws.glEndList();
            for (int id : new int[] {45020, 45021}) {
                final var meta = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
                meta.put(3, id);
                final var matches = new it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap<net.minecraft.block.Block, it.unimi.dsi.fastutil.ints.Int2IntMap>();
                matches.put(block, meta);
                settings.setBlockMetaMatches(matches);
                state.setCurrentBlockEntity(222);
                GLSMDraws.glCallList(list);
                assertEquals(id, state.getCurrentRenderedBlockEntity());
                assertEquals(id, itemUniform(program));
            }
        } finally {
            ProgramUniforms.clearActiveUniforms();
            settings.setBlockMetaMatches(previousMatches);
            state.popCurrentBlockEntity();
        }
    }

    @Test
    void constantVertexAttributesReplayWithoutChangingLiveStateDuringCompile() {
        final int index = 12;
        final var values = BufferUtils.createFloatBuffer(4);
        try {
            for (int mode : new int[] {GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE}) {
                GLSMCurrentAttribs.glVertexAttrib4f(index, 1, 2, 3, 4);
                final int list = newList();
                GLSMDraws.glNewList(list, mode);
                GLSMCurrentAttribs.glVertexAttrib2s(index, (short) -1, (short) 7);
                GL20.glGetVertexAttrib(index, GL20.GL_CURRENT_VERTEX_ATTRIB, values);
                assertEquals(mode == GL11.GL_COMPILE ? 1f : -1f, values.get(0));
                GLSMDraws.glEndList();
                GLSMCurrentAttribs.glVertexAttrib4f(index, 9, 9, 9, 9);
                GLSMDraws.glCallList(list);
                GL20.glGetVertexAttrib(index, GL20.GL_CURRENT_VERTEX_ATTRIB, values);
                assertEquals(-1f, values.get(0));
                assertEquals(7f, values.get(1));
                assertEquals(0f, values.get(2));
                assertEquals(1f, values.get(3));
                assertEquals(1, DisplayListManager.getDisplayList(list).getCommandCounts().get(GLCommand.VERTEX_ATTRIB));
            }
        } finally {
            GLSMCurrentAttribs.glVertexAttrib4f(index, 0, 0, 0, 1);
        }
    }

    @Test
    void passBindsUsePlaybackSelectionInBothCompileModes() {
        int caller = program();
        int compileProgram = program();
        int playbackProgram = program();
        for (int mode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
            GLSMPrograms.glUseProgram(caller);
            selectedProgram = compileProgram;
            int list = newList();
            GLSMDraws.glNewList(list, mode);
            DisplayListManager.recordStateCommand(this::selectProgram);
            selectProgram();
            assertEquals(mode == GL11.GL_COMPILE ? caller : compileProgram, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            GLSMDraws.glEndList();
            assertEquals(0, DisplayListManager.getDisplayList(list).getCommandCounts().get(GLCommand.USE_PROGRAM));
            selectedProgram = playbackProgram;
            GLSMDraws.glCallList(list);
            assertEquals(playbackProgram, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            assertEquals(playbackProgram, GLSMPrograms.getActiveProgram());
        }
    }

    @Test
    void explicitForeignProgramBindsStillRecord() {
        int explicit = program();
        int caller = program();
        GLSMPrograms.glUseProgram(caller);
        int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMPrograms.glUseProgram(explicit);
        GLSMDraws.glEndList();
        assertEquals(caller, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        GLSMDraws.glCallList(list);
        assertEquals(explicit, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }

    @Test
    void transitionFailureResumesRecording() {
        int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        assertThrows(IllegalStateException.class, () -> IrisDisplayListState.runProgramTransition(() -> {
            throw new IllegalStateException("test");
        }));
        assertTrue(DisplayListManager.isRecording());
        GLSMDraws.glEndList();
    }

    @Test
    void repeatedItemIdsDeduplicateWithoutInheritingTheCompilerOrCrossingNestedCalls() {
        final int original = ItemIdManager.getItemId();
        try {
            final int child = newList();
            GLSMDraws.glNewList(child, GL11.GL_COMPILE);
            ItemIdManager.setItemIdRaw(19);
            GLSMDraws.glEndList();
            ItemIdManager.setItemIdRaw(42);
            final int parent = newList();
            GLSMDraws.glNewList(parent, GL11.GL_COMPILE);
            ItemIdManager.setItemIdRaw(42);
            ItemIdManager.setItemIdRaw(42);
            GLSMDraws.glCallList(child);
            ItemIdManager.setItemIdRaw(42);
            ItemIdManager.setItemIdRaw(42);
            GLSMDraws.glEndList();
            assertEquals(2, DisplayListManager.getDisplayList(parent).getCommandCounts().get(GLCommand.COMPLEX_REF));
            ItemIdManager.setItemIdRaw(77);
            GLSMDraws.glCallList(parent);
            assertEquals(42, ItemIdManager.getItemId());
        } finally {
            ItemIdManager.setItemIdRaw(original);
        }
    }

    @Test
    void nestedItemScopesRestorePlaybackCallerAndIntermediateValues() {
        int original = ItemIdManager.getItemId();
        try {
            ItemIdManager.setItemIdRaw(111);
            int child = newList();
            GLSMDraws.glNewList(child, GL11.GL_COMPILE);
            ItemIdManager.pushItemId();
            ItemIdManager.setItemIdRaw(9);
            ItemIdManager.popItemId();
            GLSMDraws.glEndList();
            int parent = newList();
            GLSMDraws.glNewList(parent, GL11.GL_COMPILE);
            ItemIdManager.pushItemId();
            ItemIdManager.setItemIdRaw(42);
            GLSMDraws.glCallList(child);
            DisplayListManager.recordStateCommand(() -> assertEquals(42, ItemIdManager.getItemId()));
            ItemIdManager.popItemId();
            GLSMDraws.glEndList();
            assertEquals(111, ItemIdManager.getItemId());
            for (int caller : new int[] {222, 333}) {
                ItemIdManager.setItemIdRaw(caller);
                GLSMDraws.glCallList(parent);
                assertEquals(caller, ItemIdManager.getItemId());
            }
        } finally {
            ItemIdManager.setItemIdRaw(original);
        }
    }

    @Test
    void phaseSpecialAndTranslucencyScopesRestorePlaybackCaller() {
        WorldRenderingPhase originalPhase = GbufferPrograms.getOverridePhase();
        Boolean originalTranslucency = GbufferPrograms.getDeclaredTranslucency();
        int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GbufferPrograms.pushOverridePhase(WorldRenderingPhase.ENTITIES);
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
        Boolean previous = GbufferPrograms.beginTranslucencyDeclaration(false);
        GbufferPrograms.endTranslucencyDeclaration(previous);
        GbufferPrograms.teardownSpecialRenderCondition();
        GbufferPrograms.popOverridePhase();
        GLSMDraws.glEndList();
        GbufferPrograms.setOverridePhase(WorldRenderingPhase.HAND_SOLID);
        GbufferPrograms.setTranslucencyDeclaration(true);
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.ENTITY_EYES);
        try {
            GLSMDraws.glCallList(list);
            assertEquals(WorldRenderingPhase.HAND_SOLID, GbufferPrograms.getOverridePhase());
            assertEquals(SpecialCondition.ENTITY_EYES, GbufferPrograms.getSpecialCondition());
            assertEquals(Boolean.TRUE, GbufferPrograms.getDeclaredTranslucency());
        } finally {
            GbufferPrograms.teardownSpecialRenderCondition();
            GbufferPrograms.setOverridePhase(originalPhase);
            GbufferPrograms.setTranslucencyDeclaration(originalTranslucency);
        }
    }

    @Test
    void nestedEntityPhaseUsesThePlaybackPipelineInBothCompileModes() {
        PipelineManager manager = Iris.getPipelineManager();
        WorldRenderingPipeline previousPipeline = manager.getPipelineNullable();
        WorldRenderingPhase previousOverride = GbufferPrograms.getOverridePhase();
        PhasePipeline pipeline = new PhasePipeline(program(), program());
        WorldRenderingPhase[] expected = { WorldRenderingPhase.NONE };
        Reflect.set(manager, "pipeline", pipeline);
        GbufferPrograms.setOverridePhase(null);
        try {
            for (int mode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
                for (WorldRenderingPhase compiling : new WorldRenderingPhase[] { WorldRenderingPhase.NONE, WorldRenderingPhase.BLOCK_ENTITIES }) {
                    pipeline.phase = compiling;
                    int child = newList();
                    GLSMDraws.glNewList(child, mode);
                    boolean childScope = GbufferPrograms.beginNestedEntityPhase();
                    DisplayListManager.recordStateCommand(() -> {
                        assertEquals(expected[0], GbufferPrograms.getCurrentPhase());
                        assertEquals(expected[0] == WorldRenderingPhase.ENTITIES ? pipeline.entityProgram : pipeline.blockProgram,
                            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
                    });
                    GbufferPrograms.endNestedEntityPhase(childScope);
                    GLSMDraws.glEndList();

                    int parent = newList();
                    GLSMDraws.glNewList(parent, mode);
                    boolean parentScope = GbufferPrograms.beginNestedEntityPhase();
                    // COMPILE_AND_EXECUTE also replays the child now.
                    expected[0] = GbufferPrograms.getCurrentPhase();
                    pipeline.bind();
                    GLSMDraws.glCallList(child);
                    GbufferPrograms.endNestedEntityPhase(parentScope);
                    GLSMDraws.glEndList();

                    for (WorldRenderingPhase playback : new WorldRenderingPhase[] { WorldRenderingPhase.BLOCK_ENTITIES, WorldRenderingPhase.HAND_SOLID, WorldRenderingPhase.ENTITIES }) {
                        pipeline.phase = playback;
                        GbufferPrograms.setOverridePhase(null);
                        expected[0] = playback == WorldRenderingPhase.BLOCK_ENTITIES ? WorldRenderingPhase.ENTITIES : playback;
                        GLSMDraws.glCallList(parent);
                        assertEquals(playback, GbufferPrograms.getCurrentPhase());
                        assertNull(GbufferPrograms.getOverridePhase());
                        assertEquals(playback == WorldRenderingPhase.ENTITIES ? pipeline.entityProgram : pipeline.blockProgram,
                            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
                    }
                }
            }
        } finally {
            if (DisplayListManager.isRecording()) GLSMDraws.glEndList();
            GbufferPrograms.setOverridePhase(previousOverride);
            Reflect.set(manager, "pipeline", previousPipeline);
        }
    }

    private static final class PhasePipeline extends FixedFunctionWorldRenderingPipeline {
        private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
        private WorldRenderingPhase override;
        private final int entityProgram;
        private final int blockProgram;

        private PhasePipeline(int entityProgram, int blockProgram) {
            this.entityProgram = entityProgram;
            this.blockProgram = blockProgram;
        }

        @Override
        public WorldRenderingPhase getPhase() {
            return override == null ? phase : override;
        }

        @Override
        public void setOverridePhase(WorldRenderingPhase phase) {
            override = phase;
            bind();
        }

        private void bind() {
            IrisDisplayListState.runProgramTransition(() -> GLSMPrograms.glUseProgram(
                getPhase() == WorldRenderingPhase.ENTITIES ? entityProgram : blockProgram));
        }
    }

    private static int itemUniform(int program) {
        IntBuffer result = BufferUtils.createIntBuffer(1);
        GL20.glGetUniform(program, GLSMPrograms.glGetUniformLocation(program, "itemId"), result);
        return result.get(0);
    }

    @Test
    void cachedSpawnerEntityResolvesCurrentPackAndRestoresNestedCallerIds() {
        final BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        final Object2IntFunction<NamespacedId> previousIds = settings.getEntityIds();
        final NbtConditionalIdMap<NamespacedId> previousNbt = settings.getEntityNbtMap();
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        final int previousEntity = state.getCurrentRenderedEntity();
        final int previousItem = state.getCurrentRenderedItem();
        final int previousBlock = state.getCurrentRenderedBlockEntity();
        final Entity entity = mock(Entity.class);
        final String registered = EntityList.getEntityString(entity);
        final NamespacedId name = new NamespacedId(registered == null ? entity.getClass().getSimpleName() : registered);
        final int program = program();
        GLSMPrograms.glUseProgram(program);
        final ProgramUniforms.Builder builder = ProgramUniforms.builder("cached entity", program);
        // The fixture's active int uniform carries the entity ID here.
        builder.uniform1i("itemId", state::getCurrentRenderedEntity, state.getEntityIdNotifier());
        final ProgramUniforms uniforms = builder.buildUniforms();
        Reflect.setStatic(ProgramUniforms.class, "active", uniforms);
        Reflect.<List<Uniform>>get(uniforms, "dynamic").get(0).update();
        final int[] expected = { -1 };
        try {
            settings.setEntityNbtMap(null);
            state.setCurrentBlockEntity(52);
            for (int mode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
                settings.setEntityIds(null);
                expected[0] = -1;
                state.setCurrentEntityAndItem(111, 112);
                final int child = newList();
                GLSMDraws.glNewList(child, mode);
                state.pushCurrentEntityAndItem();
                state.setCurrentRenderedEntity(entity);
                DisplayListManager.recordStateCommand(() -> {
                    assertEquals(expected[0], state.getCurrentRenderedEntity());
                    assertEquals(expected[0], itemUniform(program));
                    assertEquals(0, state.getCurrentRenderedItem());
                    assertEquals(52, state.getCurrentRenderedBlockEntity(), "the cage keeps its block ID");
                });
                state.popCurrentEntityAndItem();
                GLSMDraws.glEndList();
                assertEquals(111, state.getCurrentRenderedEntity());
                assertEquals(112, state.getCurrentRenderedItem());

                final int parent = newList();
                GLSMDraws.glNewList(parent, mode);
                state.pushCurrentEntityAndItem();
                state.setCurrentRenderedEntity(entity);
                ItemIdManager.pushItemId();
                ItemIdManager.setItemIdRaw(73);
                GLSMDraws.glCallList(child);
                DisplayListManager.recordStateCommand(() -> {
                    assertEquals(expected[0], state.getCurrentRenderedEntity());
                    assertEquals(73, state.getCurrentRenderedItem(), "nested entity restores outer item ID");
                });
                ItemIdManager.popItemId();
                state.popCurrentEntityAndItem();
                GLSMDraws.glEndList();

                for (int id : new int[] { 45010, 45011, -1 }) {
                    settings.setEntityIds(key -> name.equals(key) ? id : -1);
                    expected[0] = id;
                    state.setCurrentEntityAndItem(222, 223);
                    GLSMDraws.glCallList(parent);
                    assertEquals(222, state.getCurrentRenderedEntity());
                    assertEquals(222, itemUniform(program));
                    assertEquals(223, state.getCurrentRenderedItem());
                    assertEquals(52, state.getCurrentRenderedBlockEntity());
                }
            }
        } finally {
            if (DisplayListManager.isRecording()) GLSMDraws.glEndList();
            ProgramUniforms.clearActiveUniforms();
            settings.setEntityIds(previousIds);
            settings.setEntityNbtMap(previousNbt);
            state.setCurrentEntityAndItem(previousEntity, previousItem);
            state.setCurrentBlockEntity(previousBlock);
        }
    }

    @Test
    void cachedItemResolvesItsIdAfterFirstPackLoadAndPackReplacement() {
        BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        Object2IntFunction<NamespacedId> previousIds = settings.getItemIds();
        NbtConditionalIdMap<NamespacedId> previousNbt = settings.getItemNbtMap();
        int previousItem = ItemIdManager.getItemId();
        Item item = new Item();
        NamespacedId name = new NamespacedId("angelica", "cached_item_test");
        int program = program();
        GLSMPrograms.glUseProgram(program);
        ProgramUniforms.Builder builder = ProgramUniforms.builder("cached item", program);
        builder.uniform1i("itemId", ItemIdManager::getItemId, CapturedRenderingState.INSTANCE.getItemIdNotifier());
        ProgramUniforms uniforms = builder.buildUniforms();
        Reflect.setStatic(ProgramUniforms.class, "active", uniforms);
        Reflect.<List<Uniform>>get(uniforms, "dynamic").get(0).update();
        int[] expected = { 0 };
        try {
            for (int mode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
                settings.setItemIds(null);
                settings.setItemNbtMap(null);
                ItemMaterialHelper.clearCache();
                ItemIdManager.setItemIdRaw(111);
                ItemStack stack = new ItemStack(item);
                int list = newList();
                GLSMDraws.glNewList(list, mode);
                ItemIdManager.pushItemId();
                ItemIdManager.setItemId(stack);
                DisplayListManager.recordStateCommand(() -> {
                    assertEquals(expected[0], ItemIdManager.getItemId());
                    assertEquals(expected[0], itemUniform(program));
                });
                ItemIdManager.popItemId();
                GLSMDraws.glEndList();
                assertEquals(111, ItemIdManager.getItemId());
                // A renderer may reuse its ItemStack after compiling a list.
                stack.func_150996_a(new Item());
                for (int id : new int[] { 45020, 45028 }) {
                    settings.setItemIds(key -> name.equals(key) ? id : 0);
                    ItemMaterialHelper.clearCache();
                    // Supply a registry name without bootstrapping the entire Forge item registry.
                    Reflect.<Reference2ObjectMap<Item, NamespacedId>>getStatic(ItemMaterialHelper.class, "ITEM_NAME_CACHE").put(item, name);
                    expected[0] = id;
                    ItemIdManager.setItemIdRaw(222);
                    GLSMDraws.glCallList(list);
                    assertEquals(222, ItemIdManager.getItemId());
                    assertEquals(222, itemUniform(program));
                }
            }
        } finally {
            if (DisplayListManager.isRecording()) GLSMDraws.glEndList();
            ProgramUniforms.clearActiveUniforms();
            settings.setItemIds(previousIds);
            settings.setItemNbtMap(previousNbt);
            ItemMaterialHelper.clearCache();
            ItemIdManager.setItemIdRaw(previousItem);
        }
    }

    @Test
    void compileOnlyItemNotificationsDoNotUploadToLiveProgram() {
        int program = program();
        int original = ItemIdManager.getItemId();
        GLSMPrograms.glUseProgram(program);
        ItemIdManager.setItemIdRaw(111);
        ProgramUniforms.Builder builder = ProgramUniforms.builder("review", program);
        builder.uniform1i("itemId", ItemIdManager::getItemId, CapturedRenderingState.INSTANCE.getItemIdNotifier());
        ProgramUniforms uniforms = builder.buildUniforms();
        List<Uniform> dynamic = Reflect.get(uniforms, "dynamic");
        Reflect.setStatic(ProgramUniforms.class, "active", uniforms);
        dynamic.get(0).update();
        try {
            int list = newList();
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            ItemIdManager.pushItemId();
            ItemIdManager.setItemIdRaw(42);
            uniforms.update();
            assertEquals(111, itemUniform(program));
            ItemIdManager.popItemId();
            GLSMDraws.glEndList();
            ItemIdManager.setItemIdRaw(222);
            assertEquals(222, itemUniform(program));
            GLSMDraws.glCallList(list);
            assertEquals(222, itemUniform(program));
        } finally {
            ProgramUniforms.clearActiveUniforms();
            ItemIdManager.setItemIdRaw(original);
        }
    }
}

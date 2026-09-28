package com.gtnewhorizons.angelica.iris;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
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
        if (DisplayListManager.isRecording()) GLStateManager.glEndList();
        GLStateManager.glUseProgram(0);
        for (int list : lists) GLStateManager.glDeleteLists(list, 1);
        for (int program : programs) GLStateManager.glDeleteProgram(program);
    }

    private int newList() {
        int list = GLStateManager.glGenLists(1);
        lists.add(list);
        return list;
    }

    private int program() {
        int vertex = shader(GL20.GL_VERTEX_SHADER,
            "#version 330 core\nvoid main() { gl_Position = vec4(0, 0, 0, 1); }");
        int fragment = shader(GL20.GL_FRAGMENT_SHADER,
            "#version 330 core\nuniform int itemId; out vec4 color; void main() { color = vec4(float(itemId)); }");
        int program = GLStateManager.glCreateProgram();
        programs.add(program);
        GLStateManager.glAttachShader(program, vertex);
        GLStateManager.glAttachShader(program, fragment);
        GLStateManager.glLinkProgram(program);
        GLStateManager.glDeleteShader(vertex);
        GLStateManager.glDeleteShader(fragment);
        assertEquals(GL11.GL_TRUE, GLStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS));
        return program;
    }

    private static int shader(int type, String source) {
        int shader = GLStateManager.glCreateShader(type);
        GLStateManager.glShaderSource(shader, source);
        GLStateManager.glCompileShader(shader);
        assertEquals(GL11.GL_TRUE, GLStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS));
        return shader;
    }

    private void selectProgram() {
        IrisDisplayListState.runProgramTransition(() -> GLStateManager.glUseProgram(selectedProgram));
    }

    @Test
    void cutoutScopesRestorePlaybackAlphaAndLightmapWithoutChangingItsActiveUnit() {
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            for (int mode : new int[] {GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE}) {
                GLStateManager.disableAlphaTest();
                GLStateManager.glAlphaFunc(GL11.GL_LESS, 0.2f);
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
                GLStateManager.disableTexture();
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE3);
                final int list = newList();
                GLStateManager.glNewList(list, mode);
                final int outer = GLStateManager.pushState(StateSet.CUTOUT);
                GbufferPrograms.setCutoutDefaults();
                final int inner = GLStateManager.pushState(StateSet.CUTOUT);
                GbufferPrograms.setCutoutDefaults();
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
                GLStateManager.disableTexture();
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
                GLStateManager.popStateTo(inner);
                DisplayListManager.recordStateCommand(() -> assertTrue(GLStateManager.getTextures().getTextureUnitStates(1).isEnabled()));
                GLStateManager.popStateTo(outer);
                GLStateManager.glEndList();

                for (boolean lightmap : new boolean[] {true, false}) {
                    GLStateManager.enableAlphaTest();
                    GLStateManager.glAlphaFunc(GL11.GL_GEQUAL, 0.7f);
                    GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
                    if (lightmap) GLStateManager.enableTexture(); else GLStateManager.disableTexture();
                    GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
                    GLStateManager.glCallList(list);
                    assertTrue(GLStateManager.isEffectiveAlphaTestEnabled());
                    assertEquals(GL11.GL_GEQUAL, GLStateManager.getAlphaState().getFunction());
                    assertEquals(0.7f, GLStateManager.getAlphaState().getReference());
                    assertEquals(lightmap, GLStateManager.getTextures().getTextureUnitStates(1).isEnabled());
                    assertEquals(2, GLStateManager.getActiveTextureUnit(), "scope restores must preserve the list's explicit unit selection");
                }
            }
        } finally {
            GLStateManager.glPopAttrib();
        }
    }

    @Test
    void nestedBlendScopesRestorePlaybackValuesEvenIfCompileTimePopWasANoop() {
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            for (int mode : new int[] {GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE}) {
                GLStateManager.disableBlend();
                GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
                final int child = newList();
                GLStateManager.glNewList(child, mode);
                final int scope = GLStateManager.pushState(StateSet.BLEND);
                GLStateManager.disableBlend();
                GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
                GLStateManager.popStateTo(scope);
                GLStateManager.glEndList();
                final int parent = newList();
                GLStateManager.glNewList(parent, mode);
                final int parentScope = GLStateManager.pushState(StateSet.BLEND);
                GLStateManager.glCallList(child);
                GLStateManager.popStateTo(parentScope);
                GLStateManager.glEndList();

                GLStateManager.enableBlend();
                GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                final int caller = GLStateManager.pushState(StateSet.BLEND);
                try {
                    GLStateManager.glCallList(parent);
                    assertTrue(GLStateManager.isEffectiveBlendEnabled());
                    final var blend = GLStateManager.getEffectiveBlendState(new com.gtnewhorizons.angelica.glsm.states.BlendState());
                    assertEquals(GL11.GL_SRC_ALPHA, blend.getSrcRgb());
                    assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, blend.getDstRgb());
                    assertEquals(GL11.GL_ZERO, blend.getSrcAlpha());
                    assertEquals(GL11.GL_ONE, blend.getDstAlpha());
                    assertEquals(caller + 1, GLStateManager.getAttribDepth());
                } finally {
                    GLStateManager.popStateTo(caller);
                }
            }
        } finally {
            GLStateManager.glPopAttrib();
        }
    }

    @Test
    void idsAndColorReplayInsideNestedScopesAndRestoreThePlaybackCaller() {
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.pushCurrentEntityAndItem();
        state.pushCurrentBlockEntity();
        state.pushCurrentEntityColor();
        try {
            for (int mode : new int[] {GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE}) {
                state.setCurrentEntityAndItem(10, 11);
                state.setCurrentBlockEntity(12);
                state.setCurrentEntityColor(1, 0, 0, 0.5f);
                final int child = newList();
                GLStateManager.glNewList(child, mode);
                state.pushCurrentEntityAndItem();
                state.pushCurrentBlockEntity();
                state.pushCurrentEntityColor();
                state.setCurrentEntityAndItem(45010, 45011);
                state.setCurrentBlockEntity(45012);
                state.setCurrentEntityColor(0, 1, 0, 0.25f);
                DisplayListManager.recordStateCommand(() -> {
                    assertEquals(45010, state.getCurrentRenderedEntity());
                    assertEquals(45011, state.getCurrentRenderedItem());
                    assertEquals(45012, state.getCurrentRenderedBlockEntity());
                    assertTrue(state.getCurrentEntityColor().equals(0, 1, 0, 0.25f));
                });
                state.popCurrentEntityColor();
                state.popCurrentBlockEntity();
                state.popCurrentEntityAndItem();
                GLStateManager.glEndList();

                final int parent = newList();
                GLStateManager.glNewList(parent, mode);
                state.pushCurrentBlockEntity();
                state.setCurrentBlockEntity(99);
                GLStateManager.glCallList(child);
                DisplayListManager.recordStateCommand(() -> assertEquals(99, state.getCurrentRenderedBlockEntity()));
                state.popCurrentBlockEntity();
                GLStateManager.glEndList();

                for (int caller : new int[] {33, 44}) {
                    state.setCurrentEntityAndItem(caller, caller + 1);
                    state.setCurrentBlockEntity(caller + 2);
                    state.setCurrentEntityColor(0, 0, 1, 0.75f);
                    GLStateManager.glCallList(parent);
                    assertEquals(caller, state.getCurrentRenderedEntity());
                    assertEquals(caller + 1, state.getCurrentRenderedItem());
                    assertEquals(caller + 2, state.getCurrentRenderedBlockEntity());
                    assertTrue(state.getCurrentEntityColor().equals(0, 0, 1, 0.75f));
                }
            }
        } finally {
            state.popCurrentEntityColor();
            state.popCurrentBlockEntity();
            state.popCurrentEntityAndItem();
        }
    }

    @Test
    void explicitStateRecordsEvenWhenUnchangedAndOnlyDeduplicatesAdjacentCommands() {
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.pushCurrentEntityAndItem();
        state.pushCurrentBlockEntity();
        state.pushCurrentEntityColor();
        try {
            state.setCurrentEntityAndItem(7, 8);
            state.setCurrentBlockEntity(9);
            state.setCurrentEntityColor(1, 0, 0, 0.5f);
            final int list = newList();
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            for (int i = 0; i < 100; i++) state.setCurrentEntityAndItem(7, 8);
            for (int i = 0; i < 100; i++) state.setCurrentBlockEntity(9);
            for (int i = 0; i < 100; i++) state.setCurrentEntityColor(1, 0, 0, 0.5f);
            GLStateManager.glEndList();
            assertEquals(3, DisplayListManager.getDisplayList(list).getCommandCounts().get(GLCommand.COMPLEX_REF));
            state.setCurrentEntityAndItem(70, 80);
            state.setCurrentBlockEntity(90);
            state.setCurrentEntityColor(0, 1, 0, 1);
            GLStateManager.glCallList(list);
            assertEquals(7, state.getCurrentRenderedEntity());
            assertEquals(8, state.getCurrentRenderedItem());
            assertEquals(9, state.getCurrentRenderedBlockEntity());
            assertTrue(state.getCurrentEntityColor().equals(1, 0, 0, 0.5f));
        } finally {
            state.popCurrentEntityColor();
            state.popCurrentBlockEntity();
            state.popCurrentEntityAndItem();
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
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            state.setCurrentNamedEntity(name);
            state.setCurrentNamedItem(name);
            GLStateManager.glEndList();
            for (int id : new int[] {45001, 45002, -1}) {
                settings.setEntityIds(key -> name.equals(key) ? id : -1);
                settings.setItemIds(key -> name.equals(key) ? id + 4 : 0);
                GLStateManager.glCallList(list);
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
        GLStateManager.glUseProgram(program);
        state.setCurrentBlockEntity(111);
        final var builder = ProgramUniforms.builder("block identity", program);
        builder.uniform1i("itemId", state::getCurrentRenderedBlockEntity, state.getBlockEntityIdNotifier());
        final var uniforms = builder.buildUniforms();
        Reflect.setStatic(ProgramUniforms.class, "active", uniforms);
        Reflect.<List<Uniform>>get(uniforms, "dynamic").get(0).update();
        try {
            settings.setBlockMetaMatches(null);
            final int list = newList();
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            state.setCurrentBlockEntity(block, 3);
            uniforms.update();
            assertEquals(111, itemUniform(program), "compile-only notifications must not change the live uniform");
            GLStateManager.glEndList();
            for (int id : new int[] {45020, 45021}) {
                final var meta = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
                meta.put(3, id);
                final var matches = new it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap<net.minecraft.block.Block, it.unimi.dsi.fastutil.ints.Int2IntMap>();
                matches.put(block, meta);
                settings.setBlockMetaMatches(matches);
                state.setCurrentBlockEntity(222);
                GLStateManager.glCallList(list);
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
                GLStateManager.glVertexAttrib4f(index, 1, 2, 3, 4);
                final int list = newList();
                GLStateManager.glNewList(list, mode);
                GLStateManager.glVertexAttrib2s(index, (short) -1, (short) 7);
                GL20.glGetVertexAttrib(index, GL20.GL_CURRENT_VERTEX_ATTRIB, values);
                assertEquals(mode == GL11.GL_COMPILE ? 1f : -1f, values.get(0));
                GLStateManager.glEndList();
                GLStateManager.glVertexAttrib4f(index, 9, 9, 9, 9);
                GLStateManager.glCallList(list);
                GL20.glGetVertexAttrib(index, GL20.GL_CURRENT_VERTEX_ATTRIB, values);
                assertEquals(-1f, values.get(0));
                assertEquals(7f, values.get(1));
                assertEquals(0f, values.get(2));
                assertEquals(1f, values.get(3));
                assertEquals(1, DisplayListManager.getDisplayList(list).getCommandCounts().get(GLCommand.VERTEX_ATTRIB));
            }
        } finally {
            GLStateManager.glVertexAttrib4f(index, 0, 0, 0, 1);
        }
    }

    @Test
    void passBindsUsePlaybackSelectionInBothCompileModes() {
        int caller = program();
        int compileProgram = program();
        int playbackProgram = program();
        for (int mode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
            GLStateManager.glUseProgram(caller);
            selectedProgram = compileProgram;
            int list = newList();
            GLStateManager.glNewList(list, mode);
            DisplayListManager.recordStateCommand(this::selectProgram);
            selectProgram();
            assertEquals(mode == GL11.GL_COMPILE ? caller : compileProgram, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            GLStateManager.glEndList();
            assertEquals(0, DisplayListManager.getDisplayList(list).getCommandCounts().get(GLCommand.USE_PROGRAM));
            selectedProgram = playbackProgram;
            GLStateManager.glCallList(list);
            assertEquals(playbackProgram, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            assertEquals(playbackProgram, GLStateManager.getActiveProgram());
        }
    }

    @Test
    void explicitForeignProgramBindsStillRecord() {
        int explicit = program();
        int caller = program();
        GLStateManager.glUseProgram(caller);
        int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glUseProgram(explicit);
        GLStateManager.glEndList();
        assertEquals(caller, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        GLStateManager.glCallList(list);
        assertEquals(explicit, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }

    @Test
    void transitionFailureResumesRecording() {
        int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        assertThrows(IllegalStateException.class, () -> IrisDisplayListState.runProgramTransition(() -> {
            throw new IllegalStateException("test");
        }));
        assertTrue(DisplayListManager.isRecording());
        GLStateManager.glEndList();
    }

    @Test
    void repeatedItemIdsDeduplicateWithoutInheritingTheCompilerOrCrossingNestedCalls() {
        final int original = ItemIdManager.getItemId();
        try {
            final int child = newList();
            GLStateManager.glNewList(child, GL11.GL_COMPILE);
            ItemIdManager.setItemIdRaw(19);
            GLStateManager.glEndList();
            ItemIdManager.setItemIdRaw(42);
            final int parent = newList();
            GLStateManager.glNewList(parent, GL11.GL_COMPILE);
            ItemIdManager.setItemIdRaw(42);
            ItemIdManager.setItemIdRaw(42);
            GLStateManager.glCallList(child);
            ItemIdManager.setItemIdRaw(42);
            ItemIdManager.setItemIdRaw(42);
            GLStateManager.glEndList();
            assertEquals(2, DisplayListManager.getDisplayList(parent).getCommandCounts().get(GLCommand.COMPLEX_REF));
            ItemIdManager.setItemIdRaw(77);
            GLStateManager.glCallList(parent);
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
            GLStateManager.glNewList(child, GL11.GL_COMPILE);
            ItemIdManager.pushItemId();
            ItemIdManager.setItemIdRaw(9);
            ItemIdManager.popItemId();
            GLStateManager.glEndList();
            int parent = newList();
            GLStateManager.glNewList(parent, GL11.GL_COMPILE);
            ItemIdManager.pushItemId();
            ItemIdManager.setItemIdRaw(42);
            GLStateManager.glCallList(child);
            DisplayListManager.recordStateCommand(() -> assertEquals(42, ItemIdManager.getItemId()));
            ItemIdManager.popItemId();
            GLStateManager.glEndList();
            assertEquals(111, ItemIdManager.getItemId());
            for (int caller : new int[] {222, 333}) {
                ItemIdManager.setItemIdRaw(caller);
                GLStateManager.glCallList(parent);
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
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GbufferPrograms.pushOverridePhase(WorldRenderingPhase.ENTITIES);
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
        Boolean previous = GbufferPrograms.beginTranslucencyDeclaration(false);
        GbufferPrograms.endTranslucencyDeclaration(previous);
        GbufferPrograms.teardownSpecialRenderCondition();
        GbufferPrograms.popOverridePhase();
        GLStateManager.glEndList();
        GbufferPrograms.setOverridePhase(WorldRenderingPhase.HAND_SOLID);
        GbufferPrograms.setTranslucencyDeclaration(true);
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.ENTITY_EYES);
        try {
            GLStateManager.glCallList(list);
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
                    GLStateManager.glNewList(child, mode);
                    boolean childScope = GbufferPrograms.beginNestedEntityPhase();
                    DisplayListManager.recordStateCommand(() -> {
                        assertEquals(expected[0], GbufferPrograms.getCurrentPhase());
                        assertEquals(expected[0] == WorldRenderingPhase.ENTITIES ? pipeline.entityProgram : pipeline.blockProgram,
                            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
                    });
                    GbufferPrograms.endNestedEntityPhase(childScope);
                    GLStateManager.glEndList();

                    int parent = newList();
                    GLStateManager.glNewList(parent, mode);
                    boolean parentScope = GbufferPrograms.beginNestedEntityPhase();
                    // COMPILE_AND_EXECUTE also replays the child now.
                    expected[0] = GbufferPrograms.getCurrentPhase();
                    pipeline.bind();
                    GLStateManager.glCallList(child);
                    GbufferPrograms.endNestedEntityPhase(parentScope);
                    GLStateManager.glEndList();

                    for (WorldRenderingPhase playback : new WorldRenderingPhase[] { WorldRenderingPhase.BLOCK_ENTITIES, WorldRenderingPhase.HAND_SOLID, WorldRenderingPhase.ENTITIES }) {
                        pipeline.phase = playback;
                        GbufferPrograms.setOverridePhase(null);
                        expected[0] = playback == WorldRenderingPhase.BLOCK_ENTITIES ? WorldRenderingPhase.ENTITIES : playback;
                        GLStateManager.glCallList(parent);
                        assertEquals(playback, GbufferPrograms.getCurrentPhase());
                        assertNull(GbufferPrograms.getOverridePhase());
                        assertEquals(playback == WorldRenderingPhase.ENTITIES ? pipeline.entityProgram : pipeline.blockProgram,
                            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
                    }
                }
            }
        } finally {
            if (DisplayListManager.isRecording()) GLStateManager.glEndList();
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
            IrisDisplayListState.runProgramTransition(() -> GLStateManager.glUseProgram(
                getPhase() == WorldRenderingPhase.ENTITIES ? entityProgram : blockProgram));
        }
    }

    private static int itemUniform(int program) {
        IntBuffer result = BufferUtils.createIntBuffer(1);
        GL20.glGetUniform(program, GLStateManager.glGetUniformLocation(program, "itemId"), result);
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
        GLStateManager.glUseProgram(program);
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
                GLStateManager.glNewList(child, mode);
                state.pushCurrentEntityAndItem();
                state.setCurrentRenderedEntity(entity);
                DisplayListManager.recordStateCommand(() -> {
                    assertEquals(expected[0], state.getCurrentRenderedEntity());
                    assertEquals(expected[0], itemUniform(program));
                    assertEquals(0, state.getCurrentRenderedItem());
                    assertEquals(52, state.getCurrentRenderedBlockEntity(), "the cage keeps its block ID");
                });
                state.popCurrentEntityAndItem();
                GLStateManager.glEndList();
                assertEquals(111, state.getCurrentRenderedEntity());
                assertEquals(112, state.getCurrentRenderedItem());

                final int parent = newList();
                GLStateManager.glNewList(parent, mode);
                state.pushCurrentEntityAndItem();
                state.setCurrentRenderedEntity(entity);
                ItemIdManager.pushItemId();
                ItemIdManager.setItemIdRaw(73);
                GLStateManager.glCallList(child);
                DisplayListManager.recordStateCommand(() -> {
                    assertEquals(expected[0], state.getCurrentRenderedEntity());
                    assertEquals(73, state.getCurrentRenderedItem(), "nested entity restores outer item ID");
                });
                ItemIdManager.popItemId();
                state.popCurrentEntityAndItem();
                GLStateManager.glEndList();

                for (int id : new int[] { 45010, 45011, -1 }) {
                    settings.setEntityIds(key -> name.equals(key) ? id : -1);
                    expected[0] = id;
                    state.setCurrentEntityAndItem(222, 223);
                    GLStateManager.glCallList(parent);
                    assertEquals(222, state.getCurrentRenderedEntity());
                    assertEquals(222, itemUniform(program));
                    assertEquals(223, state.getCurrentRenderedItem());
                    assertEquals(52, state.getCurrentRenderedBlockEntity());
                }
            }
        } finally {
            if (DisplayListManager.isRecording()) GLStateManager.glEndList();
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
        GLStateManager.glUseProgram(program);
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
                GLStateManager.glNewList(list, mode);
                ItemIdManager.pushItemId();
                ItemIdManager.setItemId(stack);
                DisplayListManager.recordStateCommand(() -> {
                    assertEquals(expected[0], ItemIdManager.getItemId());
                    assertEquals(expected[0], itemUniform(program));
                });
                ItemIdManager.popItemId();
                GLStateManager.glEndList();
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
                    GLStateManager.glCallList(list);
                    assertEquals(222, ItemIdManager.getItemId());
                    assertEquals(222, itemUniform(program));
                }
            }
        } finally {
            if (DisplayListManager.isRecording()) GLStateManager.glEndList();
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
        GLStateManager.glUseProgram(program);
        ItemIdManager.setItemIdRaw(111);
        ProgramUniforms.Builder builder = ProgramUniforms.builder("review", program);
        builder.uniform1i("itemId", ItemIdManager::getItemId, CapturedRenderingState.INSTANCE.getItemIdNotifier());
        ProgramUniforms uniforms = builder.buildUniforms();
        List<Uniform> dynamic = Reflect.get(uniforms, "dynamic");
        Reflect.setStatic(ProgramUniforms.class, "active", uniforms);
        dynamic.get(0).update();
        try {
            int list = newList();
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            ItemIdManager.pushItemId();
            ItemIdManager.setItemIdRaw(42);
            uniforms.update();
            assertEquals(111, itemUniform(program));
            ItemIdManager.popItemId();
            GLStateManager.glEndList();
            ItemIdManager.setItemIdRaw(222);
            assertEquals(222, itemUniform(program));
            GLStateManager.glCallList(list);
            assertEquals(222, itemUniform(program));
        } finally {
            ProgramUniforms.clearActiveUniforms();
            ItemIdManager.setItemIdRaw(original);
        }
    }
}

package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.rendering.DeferredEntityOverlay;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.SkippedGlintBlock;
import com.gtnewhorizons.angelica.rendering.items.ItemGlint;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.BufferSegment;
import net.coderbot.batchedentityrendering.impl.SegmentedBufferBuilder;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gbuffer_overrides.matching.RenderCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.layer.PassOverride;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.CapturedStateScope;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderCreeper;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderWither;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.cubes;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.onlyGroup;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.template;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@GLCoreTest
class EntityShadowReplayGLTest {
    private static final float TICK_DELTA = 0.25f;
    private final Entity entity = mock(Entity.class);
    private final Render renderer = mock(Render.class);
    private final RenderManager manager = mock(RenderManager.class);
    private final ModelPartBatcher batcher = ModelPartBatcher.INSTANCE;
    private final Matrix4f shadowView = new Matrix4f().rotateXYZ(0.6f, -0.7f, 0.2f).translate(4, -2, 5);
    private final Matrix4f mainView = new Matrix4f().rotateXYZ(-0.1f, 0.8f, 0);
    private final Matrix4f model = new Matrix4f().translation(3, 2, -6).rotateY(0.7f).scale(0.5f);
    private final Matrix4f captureEntry = new Matrix4f();
    private boolean captureOpen, capturePoseOpen;
    private RetainedTesrGroups target;
    private long previousGlintTime;
    private boolean previousBatching, previousReplay, previousAtlas, previousShadow;
    private String[] previousExcluded;

    private static void applyGlintExit(String kind) {
        switch (kind) {
            case "armor" -> SkippedGlintBlock.applyArmorExitState();
            case "held" -> SkippedGlintBlock.applyHeldItemExitState();
            case "dropped" -> SkippedGlintBlock.applyDroppedItemExitState();
            default -> throw new AssertionError(kind);
        }
    }

    private static ObjectArrayList<EntityRenderCommands.Model> glintBases() {
        final ObjectArrayList<EntityRenderCommands.Model> bases = new ObjectArrayList<>();
        for (EntityRenderCommands.Command command : commands()) {
            if (command instanceof EntityRenderCommands.Model model && model.glint != null) bases.add(model);
        }
        assertFalse(bases.isEmpty());
        return bases;
    }

    private static void assertGlintLayers(RetainedTesrGroups.Group glint, Matrix4f first, Matrix4f second,
                                          TemplateBuffer[] templates, Matrix4f[] matrices, long info, int overlay) {
        assertGlintLayers(glint, first, second, templates, matrices, info, overlay, true);
    }

    private static void assertGlintLayers(RetainedTesrGroups.Group glint, Matrix4f first, Matrix4f second,
                                          TemplateBuffer[] templates, Matrix4f[] matrices, long info, int overlay, boolean ordered) {
        final RetainedTesrGroups.InstanceColumns columns = glint.templateColumns.hasInstances() ? glint.templateColumns : glint.cubeColumns;
        final int color = AngelicaBufferSource.packAbgr(0.5f * 0.76f, 0.25f * 0.76f, 0.8f * 0.76f, 1);
        for (Matrix4f textureMatrix : new Matrix4f[]{first, second}) {
            int matched = 0;
            final boolean[] seen = new boolean[templates.length];
            for (RetainedTesrGroups.TexRun head : columns.runs) {
                assertTrue(first.equals(head.matrix, 0) || second.equals(head.matrix, 0), "unexpected glint layer");
                if (!textureMatrix.equals(head.matrix, 0)) continue;
                final RetainedTesrGroups.InstanceColumns source = head.source != null ? head.source : columns;
                for (RetainedTesrGroups.TexRun run = head; run != null; run = run.next) {
                    for (int i = run.start; i < run.end; i++) {
                        assertTrue(matched < templates.length, "glint must not include uncaptured body parts");
                        final int expected = ordered ? matched : Arrays.asList(templates).indexOf(source.templates.get(i));
                        assertTrue(expected >= 0, "glint must only include the captured parts");
                        assertFalse(seen[expected], "a glint layer must not repeat a captured part");
                        seen[expected] = true;
                        assertSame(templates[expected], source.templates.get(i));
                        assertTrue(matrices[expected].equals(new Matrix4f().set(source.matrices, i * 16), 0.00001f));
                        assertEquals(info, source.infos.getLong(i));
                        assertEquals(overlay, source.overlays.getInt(i));
                        assertEquals(color, head.source != null ? head.color : source.colors.getInt(i));
                        matched++;
                    }
                }
            }
            assertEquals(templates.length, matched, "both glint layers must cover every captured armor/item part");
        }
    }

    private static void assertGlintMatchesBase(RetainedTesrGroups.Group glint, RetainedTesrGroups.InstanceColumns base) {
        final RetainedTesrGroups.TexRun head = glint.templateColumns.runs.getFirst();
        final RetainedTesrGroups.InstanceColumns source = head.source != null ? head.source : glint.templateColumns;
        int part = 0;
        for (RetainedTesrGroups.TexRun run = head; run != null; run = run.next) {
            for (int i = run.start; i < run.end; i++) {
                assertArrayEquals(Arrays.copyOfRange(base.matrices, part * 16, (part + 1) * 16),
                    Arrays.copyOfRange(source.matrices, i * 16, (i + 1) * 16),
                    "depth-equal item glint must use the exact replayed base matrix");
                part++;
            }
        }
        assertEquals(base.size, part);
    }

    private static BufferSegment onlyStreamSegment(RetainedTesrGroups groups) {
        final AngelicaBufferSource source = Reflect.get(groups, "source");
        final SegmentedBufferBuilder[] builders = Reflect.get(source, "builders");
        final var segments = Arrays.stream(builders).flatMap(builder -> builder.getSegments().stream()).toList();
        assertEquals(1, segments.size());
        return segments.getFirst();
    }

    private static long rejections(EntityShadowReplay.Rejection reason) {
        final long[] rejected = Reflect.getStatic(EntityShadowReplay.class, "rejected");
        return rejected[reason.ordinal()];
    }

    private static ObjectArrayList<EntityRenderCommands.Command> commands() {
        final EntityRenderCommands commands = Reflect.getStatic(EntityShadowReplay.class, "commands");
        return Reflect.get(commands, "commands");
    }

    private static EntityWither wither(String name, float health, double maxHealth) {
        return boss(EntityWither.class, name, health, maxHealth);
    }

    private static <T extends EntityLivingBase> T boss(Class<T> type, String name, float health, double maxHealth) {
        final T boss = IBossDisplayData.class.isAssignableFrom(type) ? mock(type)
            : mock(type, withSettings().extraInterfaces(IBossDisplayData.class));
        final DataWatcher watcher = mock(DataWatcher.class);
        when(watcher.getWatchableObjectFloat(6)).thenReturn(health);
        Reflect.set(boss, "dataWatcher", watcher);
        final IAttributeInstance maximum = mock(IAttributeInstance.class);
        when(maximum.getAttributeValue()).thenReturn(maxHealth);
        when(boss.getEntityAttribute(SharedMonsterAttributes.maxHealth)).thenReturn(maximum);
        when(boss.func_145748_c_()).thenReturn(new ChatComponentText(name));
        return boss;
    }

    private static void observeBossStatus(IBossDisplayData boss, boolean colorModifier) {
        BossStatus.setBossStatus(boss, colorModifier);
        EntityShadowReplay.recordBossStatus(boss, colorModifier);
    }

    private static void assertBossStatus(IBossDisplayData boss, boolean colorModifier) {
        assertEquals(boss.getHealth() / boss.getMaxHealth(), BossStatus.healthScale);
        assertEquals(100, BossStatus.statusBarTime);
        assertEquals(boss.func_145748_c_().getFormattedText(), BossStatus.bossName);
        assertEquals(colorModifier, BossStatus.hasColorModifier);
    }

    private static void resetBossStatus() {
        BossStatus.healthScale = -0.25f;
        BossStatus.statusBarTime = 7;
        BossStatus.bossName = "unchanged";
        BossStatus.hasColorModifier = false;
    }

    private static void assertUnchangedBossStatus() {
        assertEquals(-0.25f, BossStatus.healthScale);
        assertEquals(7, BossStatus.statusBarTime);
        assertEquals("unchanged", BossStatus.bossName);
        assertFalse(BossStatus.hasColorModifier);
    }

    private static void withBossStatus(Runnable action) {
        final float health = BossStatus.healthScale;
        final int time = BossStatus.statusBarTime;
        final String name = BossStatus.bossName;
        final boolean color = BossStatus.hasColorModifier;
        try {
            action.run();
        } finally {
            BossStatus.healthScale = health;
            BossStatus.statusBarTime = time;
            BossStatus.bossName = name;
            BossStatus.hasColorModifier = color;
        }
    }

    private static void assertAura(Object actual, DeferredEntityOverlay.ShouldRenderPassFn callback,
                                   RendererLivingEntity renderer, EntityLivingBase entity, float[] args, Matrix4f matrix) {
        assertSame(callback, Reflect.get(actual, "shouldRenderPass"));
        assertSame(renderer, Reflect.get(actual, "renderer"));
        assertSame(entity, Reflect.get(actual, "entity"));
        final String[] fields = {"partialTick", "limbSwing", "limbSwingAmount", "ageInTicks", "headYaw", "headPitch", "scale"};
        for (int i = 0; i < fields.length; i++) assertEquals(args[i], (float) Reflect.get(actual, fields[i]), fields[i]);
        final float[] actualMatrix = Reflect.get(actual, "matrix");
        assertTrue(matrix.equals(new Matrix4f().set(actualMatrix), 0.00001f), "deferred aura must use the main camera");
    }

    private static void withDeferredOverlays(Runnable action) {
        final String[] contextFields = {"overlayPassActive", "replaying", "pendingShouldRenderPass", "pendingRenderer", "pendingEntity", "pendingPartialTick"};
        final Object[] previousContext = Arrays.stream(contextFields).map(name -> Reflect.getStatic(DeferredEntityOverlay.class, name)).toArray();
        final List<?> deferred = Reflect.getStatic(DeferredEntityOverlay.class, "deferred");
        assertTrue(deferred.isEmpty());
        try {
            action.run();
        } finally {
            DeferredEntityOverlay.clear();
            for (int i = 0; i < contextFields.length; i++) Reflect.setStatic(DeferredEntityOverlay.class, contextFields[i], previousContext[i]);
        }
    }

    @BeforeEach
    void setUp() {
        previousBatching = AngelicaConfig.enableEntityBatching;
        previousReplay = AngelicaConfig.enableEntityShadowReplay;
        previousAtlas = AngelicaConfig.enableEntityTextureAtlas;
        previousExcluded = AngelicaConfig.entityShadowReplayExcludedClasses;
        previousShadow = ShadowRenderer.ACTIVE;
        previousGlintTime = GlintClock.millis();
        AngelicaConfig.enableEntityBatching = true;
        AngelicaConfig.enableEntityShadowReplay = true;
        AngelicaConfig.enableEntityTextureAtlas = false;
        AngelicaConfig.entityShadowReplayExcludedClasses = new String[0];
        when(manager.getEntityRenderObject(entity)).thenReturn(renderer);
        EntityShadowReplay.clear();
        EntityShadowReplay.beginFrame();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.enableTexture();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLSMMatrices.setTextureMatrix(0, new Matrix4f());
        GLSMCurrentAttribs.glColor4f(1, 1, 1, 1);
        GLSMCurrentAttribs.setOverlayColor(0, 0, 0, 0);
        GLSMTextures.getTextures().getTextureUnitStates(1).setEnabled(true);
        ModelPartBatcher.onTextureBind(new ResourceLocation("angelica", "test/replay"), 0);
        RenderManager.renderPosX = 1000000.25;
        RenderManager.renderPosY = 7.74;
        RenderManager.renderPosZ = -1000000.5;
        RenderManager.instance.playerViewX = 6;
        RenderManager.instance.playerViewY = 0;
        Reflect.set(batcher, "active", true);
        Reflect.set(batcher, "mode", ModelPartBatcher.Mode.ENTITIES);
        Reflect.set(batcher, "shadow", true);
        final AngelicaBufferSource source = new AngelicaBufferSource();
        source.setIdKind(AngelicaBufferSource.GroupIdKind.ENTITY);
        target = new RetainedTesrGroups(source);
        target.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(new InstanceRing()),
            new RetainedTesrTestSupport.CountingPipeline(true, true));
        Reflect.set(batcher, "activeGroups", target);
        GLSMMatrices.setModelViewMatrix(shadowView);
        GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(1, 1);
        ShadowRenderer.ACTIVE = true;
        EntityShadowReplay.beginPass(true, 0);
    }

    @AfterEach
    void tearDown() {
        EntityShadowReplay.clear();
        closeCapturePose();
        ShadowRenderer.ACTIVE = previousShadow;
        GlintClock.beginFrame(previousGlintTime);
        batcher.clear();
        AngelicaConfig.enableEntityBatching = previousBatching;
        AngelicaConfig.enableEntityShadowReplay = previousReplay;
        AngelicaConfig.enableEntityTextureAtlas = previousAtlas;
        AngelicaConfig.entityShadowReplayExcludedClasses = previousExcluded;
    }

    private void beginCapture(Entity captured) {
        captureEntry.set(GLSMMatrices.getModelViewMatrix());
        captureOpen = !EntityShadowReplay.isExcluded(captured.getClass()) && !captured.shouldRenderInPass(1);
        assertEquals(captureOpen, EntityShadowReplay.beginEntity(captured, TICK_DELTA));
    }

    private void setCapturePose(Matrix4f pose) {
        closeCapturePose();
        GLSMMatrices.glPushMatrix();
        capturePoseOpen = true;
        GLSMMatrices.applyMultMatrix(pose);
    }

    private void closeCapturePose() {
        if (!capturePoseOpen) return;
        GLSMMatrices.glPopMatrix();
        capturePoseOpen = false;
    }

    private void finishCapture(boolean completed) {
        closeCapturePose();
        if (captureOpen) EntityShadowReplay.endEntity(completed);
        captureOpen = false;
    }

    private void capture() {
        beginCapture(entity);
        assertEquals(captureOpen, EntityShadowReplay.beginRenderer(renderer, entity, TICK_DELTA, BatchEligibility.SAFE));
        GLSMCurrentAttribs.glColor4f(1, 1, 1, 1);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        setCapturePose(model);
        assertTrue(batcher.queueTemplate(template(), EntityMaterials.CUTOUT));
    }

    private void endCapture() {
        EntityShadowReplay.endRenderer(true, BatchEligibility.SAFE);
        finishCapture(true);
    }

    private void endCaptureAndStartMainPass() {
        endCapture();
        mainPass();
    }

    private void mainPass() {
        if (Reflect.getStatic(EntityShadowReplay.class, "recording") != null) finishCapture(true);
        closeCapturePose();
        captureOpen = false;
        DeferredEntityOverlay.clear();
        ShadowRenderer.ACTIVE = false;
        Reflect.set(batcher, "shadow", false);
        RenderManager.renderPosX -= 0.5;
        RenderManager.renderPosY -= 0.12;
        RenderManager.renderPosZ += 0.25;
        GLSMMatrices.setModelViewMatrix(mainView);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(0, 0);
        target = new RetainedTesrGroups(new AngelicaBufferSource());
        final AngelicaBufferSource source = Reflect.get(target, "source");
        source.setIdKind(AngelicaBufferSource.GroupIdKind.ENTITY);
        target.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(new InstanceRing()),
            new RetainedTesrTestSupport.CountingPipeline(true, true));
        Reflect.set(batcher, "activeGroups", target);
        EntityShadowReplay.beginPass(false, 0);
    }

    @Test
    void replayUsesTheMainCameraAndInheritsMainEntryState() {
        GLSMRasterizer.glShadeModel(GL11.GL_SMOOTH);
        capture();
        endCaptureAndStartMainPass();
        GLSMRasterizer.glShadeModel(GL11.GL_FLAT);
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        final RetainedTesrGroups.Group group = onlyGroup(target);
        final Matrix4f expected = new Matrix4f(mainView).translate(0.5f, 0.12f, -0.25f).mul(model);
        final Matrix4f actual = new Matrix4f().set(group.templateColumns.matrices);
        assertTrue(expected.equals(actual, 0.00001f), "replay must use the main camera with the measured renderPos difference");
        assertEquals(0, group.layer.getState().getOffsetFactor());
        assertEquals(0, group.layer.getState().getOffsetUnits());
        assertTrue(group.layer.getState().isLightmap());
        assertEquals(GL11.GL_FLAT, GLSMRasterizer.getShadeModelState().getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"inherited", "explicitNoOp", "popped"})
    void bodyLightingUsesMainInheritanceWhileGlintRemainsUnlit(String kind) {
        prepareGlintCapture();
        GLSMEnable.glDisable(GL11.GL_LIGHTING);
        final GlintCapture armor = new GlintCapture();
        captureGlint(entity, renderer, () -> {
            if (kind.equals("explicitNoOp")) {
                GLSMEnable.glDisable(GL11.GL_LIGHTING);
            } else if (kind.equals("popped")) {
                GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
                GLSMEnable.glEnable(GL11.GL_LIGHTING);
                GLSMAttribStack.glPopAttrib();
            }
            setCapturePose(model);
            assertTrue(batcher.beginArmorCapture(armor));
            assertTrue(batcher.queueTemplate(template(), EntityMaterials.CUTOUT));
            armor.endWithRestoredPose();
            assertTrue(batcher.queueSkippedArmorGlint(armor, ItemGlint.texture()));
        });
        assertFalse(onlyMaterialGroup(EntityMaterials.CUTOUT).layer.getState().isLit());
        mainPass();
        GLSMEnable.glEnable(GL11.GL_LIGHTING);

        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        final boolean inherited = !kind.equals("explicitNoOp");
        assertEquals(inherited, onlyMaterialGroup(EntityMaterials.CUTOUT).layer.getState().isLit());
        assertFalse(onlyMaterialGroup(EntityMaterials.GLINT).layer.getState().isLit());
        assertEquals(inherited, GLSMLighting.getLightingState().isEnabled(),
            "untouched or restored lighting must preserve main entry state; an explicit no-op disable must still apply");
    }

    @ParameterizedTest
    @ValueSource(strings = {"enabled", "explicitNoOp", "scoped", "afterPop"})
    void lightmapEntryChangesOnlyRejectSubmissionsThatInheritThem(String kind) {
        final boolean scoped = kind.equals("scoped") || kind.equals("afterPop");
        final boolean inheritedDraw = kind.equals("afterPop");
        final boolean enabled = !kind.equals("explicitNoOp");
        final var lightmap = GLSMTextures.getTextures().getTextureUnitStates(1);
        lightmap.setEnabled(false);
        beginCapture(entity);
        assertTrue(EntityShadowReplay.beginRenderer(renderer, entity, TICK_DELTA, BatchEligibility.SAFE));
        setCapturePose(model);
        if (scoped) GLSMAttribStack.glPushAttrib(GL11.GL_TEXTURE_BIT);
        try {
            lightmap.setEnabled(enabled);
            assertTrue(batcher.queueTemplate(template(), EntityMaterials.CUTOUT));
        } finally {
            if (scoped) GLSMAttribStack.glPopAttrib();
        }
        if (inheritedDraw) assertTrue(batcher.queueTemplate(template(), EntityMaterials.CUTOUT));
        endCaptureAndStartMainPass();
        lightmap.setEnabled(true);

        assertEquals(!inheritedDraw, EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        if (inheritedDraw) {
            assertEquals(0, target.groupCount(), "an inherited draw must reject before the earlier explicit draw is queued");
            assertEquals(1, rejections(EntityShadowReplay.Rejection.ENTRY_STATE_CHANGED));
            assertTrue(lightmap.isEnabled());
        } else {
            final RetainedTesrGroups.Group group = onlyGroup(target);
            assertEquals(1, group.templateColumns.size);
            assertEquals(enabled, group.layer.getState().isLightmap());
            assertEquals(scoped || enabled, lightmap.isEnabled(),
                "a scoped override preserves main entry state; an explicit no-op still writes the exit state");
        }
    }

    @Test
    void unbalancedRendererMatrixRejectsBeforeMainGeometryIsQueued() {
        capture();
        EntityShadowReplay.endRenderer(true, BatchEligibility.SAFE);
        EntityShadowReplay.endEntity(true);
        mainPass();

        assertFalse(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        assertEquals(0, target.groupCount());
        assertEquals(1, rejections(EntityShadowReplay.Rejection.EXIT_STATE_UNSUPPORTED));
        assertTrue(mainView.equals(GLSMMatrices.getModelViewMatrix(), 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"armor", "held", "dropped"})
    void skippedGlintExitWritesStillApplyWhenTheyWereNoOpsDuringCapture(String kind) {
        applyGlintExit(kind);
        beginCapture(entity);
        assertTrue(EntityShadowReplay.beginRenderer(renderer, entity, TICK_DELTA, BatchEligibility.SAFE));
        applyGlintExit(kind);
        setCapturePose(model);
        assertTrue(batcher.queueTemplate(template(), EntityMaterials.CUTOUT));
        applyGlintExit(kind);
        endCaptureAndStartMainPass();

        GLSMCurrentAttribs.glColor4f(0.2f, 0.3f, 0.4f, 0.5f);
        GLSMEnable.glEnable(GL11.GL_BLEND);
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        GLSMEnable.glDisable(GL11.GL_LIGHTING);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LESS);
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.63f);
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));

        final boolean armor = kind.equals("armor");
        final boolean held = kind.equals("held");
        assertEquals(armor ? 1 : 0.5f * 0.76f, GLSMCurrentAttribs.getColor().getRed());
        assertEquals(armor ? 1 : 0.25f * 0.76f, GLSMCurrentAttribs.getColor().getGreen());
        assertEquals(armor ? 1 : 0.8f * 0.76f, GLSMCurrentAttribs.getColor().getBlue());
        assertEquals(1, GLSMCurrentAttribs.getColor().getAlpha());
        assertFalse(GLSMBlend.isEffectiveBlendEnabled());
        assertTrue(GLSMLighting.getLightingState().isEnabled());
        assertEquals(GL11.GL_LEQUAL, GLSMDepthStencilAlpha.getDepthState().getFunc());
        assertEquals(armor, GLSMDepthStencilAlpha.isEffectiveDepthMaskEnabled());
        assertEquals(GL11.GL_SRC_COLOR, GLSMBlend.getBlendState().getSrcRgb());
        assertEquals(GL11.GL_ONE, GLSMBlend.getBlendState().getDstRgb());
        assertEquals(held ? GL11.GL_ONE : GL11.GL_SRC_COLOR, GLSMBlend.getBlendState().getSrcAlpha());
        assertEquals(held ? GL11.GL_ZERO : GL11.GL_ONE, GLSMBlend.getBlendState().getDstAlpha());
        assertEquals(0.63f, GLSMDepthStencilAlpha.getAlphaState().getReference(), "an untouched exit field must retain the main entry value");
        assertTrue(mainView.equals(GLSMMatrices.getModelViewMatrix(), 0));
    }

    @Test
    void followingLiveModelInheritsTheFinalTextureBoundAfterCapturedGeometry() {
        final ResourceLocation finalTexture = new ResourceLocation("angelica", "test/final_binding");
        final ResourceLocation liveTexture = new ResourceLocation("angelica", "test/preceding_live_binding");
        final int capturedId = GLSMTextures.glGenTextures();
        final int liveId = GLSMTextures.glGenTextures();
        try {
            capture();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, capturedId);
            ModelPartBatcher.onTextureBind(finalTexture, capturedId);
            endCaptureAndStartMainPass();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, liveId);
            ModelPartBatcher.onTextureBind(liveTexture, liveId);
            assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
            assertEquals(capturedId, GLSMTextures.getBoundTextureForServerState(0));

            final TemplateBuffer following = template();
            assertTrue(batcher.queueTemplate(following, EntityMaterials.CUTOUT), "the next live renderer must inherit matching texture metadata");
            final var layers = RetainedTesrTestSupport.byLayer(target);
            assertEquals(2, layers.size());
            boolean found = false;
            for (var entry : layers.entrySet()) {
                assertNotEquals(liveTexture, entry.getKey().getTextureId());
                if (finalTexture.equals(entry.getKey().getTextureId())) {
                    found = true;
                    assertEquals(1, entry.getValue().size());
                    assertEquals(List.of(following), entry.getValue().getFirst().templateColumns.templates);
                }
            }
            assertTrue(found, "the final texture can differ from every captured geometry command's texture");
        } finally {
            GLSMTextures.glDeleteTextures(capturedId);
            GLSMTextures.glDeleteTextures(liveId);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"explicitNoOp", "enableOnly", "valuesOnly", "explicit", "inherited", "popped"})
    void polygonOffsetAndTextureMatrixKeepOnlyTheWritesTheCaptureMade(String operation) {
        final Matrix4f texture = new Matrix4f().translation(0.2f, 0.3f, 0);
        if (operation.equals("enableOnly") || operation.equals("valuesOnly")) {
            GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        beginCapture(entity);
        assertTrue(EntityShadowReplay.beginRenderer(renderer, entity, TICK_DELTA, BatchEligibility.SAFE));
        switch (operation) {
            case "explicitNoOp" -> {
                GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GLSMRasterizer.glPolygonOffset(1, 1);
            }
            case "enableOnly" -> GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            case "valuesOnly" -> GLSMRasterizer.glPolygonOffset(-2, -3);
            case "explicit" -> {
                GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GLSMRasterizer.glPolygonOffset(-2, -3);
                GLSMMatrices.setTextureMatrix(0, texture);
            }
            case "popped" -> {
                GLSMAttribStack.glPushAttrib(GL11.GL_POLYGON_BIT | GL11.GL_ENABLE_BIT);
                GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                GLSMRasterizer.glPolygonOffset(7, 8);
                GLSMAttribStack.glPopAttrib();
            }
            default -> {
            }
        }
        assertTrue(batcher.queueTemplate(template(), EntityMaterials.CUTOUT));
        endCaptureAndStartMainPass();
        GLSMMatrices.setTextureMatrix(0, new Matrix4f());
        GLSMRasterizer.glPolygonOffset(-4, -5);
        if (!operation.equals("explicitNoOp") && !operation.equals("enableOnly")) {
            GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        }

        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        final RetainedTesrGroups.Group group = onlyGroup(target);
        final boolean values = operation.equals("valuesOnly") || operation.equals("explicit");
        assertEquals(operation.equals("explicitNoOp") ? 1 : values ? -2 : -4, group.layer.getState().getOffsetFactor());
        assertEquals(operation.equals("explicitNoOp") ? 1 : values ? -3 : -5, group.layer.getState().getOffsetUnits());
        assertEquals(operation.equals("explicit"), texture.equals(group.templateColumns.runs.getFirst().matrix, 0));
    }

    @Test
    void sharedRangePreservesChangingPartDataWithoutChangingShadowInstances() {
        capture();
        final RetainedTesrGroups.Group shadow = onlyGroup(target);
        final TemplateBuffer second = template();
        final Matrix4f secondModel = new Matrix4f(model).translate(2, 3, 4);
        setCapturePose(secondModel);
        GLSMCurrentAttribs.glColor4f(0.25f, 0.5f, 0.75f, 1);
        GLSMCurrentAttribs.setOverlayColor(1, 0, 0, 0.5f);
        assertTrue(batcher.queueTemplate(second, EntityMaterials.CUTOUT));
        EntityShadowReplay.endRenderer(true, BatchEligibility.SAFE);
        final ObjectArrayList<EntityRenderCommands.Command> entries = commands();
        assertEquals(1, entries.size());
        final EntityRenderCommands.Model captured = (EntityRenderCommands.Model) entries.getFirst();
        assertSame(shadow.templateColumns, captured.source);
        assertEquals(2, captured.end - captured.start);
        final float[] original = shadow.templateColumns.matrices.clone();
        GLSMCurrentAttribs.setOverlayColor(0, 0, 0, 0);
        mainPass();
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        final RetainedTesrGroups.InstanceColumns actual = onlyGroup(target).templateColumns;
        assertEquals(2, actual.size);
        assertSame(second, actual.templates.get(1));
        assertEquals(shadow.templateColumns.colors, actual.colors);
        assertEquals(shadow.templateColumns.lights, actual.lights);
        assertEquals(shadow.templateColumns.infos, actual.infos);
        assertEquals(0, actual.overlays.getInt(0));
        assertEquals(AngelicaBufferSource.packAbgr(1, 0, 0, 0.5f), actual.overlays.getInt(1));
        final Matrix4f expected = new Matrix4f(mainView).translate(0.5f, 0.12f, -0.25f).mul(secondModel);
        assertTrue(expected.equals(new Matrix4f().set(actual.matrices, 16), 0.00001f));
        assertTrue(new Matrix4f().set(original).equals(new Matrix4f().set(shadow.templateColumns.matrices), 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptedModelsSharingAnAtlasPageKeepTheirOriginalTextures(boolean sameTextureId) {
        final ResourceLocation firstTexture = new ResourceLocation("angelica", "test/first_original");
        final ResourceLocation secondTexture = new ResourceLocation("angelica", "test/second_original");
        final ResourceLocation atlasTexture = new ResourceLocation("angelica", "test/shared_atlas");
        final EntityTextureAtlas.Page page = new EntityTextureAtlas.Page(atlasTexture, 0);
        final EntityTextureAtlas.Slot firstSlot = new EntityTextureAtlas.Slot(page, 0, 0, 64, 32);
        final EntityTextureAtlas.Slot secondSlot = new EntityTextureAtlas.Slot(page, 66, 0, 64, 32);
        final RenderLayer layer = RenderLayer.tesr(atlasTexture, EntityMaterials.CUTOUT, PassOverride.NONE,
            1, 1, -1, DrawState.CULL_BACK, false, true);
        final CubeParams[] cube = cubes(1);
        final TemplateBuffer geometry = template();
        final Matrix4f pose = new Matrix4f(shadowView).mul(model);
        beginCapture(entity);
        assertTrue(EntityShadowReplay.beginRenderer(renderer, entity, TICK_DELTA, BatchEligibility.SAFE));
        target.queueAndRecord(geometry, cube, 1, layer, pose, 0x00F000F0, -1, 0, 0L, 0, null, firstSlot, true,
            new EntityRenderCommands.DrawInputs().set(firstTexture, 17, EntityMaterials.CUTOUT, PassOverride.NONE, true, 1, 1, -1,
                DrawState.CULL_BACK, false, true, false));
        pose.translate(2, 0, 0);
        target.queueAndRecord(geometry, cube, 1, layer, pose, 0x00F000F0, -1, 0, 0L, 0, null, secondSlot, true,
            new EntityRenderCommands.DrawInputs().set(secondTexture, sameTextureId ? 17 : 18, EntityMaterials.CUTOUT, PassOverride.NONE,
                true, 1, 1, -1, DrawState.CULL_BACK, false, true, false));
        final RetainedTesrGroups.InstanceColumns shadow = onlyGroup(target).cubeColumns;
        assertEquals(2, shadow.size);
        assertSame(firstSlot, shadow.atlasSlot(0));
        assertSame(secondSlot, shadow.atlasSlot(1));
        endCaptureAndStartMainPass();

        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        final var mainLayers = RetainedTesrTestSupport.byLayer(target);
        assertEquals(2, mainLayers.size(), "distinct source textures sharing one shadow page must not merge");
        final var textures = mainLayers.keySet().stream().map(RenderLayer::getTextureId).toList();
        assertTrue(textures.contains(firstTexture));
        assertTrue(textures.contains(secondTexture));
        for (var groups : mainLayers.values()) {
            assertEquals(1, groups.size());
            assertEquals(1, groups.getFirst().cubeColumns.size);
            assertNull(groups.getFirst().cubeColumns.atlasSlot(0), "main submission must not retain the shadow atlas slot");
        }
        assertSame(firstSlot, shadow.atlasSlot(0));
        assertSame(secondSlot, shadow.atlasSlot(1));
    }

    @Test
    void rebuiltSourceRejectsAllRangesBeforeAnyReplayIsQueued() {
        capture();
        assertTrue(batcher.queueTemplate(template(), EntityMaterials.TRANSLUCENT));
        EntityShadowReplay.endRenderer(true, BatchEligibility.SAFE);
        final ObjectArrayList<EntityRenderCommands.Command> entries = commands();
        assertEquals(2, entries.size());
        final RetainedTesrGroups.InstanceColumns source = ((EntityRenderCommands.Model) entries.get(1)).source;
        mainPass();
        source.clear();
        assertFalse(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        assertEquals(0, target.groupCount());
    }

    @Test
    void sharedRangeFallsBackWhenMainPassCannotInstance() {
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        try (CapturedStateScope ignored = new CapturedStateScope()) {
            Reflect.set(batcher, "shaderPipeline", mock(DeferredWorldRenderingPipeline.class));
            state.setCurrentEntityAndItem(71, 23);
            state.setCurrentBlockEntity(19);
            state.setCurrentEntityColor(0.25f, 0.5f, 0.75f, 1);
            capture();
            endCaptureAndStartMainPass();
            target.beginPass(new Matrix4f(), 0, 0, 0);
            state.setCurrentEntityAndItem(89, 29);
            state.setCurrentBlockEntity(31);
            state.setCurrentEntityColor(1, 0, 0, 0.5f);
            assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
            assertEquals(1, target.streamedInstances);
            final BufferSegment actual = onlyStreamSegment(target);
            assertEquals(71, actual.getEntityId());
            assertEquals(19, actual.getRenderedBlockEntityId());
            assertEquals(23, actual.getItemId());
            assertEquals(AngelicaBufferSource.packAbgr(0.25f, 0.5f, 0.75f, 1), actual.getEntityColor());
            assertEquals(89, state.getCurrentRenderedEntity());
            assertEquals(31, state.getCurrentRenderedBlockEntity());
            assertEquals(29, state.getCurrentRenderedItem());
            assertTrue(state.getCurrentEntityColor().equals(1, 0, 0, 0.5f));
        }
    }

    @Test
    void armorGlintReplaysOnlyItsBaseAfterCaptureReuseAndReversedEntityOrder() {
        prepareGlintCapture();
        final ResourceLocation glintTexture = new ResourceLocation("angelica", "test/armor_glint");
        final Entity other = mock(Entity.class);
        final TemplateBuffer body = template(), firstArmor = template(), secondArmor = template(), otherArmor = template();
        final Matrix4f firstPose = new Matrix4f(model).translate(0.25f, 0.5f, -0.75f);
        final Matrix4f secondPose = new Matrix4f(model).rotateX(0.3f).translate(-0.5f, 1.25f, 0.75f);
        final Matrix4f otherPose = new Matrix4f(model).translate(5, 2, -3).rotateZ(0.4f);
        final GlintCapture capture = new GlintCapture();
        final long drawsBefore = GLSMDraws.drawCalls;
        captureGlint(entity, renderer, () -> {
            setCapturePose(model);
            assertTrue(batcher.queueTemplate(body, EntityMaterials.CUTOUT));
            assertTrue(batcher.beginArmorCapture(capture));
            setCapturePose(firstPose);
            assertTrue(batcher.queueTemplate(firstArmor, EntityMaterials.CUTOUT));
            setCapturePose(secondPose);
            assertTrue(batcher.queueTemplate(secondArmor, EntityMaterials.CUTOUT));
            setCapturePose(model);
            capture.endWithRestoredPose();
            assertTrue(batcher.queueSkippedArmorGlint(capture, glintTexture));
        });
        captureGlint(other, renderer, () -> {
            setCapturePose(otherPose);
            assertTrue(batcher.beginArmorCapture(capture));
            assertTrue(batcher.queueTemplate(otherArmor, EntityMaterials.CUTOUT));
            capture.endWithRestoredPose();
            assertTrue(batcher.queueSkippedArmorGlint(capture, glintTexture));
        });
        capture.reset();
        assertEquals(4, onlyGroup(target).templateColumns.size, "the shadow pass must only queue base geometry");
        mainPass();
        final Matrix4f callerMatrix = new Matrix4f(GLSMMatrices.getModelViewMatrix());
        assertTrue(EntityShadowReplay.replay(manager, other, TICK_DELTA, 0));
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        assertEquals(List.of(otherArmor, body, firstArmor, secondArmor), onlyMaterialGroup(EntityMaterials.CUTOUT).templateColumns.templates);
        final RetainedTesrGroups.Group glint = onlyMaterialGroup(EntityMaterials.GLINT);
        assertEquals(glintTexture, glint.layer.getTextureId());
        assertGlintLayers(glint, GlintClock.firstArmorMatrix(), GlintClock.secondArmorMatrix(),
            new TemplateBuffer[]{otherArmor, firstArmor, secondArmor},
            new Matrix4f[]{mainPose(otherPose), mainPose(firstPose), mainPose(secondPose)}, 0L, 0);
        assertEquals(0, glint.layer.getState().getOffsetFactor());
        assertEquals(0, glint.layer.getState().getOffsetUnits());
        assertEquals(drawsBefore, GLSMDraws.drawCalls, "capture and replay must leave draws to the batch flush");
        assertTrue(callerMatrix.equals(GLSMMatrices.getModelViewMatrix(), 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void armorCubeGlintTracksActualMainRangesAndReleasesTheirDependencies(boolean sourceNull) {
        prepareGlintCapture();
        if (sourceNull) target.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(new InstanceRing()));
        final TemplateBuffer first = template(), second = template(), unrelated = template();
        final CubeParams[] cube = cubes(1);
        final Matrix4f secondPose = new Matrix4f(model).translate(0.33f, -0.41f, 0.57f).rotateY(0.23f);
        final GlintCapture capture = new GlintCapture();
        captureGlint(entity, renderer, () -> {
            setCapturePose(model);
            assertTrue(batcher.beginArmorCapture(capture));
            queueCubes(first, cube);
            setCapturePose(secondPose);
            queueCubes(second, cube);
            setCapturePose(model);
            capture.endWithRestoredPose();
            assertTrue(batcher.queueSkippedArmorGlint(capture, ItemGlint.texture()));
        });
        final ObjectArrayList<EntityRenderCommands.Model> bases = glintBases();
        for (EntityRenderCommands.Model base : bases) assertEquals(sourceNull, base.source == null);
        final EntityRenderCommands.Glint capturedGlint = bases.getFirst().glint;
        mainPass();
        GLSMMatrices.setModelViewMatrix(mainPose(new Matrix4f(model).translate(-3, 0, 0)));
        queueCubes(unrelated, cube);
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        final RetainedTesrGroups.InstanceColumns mainBase = onlyMaterialGroup(EntityMaterials.CUTOUT).cubeColumns;
        assertEquals(List.of(unrelated, first, second), mainBase.templates);
        final ObjectArrayList<RetainedTesrGroups.InstanceRange> ranges = new ObjectArrayList<>();
        int next = 1;
        for (EntityRenderCommands.Model base : bases) {
            final RetainedTesrGroups.InstanceRange range = base.replayed;
            ranges.add(range);
            assertSame(mainBase, range.columns);
            assertEquals(next, range.start);
            assertTrue(range.end > range.start);
            assertTrue(range.reuseUpload);
            assertTrue(range.run.glintBase, "armor uploads must stay separate from ordinary opaque parts");
            next = range.end;
        }
        assertEquals(3, next);
        final RetainedTesrGroups.Group glint = onlyMaterialGroup(EntityMaterials.GLINT);
        assertGlintLayers(glint, GlintClock.firstArmorMatrix(), GlintClock.secondArmorMatrix(),
            new TemplateBuffer[]{first, second}, new Matrix4f[]{mainPose(model), mainPose(secondPose)}, 0, 0);
        EntityShadowReplay.beginFrame();
        assertTrue(commands().isEmpty());
        assertNull(capturedGlint.base);
        for (EntityRenderCommands.Model base : bases) {
            assertNull(base.source);
            assertNull(base.glint);
            assertNull(base.nextGlintBase);
            assertNull(base.replayed);
        }
        for (RetainedTesrGroups.InstanceRange range : ranges) {
            assertNull(range.columns);
            assertNull(range.run);
            assertFalse(range.reuseUpload);
        }
        assertGlintLayers(glint, GlintClock.firstArmorMatrix(), GlintClock.secondArmorMatrix(),
            new TemplateBuffer[]{first, second}, new Matrix4f[]{mainPose(model), mainPose(secondPose)}, 0, 0);
    }

    @Test
    void armorGlintFollowsBaseRangesSplitAcrossTexturesAndMaterials() {
        prepareGlintCapture();
        final ResourceLocation firstTexture = new ResourceLocation("angelica", "test/armor_first");
        final ResourceLocation secondTexture = new ResourceLocation("angelica", "test/armor_second");
        final TemplateBuffer first = template(), second = template(), third = template();
        final Matrix4f secondPose = new Matrix4f(model).translate(1.5f, 0.25f, -0.5f);
        final Matrix4f thirdPose = new Matrix4f(model).rotateZ(0.47f).translate(-0.25f, 0.75f, 1.25f);
        final GlintCapture capture = new GlintCapture();
        captureGlint(entity, renderer, () -> {
            setCapturePose(model);
            assertTrue(batcher.beginArmorCapture(capture));
            ModelPartBatcher.onTextureBind(firstTexture, 0);
            assertTrue(batcher.queueTemplate(first, EntityMaterials.CUTOUT));
            setCapturePose(secondPose);
            ModelPartBatcher.onTextureBind(secondTexture, 0);
            assertTrue(batcher.queueTemplate(second, EntityMaterials.SOLID));
            setCapturePose(thirdPose);
            ModelPartBatcher.onTextureBind(firstTexture, 0);
            assertTrue(batcher.queueTemplate(third, EntityMaterials.CUTOUT));
            setCapturePose(model);
            capture.endWithRestoredPose();
            assertTrue(batcher.queueSkippedArmorGlint(capture, ItemGlint.texture()));
        });
        mainPass();
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        assertEquals(firstTexture, onlyMaterialGroup(EntityMaterials.CUTOUT).layer.getTextureId());
        assertEquals(secondTexture, onlyMaterialGroup(EntityMaterials.SOLID).layer.getTextureId());
        final ObjectArrayList<EntityRenderCommands.Model> bases = glintBases();
        for (EntityRenderCommands.Model base : bases) {
            assertTrue(base.replayed.reuseUpload);
            assertTrue(base.replayed.run.glintBase);
        }
        assertGlintLayers(onlyMaterialGroup(EntityMaterials.GLINT), GlintClock.firstArmorMatrix(), GlintClock.secondArmorMatrix(),
            new TemplateBuffer[]{first, second, third}, new Matrix4f[]{mainPose(model), mainPose(secondPose), mainPose(thirdPose)}, 0, 0, false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void itemGlintUsesOnlyTheLastInstanceOfACoalescedBaseRange(boolean dropped) {
        prepareGlintCapture();
        final TemplateBuffer first = template(), second = template(), enchanted = template(), unrelated = template();
        final Matrix4f secondPose = new Matrix4f(model).translate(0.21f, 0.43f, -0.65f);
        final Matrix4f enchantedPose = new Matrix4f(model).rotateY(0.37f).translate(-0.17f, 0.39f, 0.61f);
        captureGlint(entity, renderer, () -> {
            setCapturePose(model);
            assertTrue(batcher.queueTemplate(first, EntityMaterials.DROPPED_ITEM_CUTOUT));
            setCapturePose(secondPose);
            assertTrue(batcher.queueTemplate(second, EntityMaterials.DROPPED_ITEM_CUTOUT));
            setCapturePose(enchantedPose);
            assertTrue(batcher.queueTemplate(enchanted, EntityMaterials.DROPPED_ITEM_CUTOUT));
            assertTrue(queueItemGlint(enchanted, dropped));
        });
        final EntityRenderCommands.Model captured = glintBases().getFirst();
        assertEquals(3, captured.end - captured.start, "the shadow base must exercise the coalesced range path");
        mainPass();
        assertTrue(batcher.queueTemplate(unrelated, EntityMaterials.DROPPED_ITEM_CUTOUT));
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        final RetainedTesrGroups.InstanceColumns base = onlyMaterialGroup(EntityMaterials.DROPPED_ITEM_CUTOUT).templateColumns;
        assertEquals(List.of(unrelated, first, second, enchanted), base.templates);
        assertSame(base, captured.replayed.columns);
        assertEquals(1, captured.replayed.start);
        assertEquals(4, captured.replayed.end);
        assertFalse(captured.replayed.reuseUpload);
        assertFalse(captured.replayed.run.glintBase);
        final RetainedTesrGroups.Group glint = onlyMaterialGroup(dropped ? EntityMaterials.GLINT : EntityMaterials.ITEM_GLINT);
        assertGlintLayers(glint, ItemGlint.firstMatrix(), ItemGlint.secondMatrix(),
            new TemplateBuffer[]{enchanted}, new Matrix4f[]{mainPose(enchantedPose)}, 0, 0);
        assertArrayEquals(Arrays.copyOfRange(base.matrices, 3 * 16, 4 * 16), Arrays.copyOf(glint.templateColumns.matrices, 16));
    }

    private void queueCubes(TemplateBuffer template, CubeParams[] cubes) {
        assertTrue(Reflect.<Boolean>invoke(batcher, "queueTemplate",
            new Class<?>[]{TemplateBuffer.class, CubeParams[].class, boolean.class, float.class, TesrMaterial.class, boolean.class},
            template, cubes, true, 1.0f, EntityMaterials.CUTOUT, false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void itemGlintReplaysCapturedInputsAndKeepsTheLaterItemLayerAfterGlint(boolean dropped) {
        prepareGlintCapture();
        final TesrMaterial glintMaterial = dropped ? EntityMaterials.GLINT : EntityMaterials.ITEM_GLINT;
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        try (CapturedStateScope ignored = new CapturedStateScope()) {
            useGlintPipeline(RenderCondition.ENTITIES);
            state.setCurrentEntityAndItem(71, 23);
            state.setCurrentBlockEntity(19);
            state.setCurrentEntityColor(0.25f, 0.5f, 0.75f, 1);
            final int overlay = AngelicaBufferSource.packAbgr(0.25f, 0.5f, 0.75f, 1);
            final TemplateBuffer item = template(), laterLayer = template();
            final Matrix4f pose = new Matrix4f(model).rotateXYZ(0.17f, -0.29f, 0.41f).translate(0.37f, -0.63f, 1.19f);
            final long drawsBefore = GLSMDraws.drawCalls;
            captureGlint(entity, renderer, () -> {
                setCapturePose(pose);
                assertTrue(batcher.queueTemplate(item, EntityMaterials.DROPPED_ITEM_CUTOUT));
                assertTrue(queueItemGlint(item, dropped));
                assertTrue(batcher.queueTemplate(laterLayer, EntityMaterials.DROPPED_ITEM_CUTOUT, true));
            });
            assertTrue(materialGroups(glintMaterial).isEmpty(), "item glint must not enter the shadow queue");
            mainPass();
            state.setCurrentEntityAndItem(89, 29);
            state.setCurrentBlockEntity(31);
            state.setCurrentEntityColor(1, 0, 0, 0.5f);
            final Matrix4f callerMatrix = new Matrix4f(GLSMMatrices.getModelViewMatrix());
            assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
            final RetainedTesrGroups.Group glint = onlyMaterialGroup(glintMaterial);
            assertEquals(ItemGlint.texture(), glint.layer.getTextureId());
            assertGlintLayers(glint, ItemGlint.firstMatrix(), ItemGlint.secondMatrix(),
                new TemplateBuffer[]{item}, new Matrix4f[]{mainPose(pose)}, InstancedAttribs.packEntityInfo(71, 19, 0), overlay);
            final ObjectArrayList<RetainedTesrGroups.Group> baseGroups = materialGroups(EntityMaterials.DROPPED_ITEM_CUTOUT);
            assertEquals(2, baseGroups.size());
            for (RetainedTesrGroups.Group group : baseGroups) {
                assertEquals(1, group.templateColumns.size);
                assertEquals(InstancedAttribs.packEntityInfo(71, 19, 23), group.templateColumns.infos.getLong(0));
                if (group.layer.getTransparencyType() == TransparencyType.AFTER_GLINT) {
                    assertSame(laterLayer, group.templateColumns.templates.getFirst());
                } else {
                    assertEquals(TransparencyType.OPAQUE, group.layer.getTransparencyType());
                    assertSame(item, group.templateColumns.templates.getFirst());
                    assertGlintMatchesBase(glint, group.templateColumns);
                }
            }
            assertEquals(drawsBefore, GLSMDraws.drawCalls);
            assertTrue(callerMatrix.equals(GLSMMatrices.getModelViewMatrix(), 0));
            assertTrue(new Matrix4f().equals(GLSMTextures.getTextures().getTextureUnitMatrix(0), 0));
            assertEquals(89, state.getCurrentRenderedEntity());
            assertEquals(31, state.getCurrentRenderedBlockEntity());
            assertEquals(29, state.getCurrentRenderedItem());
            assertTrue(state.getCurrentEntityColor().equals(1, 0, 0, 0.5f));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void droppedStackGlintKeepsEachCopyPoseWhenEntitiesReplayInReverseOrder(int copies) {
        prepareGlintCapture();
        GLSMEnable.glEnable(GL11.GL_STENCIL_TEST);
        GLSMDepthStencilAlpha.glStencilFunc(GL11.GL_EQUAL, 3, 0xFF);
        final Entity other = mock(Entity.class);
        final TemplateBuffer[] items = {template(), template()};
        final Matrix4f[][] poses = new Matrix4f[2][copies];
        final long drawsBefore = GLSMDraws.drawCalls;
        for (int entityIndex = 0; entityIndex < 2; entityIndex++) {
            final int index = entityIndex;
            captureGlint(index == 0 ? entity : other, renderer, () -> {
                for (int copy = 0; copy < copies; copy++) {
                    final Matrix4f pose = new Matrix4f(model).translate(index * 4.3f + copy * 0.17f, copy * -0.13f, index * 1.7f + copy * 0.09f)
                        .rotateXYZ(0.11f * copy, -0.23f * index, 0.07f * (copy + 1));
                    poses[index][copy] = pose;
                    setCapturePose(pose);
                    assertTrue(batcher.queueTemplate(items[index], EntityMaterials.DROPPED_ITEM_CUTOUT));
                    assertTrue(batcher.queueSkippedItemGlint(items[index], EntityMaterials.GLINT));
                }
            });
        }
        assertEquals(copies * 2, onlyGroup(target).templateColumns.size);
        mainPass();
        assertTrue(EntityShadowReplay.replay(manager, other, TICK_DELTA, 0));
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        final TemplateBuffer[] expectedTemplates = new TemplateBuffer[copies * 2];
        final Matrix4f[] expectedPoses = new Matrix4f[copies * 2];
        for (int i = 0; i < expectedTemplates.length; i++) {
            final int index = 1 - i / copies;
            expectedTemplates[i] = items[index];
            expectedPoses[i] = mainPose(poses[index][i % copies]);
        }
        final RetainedTesrGroups.Group base = onlyMaterialGroup(EntityMaterials.DROPPED_ITEM_CUTOUT);
        assertEquals(Arrays.asList(expectedTemplates), base.templateColumns.templates);
        final RetainedTesrGroups.Group glint = onlyMaterialGroup(EntityMaterials.GLINT);
        assertGlintLayers(glint, ItemGlint.firstMatrix(), ItemGlint.secondMatrix(), expectedTemplates, expectedPoses, 0, 0);
        assertGlintMatchesBase(glint, base.templateColumns);
        assertTrue(materialGroups(EntityMaterials.ITEM_GLINT).isEmpty(), "dropped copies must not use held-item stencil deduplication");
        assertEquals(GL11.GL_EQUAL, GLSMDepthStencilAlpha.getStencilState().getFuncFront());
        assertEquals(drawsBefore, GLSMDraws.drawCalls);
    }

    @Test
    void droppedGlintEntryReusedForArmorDoesNotKeepTheItemTemplateOrLayers() {
        prepareGlintCapture();
        final TemplateBuffer item = template();
        captureGlint(entity, renderer, () -> {
            setCapturePose(model);
            assertTrue(batcher.queueTemplate(item, EntityMaterials.DROPPED_ITEM_CUTOUT));
            assertTrue(batcher.queueSkippedItemGlint(item, EntityMaterials.GLINT));
        });
        EntityShadowReplay.beginFrame();
        GLSMMatrices.setModelViewMatrix(shadowView);
        EntityShadowReplay.beginPass(true, 0);
        final TemplateBuffer first = template(), second = template();
        final Matrix4f secondPose = new Matrix4f(model).translate(0.31f, 0.47f, -0.59f);
        final GlintCapture capture = new GlintCapture();
        captureGlint(entity, renderer, () -> {
            setCapturePose(model);
            assertTrue(batcher.beginArmorCapture(capture));
            assertTrue(batcher.queueTemplate(first, EntityMaterials.CUTOUT));
            setCapturePose(secondPose);
            assertTrue(batcher.queueTemplate(second, EntityMaterials.CUTOUT));
            setCapturePose(model);
            capture.endWithRestoredPose();
            assertTrue(batcher.queueSkippedArmorGlint(capture, ItemGlint.texture()));
        });
        mainPass();
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        assertGlintLayers(onlyMaterialGroup(EntityMaterials.GLINT), GlintClock.firstArmorMatrix(), GlintClock.secondArmorMatrix(),
            new TemplateBuffer[]{first, second}, new Matrix4f[]{mainPose(model), mainPose(secondPose)}, 0, 0);
        assertTrue(materialGroups(EntityMaterials.DROPPED_ITEM_CUTOUT).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"stale", "instancing", "bodyVariant", "glintVariant", "stencil", "failed",
        "dropped:stale", "dropped:bodyVariant", "dropped:glintVariant"})
    void unavailableOrInvalidGlintRejectsBeforeAnyReplayIsQueued(String reason) {
        prepareGlintCapture();
        final boolean dropped = reason.startsWith("dropped:");
        final String failure = dropped ? reason.substring("dropped:".length()) : reason;
        if (failure.equals("bodyVariant") || failure.equals("glintVariant")) {
            final DeferredWorldRenderingPipeline pipeline = useGlintPipeline(RenderCondition.ENTITIES);
            final RenderCondition missing = failure.equals("bodyVariant") ? RenderCondition.ENTITIES : RenderCondition.GLINT;
            when(pipeline.hasInstancedVariant(missing, InputAvailability.BOTH, Instancing.TEMPLATE)).thenReturn(false);
        }
        final GlintCapture capture = new GlintCapture();
        captureGlint(entity, renderer, () -> {
            setCapturePose(model);
            if (dropped || failure.equals("stencil")) {
                final TemplateBuffer item = template();
                assertTrue(batcher.queueTemplate(item, EntityMaterials.DROPPED_ITEM_CUTOUT));
                assertTrue(queueItemGlint(item, dropped));
            } else {
                assertTrue(batcher.beginArmorCapture(capture));
                assertTrue(batcher.queueTemplate(template(), EntityMaterials.CUTOUT));
                capture.endWithRestoredPose();
                assertTrue(batcher.queueSkippedArmorGlint(capture, ItemGlint.texture()));
            }
            assertTrue(batcher.queueTemplate(template(), EntityMaterials.TRANSLUCENT));
            if (failure.equals("failed")) EntityShadowReplay.rejectRenderFailed();
        });
        final RetainedTesrGroups.InstanceColumns laterSource = onlyMaterialGroup(EntityMaterials.TRANSLUCENT).templateColumns;
        mainPass();
        switch (failure) {
            case "stale" -> laterSource.clear();
            case "instancing" -> target.beginPass(new Matrix4f(), 0, 0, 0);
            case "stencil" -> {
                GLSMEnable.glEnable(GL11.GL_STENCIL_TEST);
                GLSMDepthStencilAlpha.glStencilFunc(GL11.GL_EQUAL, 3, 0xFF);
            }
        }
        assertFalse(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        assertEquals(0, target.groupCount());
        assertEquals(0, target.streamedInstances);
        final EntityShadowReplay.Rejection expected = switch (failure) {
            case "stale" -> EntityShadowReplay.Rejection.SHARED_RANGE_CHANGED;
            case "stencil" -> EntityShadowReplay.Rejection.UNCAPTURED_STATE;
            case "failed" -> EntityShadowReplay.Rejection.RENDER_FAILED;
            default -> EntityShadowReplay.Rejection.NOT_READY;
        };
        assertEquals(1, rejections(expected));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void glintPreflightUsesCapturedTranslucencyInsteadOfMaterialOrMainDeclaration(boolean translucent) {
        prepareGlintCapture();
        useGlintPipeline(translucent ? RenderCondition.ENTITIES_TRANSLUCENT : RenderCondition.ENTITIES);
        final TesrMaterial material = translucent ? EntityMaterials.DROPPED_ITEM_CUTOUT : EntityMaterials.DROPPED_ITEM_TRANSLUCENT;
        final TemplateBuffer item = template();
        final Boolean beforeCapture = GbufferPrograms.beginTranslucencyDeclaration(translucent);
        try {
            captureGlint(entity, renderer, () -> {
                setCapturePose(model);
                assertTrue(batcher.queueTemplate(item, material));
                assertTrue(batcher.queueSkippedItemGlint(item, EntityMaterials.ITEM_GLINT));
            });
        } finally {
            GbufferPrograms.endTranslucencyDeclaration(beforeCapture);
        }
        mainPass();
        final Boolean beforeReplay = GbufferPrograms.beginTranslucencyDeclaration(!translucent);
        try {
            assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
            assertSame(item, onlyMaterialGroup(material).templateColumns.templates.getFirst());
            assertEquals(2, onlyMaterialGroup(EntityMaterials.ITEM_GLINT).templateColumns.runs.size());
            assertEquals(!translucent, GbufferPrograms.getDeclaredTranslucency());
        } finally {
            GbufferPrograms.endTranslucencyDeclaration(beforeReplay);
        }
    }

    @Test
    void glintEntryPoolReuseDoesNotAddGlintOrRequireInstancingOnTheNextFrame() {
        prepareGlintCapture();
        final GlintCapture capture = new GlintCapture();
        captureGlint(entity, renderer, () -> {
            setCapturePose(model);
            assertTrue(batcher.beginArmorCapture(capture));
            assertTrue(batcher.queueTemplate(template(), EntityMaterials.CUTOUT));
            capture.endWithRestoredPose();
            assertTrue(batcher.queueSkippedArmorGlint(capture, ItemGlint.texture()));
            final TemplateBuffer held = template();
            assertTrue(batcher.queueTemplate(held, EntityMaterials.DROPPED_ITEM_CUTOUT));
            assertTrue(batcher.queueSkippedItemGlint(held, EntityMaterials.ITEM_GLINT));
        });
        EntityShadowReplay.beginFrame();
        GLSMMatrices.setModelViewMatrix(shadowView);
        EntityShadowReplay.beginPass(true, 0);
        captureGlint(entity, renderer, () -> {
            setCapturePose(model);
            for (TesrMaterial material : new TesrMaterial[]{EntityMaterials.CUTOUT, EntityMaterials.SOLID,
                EntityMaterials.TRANSLUCENT, EntityMaterials.ADDITIVE}) {
                assertTrue(batcher.queueTemplate(template(), material));
            }
        });
        mainPass();
        target.beginPass(new Matrix4f(), 0, 0, 0);
        assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        assertEquals(4, target.streamedInstances);
        assertEquals(4, target.groupCount());
        assertTrue(materialGroups(EntityMaterials.GLINT).isEmpty());
        assertTrue(materialGroups(EntityMaterials.ITEM_GLINT).isEmpty());
    }

    private DeferredWorldRenderingPipeline useGlintPipeline(RenderCondition body) {
        final DeferredWorldRenderingPipeline pipeline = mock(DeferredWorldRenderingPipeline.class);
        when(pipeline.hasInstancedVariant(body, InputAvailability.BOTH, Instancing.TEMPLATE)).thenReturn(true);
        when(pipeline.hasInstancedVariant(RenderCondition.GLINT, InputAvailability.BOTH, Instancing.TEMPLATE)).thenReturn(true);
        Reflect.set(batcher, "shaderPipeline", pipeline);
        return pipeline;
    }

    private boolean queueItemGlint(TemplateBuffer item, boolean dropped) {
        return batcher.queueSkippedItemGlint(item, dropped ? EntityMaterials.GLINT : EntityMaterials.ITEM_GLINT);
    }

    private void prepareGlintCapture() {
        Reflect.set(batcher, "instancedThisCycle", true);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
        GLSMEnable.glDisable(GL11.GL_STENCIL_TEST);
        GlintClock.beginFrame(123456789L);
    }

    private void captureGlint(Entity captured, Render render, Runnable queue) {
        when(manager.getEntityRenderObject(captured)).thenReturn(render);
        beginCapture(captured);
        assertTrue(EntityShadowReplay.beginRenderer(render, captured, TICK_DELTA, BatchEligibility.SAFE));
        assertTrue(BatchEligibility.begin(BatchEligibility.SAFE, GLSMDraws.drawCalls));
        boolean completed = false;
        try {
            queue.run();
            completed = true;
        } finally {
            EntityShadowReplay.endRenderer(completed, BatchEligibility.end(BatchEligibility.SAFE, GLSMDraws.drawCalls));
            finishCapture(completed);
        }
    }

    private Matrix4f mainPose(Matrix4f pose) {
        return new Matrix4f(mainView).translate(0.5f, 0.12f, -0.25f).mul(pose);
    }

    private ObjectArrayList<RetainedTesrGroups.Group> materialGroups(TesrMaterial material) {
        final ObjectArrayList<RetainedTesrGroups.Group> matches = new ObjectArrayList<>();
        for (ObjectArrayList<RetainedTesrGroups.Group> groups : RetainedTesrTestSupport.byLayer(target).values()) {
            for (RetainedTesrGroups.Group group : groups) if (group.material == material) matches.add(group);
        }
        return matches;
    }

    private RetainedTesrGroups.Group onlyMaterialGroup(TesrMaterial material) {
        final ObjectArrayList<RetainedTesrGroups.Group> matches = materialGroups(material);
        assertEquals(1, matches.size());
        return matches.getFirst();
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "expected", "bail", "shadowOnly", "failed", "caught", "nested", "nextFrame", "passOne", "camera", "lightmap", "partialTicks", "renderer", "afterRenderer", "outerFailed", "nestedEntity"})
    void incompleteOrChangedRecordsNeverQueuePartialGeometry(String reason) {
        capture();
        switch (reason) {
            case "foreign" -> GLSMDraws.drawCalls++;
            case "expected" -> {
                BatchEligibility.begin(BatchEligibility.SAFE, GLSMDraws.drawCalls);
                BatchEligibility.beginExpectedDraws(GLSMDraws.drawCalls);
                GLSMDraws.drawCalls++;
                BatchEligibility.endExpectedDraws(GLSMDraws.drawCalls);
                BatchEligibility.end(BatchEligibility.SAFE, GLSMDraws.drawCalls);
            }
            case "bail" -> BatchEligibility.onBail(ModelPartBatcher.BailReason.TEXTURE);
            case "shadowOnly" -> EntityShadowReplay.rejectShadowOnly();
            case "caught" -> EntityShadowReplay.rejectRenderFailed();
            case "nested" -> assertFalse(EntityShadowReplay.beginRenderer(renderer, mock(Entity.class), TICK_DELTA, BatchEligibility.SAFE));
            case "nestedEntity" -> assertFalse(EntityShadowReplay.beginEntity(mock(Entity.class), TICK_DELTA));
        }
        EntityShadowReplay.endRenderer(!reason.equals("failed"), BatchEligibility.SAFE);
        if (reason.equals("afterRenderer")) GLSMDraws.drawCalls++;
        if (reason.equals("outerFailed")) finishCapture(false);
        if (reason.equals("nextFrame")) EntityShadowReplay.beginFrame();
        mainPass();
        switch (reason) {
            case "camera" -> RenderManager.instance.playerViewY = 0.2f;
            case "lightmap" -> GLSMTextures.getTextures().getTextureUnitStates(1).setEnabled(false);
            case "renderer" -> when(manager.getEntityRenderObject(entity)).thenReturn(mock(Render.class));
        }
        assertFalse(EntityShadowReplay.replay(manager, entity, reason.equals("partialTicks") ? 0.5f : TICK_DELTA, reason.equals("passOne") ? 1 : 0));
        assertEquals(0, target.groupCount());
        if (reason.equals("lightmap")) assertEquals(1, rejections(EntityShadowReplay.Rejection.ENTRY_STATE_CHANGED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"queue", "copyRange", "referenceRange", "stream"})
    void uncapturedAcceptedSubmissionsKeepShadowGeometryButRejectTheWholeReplay(String operation) {
        withBossStatus(() -> withDeferredOverlays(() -> {
            final EntityWither boss = wither("Captured boss", 100, 300);
            final RenderWither bossRenderer = mock(RenderWither.class);
            final var callback = mock(DeferredEntityOverlay.ShouldRenderPassFn.class);
            captureLiving(boss, bossRenderer);
            observeBossStatus(boss, false);
            queueAura(callback, bossRenderer, boss, new float[]{TICK_DELTA, 1, 2, 3, 4, 5, 0.0625f},
                new Matrix4f(shadowView).mul(model));
            final RetainedTesrGroups.Group shadow = onlyGroup(target);
            final RetainedTesrGroups.InstanceColumns columns = shadow.templateColumns;
            final TemplateBuffer extra = template();
            final Matrix4f pose = new Matrix4f(shadowView).mul(model).translate(2, 0, 0);
            final long draws = GLSMDraws.drawCalls;
            switch (operation) {
                case "queue" -> target.queue(0, extra, shadow.layer, EntityMaterials.CUTOUT, pose, 0, -1, 0, 0L, 0, null);
                case "copyRange" -> target.copyInstances(columns, 0, 1, shadow.layer, EntityMaterials.CUTOUT,
                    0, -1, 0, null);
                case "referenceRange" -> target.referenceInstances(columns, 0, 1, shadow.layer, EntityMaterials.CUTOUT,
                    -1, new Matrix4f());
                case "stream" -> target.streamVertices(shadow.layer, 0, extra.data, extra.vertexCount, pose);
                default -> throw new AssertionError(operation);
            }
            if (operation.equals("stream")) {
                assertEquals(extra.vertexCount, onlyStreamSegment(target).getVertexCount());
                assertEquals(1, columns.size);
            } else if (operation.equals("referenceRange")) {
                assertEquals(1, columns.referencedParts);
                assertEquals(1, columns.size);
            } else {
                assertEquals(2, columns.size, "unrecorded submission must still reach shadow storage");
            }
            assertEquals(draws, GLSMDraws.drawCalls, "rejection must not force an immediate shadow draw");
            endCaptureAndStartMainPass();
            resetBossStatus();

            assertFalse(EntityShadowReplay.replay(manager, boss, TICK_DELTA, 0));
            assertEquals(0, target.groupCount());
            assertEquals(0, target.streamedInstances);
            assertUnchangedBossStatus();
            final List<?> deferred = Reflect.getStatic(DeferredEntityOverlay.class, "deferred");
            assertTrue(deferred.isEmpty());
            verifyNoInteractions(callback, bossRenderer);
        }));
    }

    @Test
    void fireReplayCopiesVerticesAndRestoresShaderInputsForTheNextEntity() {
        capture();
        EntityShadowReplay.endRenderer(true, BatchEligibility.SAFE);
        final CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        Reflect.set(batcher, "shaderPipeline", mock(DeferredWorldRenderingPipeline.class));
        state.pushCurrentEntityAndItem();
        state.pushCurrentBlockEntity();
        state.pushCurrentEntityColor();
        try {
            state.setCurrentEntityAndItem(71, 23);
            state.setCurrentBlockEntity(19);
            state.setCurrentEntityColor(0.25f, 0.5f, 0.75f, 1);
            final TemplateBuffer quad = template();
            final int[] vertices = quad.data.clone();
            queueFire(vertices, quad.vertexCount);
            final BufferSegment shadowSegment = onlyStreamSegment(target);
            final ByteBuffer shadowBuffer = Reflect.get(shadowSegment.getOwner(), "buffer");
            final int positionOffset = 0;
            final float shadowVertexX = shadowBuffer.getFloat(positionOffset);
            final float shadowVertexY = shadowBuffer.getFloat(positionOffset + 4);
            final float shadowVertexZ = shadowBuffer.getFloat(positionOffset + 8);
            Arrays.fill(vertices, 0);
            assertEquals(1, target.streamedInstances);
            state.setCurrentEntityAndItem(89, 29);
            state.setCurrentBlockEntity(31);
            state.setCurrentEntityColor(1, 0, 0, 0.5f);
            mainPass();
            assertTrue(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
            assertEquals(1, target.streamedInstances);
            assertEquals(89, state.getCurrentRenderedEntity());
            assertEquals(29, state.getCurrentRenderedItem());
            assertEquals(31, state.getCurrentRenderedBlockEntity());
            assertTrue(state.getCurrentEntityColor().equals(1, 0, 0, 0.5f));
            final BufferSegment actual = onlyStreamSegment(target);
            assertEquals(shadowSegment.getVertexCount(), actual.getVertexCount());
            assertEquals(shadowSegment.getBlockEntityId(), actual.getBlockEntityId());
            assertEquals(71, actual.getEntityId());
            assertEquals(19, actual.getRenderedBlockEntityId());
            assertEquals(23, actual.getItemId());
            assertEquals(shadowSegment.getEntityColor(), actual.getEntityColor());
            assertEquals(0, actual.getRenderType().getState().getOffsetFactor());
            assertEquals(shadowSegment.getRenderType().getState().isLightmap(), actual.getRenderType().getState().isLightmap());
            final var expected = new Matrix4f(mainView).translate(0.5f, 0.12f, -0.25f).mul(new Matrix4f(shadowView).invert())
                .transformPosition(shadowVertexX, shadowVertexY, shadowVertexZ, new Vector3f());
            final ByteBuffer actualBuffer = Reflect.get(actual.getOwner(), "buffer");
            assertEquals(expected.x, actualBuffer.getFloat(positionOffset), 0.00001f);
            assertEquals(expected.y, actualBuffer.getFloat(positionOffset + 4), 0.00001f);
            assertEquals(expected.z, actualBuffer.getFloat(positionOffset + 8), 0.00001f);
        } finally {
            state.popCurrentEntityColor();
            state.popCurrentBlockEntity();
            state.popCurrentEntityAndItem();
        }
    }

    private void queueFire(int[] vertices, int vertexCount) {
        GLSMEnable.glEnable(GL11.GL_DEPTH_TEST);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
        GLSMEnable.glDisable(GL11.GL_BLEND);
        GLSMEnable.glDisable(GL11.GL_ALPHA_TEST);
        final Tessellator tess = mock(Tessellator.class);
        tess.isDrawing = true;
        tess.drawMode = GL11.GL_QUADS;
        tess.rawBuffer = vertices;
        tess.vertexCount = vertexCount;
        tess.rawBufferIndex = tess.rawBuffer.length;
        final long draws = GLSMDraws.drawCalls;
        batcher.beginEntityFire();
        try {
            ModelPartBatcher.drawTessellator(tess);
        } finally {
            batcher.endEntityFire();
        }
        assertEquals(draws, GLSMDraws.drawCalls);
    }

    @Test
    void bossReplaysUpdateTheBossBarInMainEntityOrder() {
        withBossStatus(() -> {
            final EntityWither first = wither("First", 45, 300);
            final EntityDragon second = boss(EntityDragon.class, "Second", 180, 300);
            final EntityLivingBase third = boss(EntityLivingBase.class, "Custom boss", 25, 100);
            final EntityWither live = wither("Live", 80, 200);
            final RenderWither witherRenderer = mock(RenderWither.class);
            final RenderDragon dragonRenderer = mock(RenderDragon.class);
            final RendererLivingEntity customRenderer = mock(RendererLivingEntity.class);
            captureLiving(first, witherRenderer);
            observeBossStatus(first, true);
            endCapture();
            captureLiving(second, dragonRenderer);
            observeBossStatus(second, false);
            endCapture();
            captureLiving(third, customRenderer);
            observeBossStatus((IBossDisplayData) third, false);
            endCaptureAndStartMainPass();

            final DataWatcher watcher = Reflect.get(second, "dataWatcher");
            when(watcher.getWatchableObjectFloat(6)).thenReturn(210f);
            when(second.func_145748_c_()).thenReturn(new ChatComponentText("Renamed second"));
            resetBossStatus();
            assertTrue(EntityShadowReplay.replay(manager, second, TICK_DELTA, 0));
            assertBossStatus(second, false);
            BossStatus.setBossStatus(live, true);
            assertBossStatus(live, true);
            BossStatus.statusBarTime = 1;
            assertTrue(EntityShadowReplay.replay(manager, first, TICK_DELTA, 0));
            assertBossStatus(first, true);
            assertTrue(EntityShadowReplay.replay(manager, third, TICK_DELTA, 0));
            assertBossStatus((IBossDisplayData) third, false);
            assertEquals(3, onlyGroup(target).templateColumns.size);
            verifyNoInteractions(witherRenderer, dragonRenderer, customRenderer);
        });
    }

    @Test
    void bossUpdateAloneDoesNotMakeACanceledEntityReplayable() {
        withBossStatus(() -> {
            final EntityLivingBase boss = boss(EntityLivingBase.class, "Canceled", 150, 300);
            when(manager.getEntityRenderObject(boss)).thenReturn(renderer);
            beginCapture(boss);
            assertTrue(EntityShadowReplay.beginRenderer(renderer, boss, TICK_DELTA, BatchEligibility.SAFE));
            observeBossStatus((IBossDisplayData) boss, true);
            endCaptureAndStartMainPass();
            resetBossStatus();

            assertFalse(EntityShadowReplay.replay(manager, boss, TICK_DELTA, 0));
            assertUnchangedBossStatus();
            assertEquals(0, target.groupCount());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "foreign", "multiple", "nonBoss", "dragonEyes", "program", "excluded",
        "edited:health", "edited:time", "edited:name", "edited:color"})
    void rejectedBossRecordsChangeNeitherTheHudNorTheQueues(String reason) {
        withBossStatus(() -> withDeferredOverlays(() -> {
            final EntityLivingBase boss = reason.equals("dragonEyes")
                ? boss(EntityDragon.class, "Dragon", 150, 300)
                : boss(EntityLivingBase.class, "Rejected", 150, 300);
            final EntityLivingBase rendered = reason.equals("nonBoss") ? mock(EntityCreeper.class) : boss;
            final RendererLivingEntity livingRenderer = mock(RendererLivingEntity.class);
            final var callback = mock(DeferredEntityOverlay.ShouldRenderPassFn.class);
            if (reason.equals("excluded")) AngelicaConfig.entityShadowReplayExcludedClasses = new String[]{EntityLivingBase.class.getName()};
            captureLiving(rendered, livingRenderer);
            if (!reason.equals("missing")) {
                observeBossStatus(reason.equals("foreign") ? wither("Foreign", 25, 300) : (IBossDisplayData) boss, false);
            }
            if (reason.equals("multiple")) observeBossStatus((IBossDisplayData) boss, true);
            switch (reason) {
                case "edited:health" -> BossStatus.healthScale = 0.75f;
                case "edited:time" -> BossStatus.statusBarTime = -1;
                case "edited:name" -> BossStatus.bossName = "Custom boss name";
                case "edited:color" -> BossStatus.hasColorModifier = true;
                default -> {
                }
            }
            queueAura(callback, livingRenderer, rendered, new float[]{TICK_DELTA, 1, 2, 3, 4, 5, 0.0625f},
                new Matrix4f(shadowView).mul(model));
            if (reason.equals("dragonEyes")) {
                final int depth = GLSMAttribStack.pushState(StateSet.BATCH);
                try {
                    GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
                    GLSMEnable.glEnable(GL11.GL_BLEND);
                    GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
                    GLSMEnable.glDisable(GL11.GL_ALPHA_TEST);
                    GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_EQUAL);
                    GLSMDepthStencilAlpha.glDepthMask(true);
                    assertFalse(batcher.queueTemplate(template(), null));
                } finally {
                    GLSMAttribStack.popStateTo(depth);
                }
            }
            endCaptureAndStartMainPass();
            if (reason.equals("program")) {
                final DeferredWorldRenderingPipeline pipeline = mock(DeferredWorldRenderingPipeline.class);
                when(pipeline.getActivePassProgramId()).thenReturn(GLSMPrograms.getActiveProgram() + 1);
                Reflect.set(batcher, "shaderPipeline", pipeline);
                assertTrue(batcher.entityPassActive(), "program ownership must not disable the active pass's fallback flushing");
            }
            resetBossStatus();
            assertFalse(EntityShadowReplay.replay(manager, rendered, TICK_DELTA, 0));
            assertUnchangedBossStatus();
            assertEquals(0, target.groupCount());
            assertEquals(0, target.streamedInstances);
            final List<?> deferred = Reflect.getStatic(DeferredEntityOverlay.class, "deferred");
            assertTrue(deferred.isEmpty());
            verifyNoInteractions(callback, livingRenderer);
            if (reason.equals("program")) assertEquals(1, rejections(EntityShadowReplay.Rejection.NOT_READY));
        }));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reusedRecordsDoNotInheritAnEarlierBossUpdate(boolean nextIsBoss) {
        withBossStatus(() -> {
            final EntityWither first = wither("Previous", 100, 300);
            final RenderWither witherRenderer = mock(RenderWither.class);
            captureLiving(first, witherRenderer);
            observeBossStatus(first, true);
            endCapture();
            EntityShadowReplay.beginFrame();
            GLSMMatrices.setModelViewMatrix(shadowView);
            EntityShadowReplay.beginPass(true, 0);
            final Entity next;
            if (nextIsBoss) {
                next = boss(EntityLivingBase.class, "Missing update", 200, 300);
                captureLiving((EntityLivingBase) next, witherRenderer);
            } else {
                next = entity;
                capture();
            }
            endCaptureAndStartMainPass();
            resetBossStatus();
            assertEquals(!nextIsBoss, EntityShadowReplay.replay(manager, next, TICK_DELTA, 0));
            assertUnchangedBossStatus();
            assertFalse(EntityShadowReplay.replay(manager, first, TICK_DELTA, 0));
            assertUnchangedBossStatus();
            assertEquals(nextIsBoss ? 0 : 1, target.groupCount());
        });
    }

    @Test
    void deferredAurasSurviveRecyclingAndFollowMainEntityOrder() {
        withDeferredOverlays(() -> {
            final EntityCreeper first = mock(EntityCreeper.class);
            final EntityCreeper second = mock(EntityCreeper.class);
            final EntityCreeper live = mock(EntityCreeper.class);
            final RenderCreeper creeperRenderer = mock(RenderCreeper.class);
            final var callback = mock(DeferredEntityOverlay.ShouldRenderPassFn.class);
            final var liveCallback = mock(DeferredEntityOverlay.ShouldRenderPassFn.class);
            final float[] firstArgs = {0.25f, 1, 2, 3, 4, 5, 0.0625f};
            final float[] secondArgs = {0.75f, 6, 7, 8, 9, 10, 0.125f};
            final float[] liveArgs = {0.5f, 11, 12, 13, 14, 15, 0.25f};
            final Matrix4f firstAura = new Matrix4f(model).translate(1, 2, 3).scale(1.1f);
            final Matrix4f secondAura = new Matrix4f(model).rotateZ(0.3f).translate(4, 5, 6);
            final Matrix4f liveAura = new Matrix4f(mainView).translate(-1, -2, -3);
            captureLiving(first, creeperRenderer);
            queueAura(callback, creeperRenderer, first, firstArgs, new Matrix4f(shadowView).mul(firstAura));
            endCapture();
            captureLiving(second, creeperRenderer);
            queueAura(callback, creeperRenderer, second, secondArgs, new Matrix4f(shadowView).mul(secondAura));
            endCapture();

            final List<?> deferred = Reflect.getStatic(DeferredEntityOverlay.class, "deferred");
            assertEquals(2, deferred.size());
            final Object recycled = deferred.get(1);
            mainPass();
            queueAura(liveCallback, creeperRenderer, live, liveArgs, liveAura);
            assertSame(recycled, deferred.getFirst(), "the live aura must overwrite a recycled shadow queue entry");
            final long draws = GLSMDraws.drawCalls;
            assertTrue(EntityShadowReplay.replay(manager, second, TICK_DELTA, 0));
            assertTrue(EntityShadowReplay.replay(manager, first, TICK_DELTA, 0));
            assertEquals(2, onlyGroup(target).templateColumns.size, "both bodies must still replay");
            assertEquals(3, deferred.size(), "replay must enqueue both captured auras after the live main aura");
            assertEquals(draws, GLSMDraws.drawCalls, "replay must leave aura drawing to the deferred phase");
            verifyNoInteractions(callback, liveCallback, creeperRenderer);
            assertTrue(liveAura.equals(GLSMMatrices.getModelViewMatrix(), 0));

            EntityShadowReplay.beginFrame();
            assertAura(deferred.get(0), liveCallback, creeperRenderer, live, liveArgs, liveAura);
            final Matrix4f mainOrigin = new Matrix4f(mainView).translate(0.5f, 0.12f, -0.25f);
            assertAura(deferred.get(1), callback, creeperRenderer, second, secondArgs, new Matrix4f(mainOrigin).mul(secondAura));
            assertAura(deferred.get(2), callback, creeperRenderer, first, firstArgs, new Matrix4f(mainOrigin).mul(firstAura));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryAndMarkedOnlyEntitiesDoNotReplayAnAura(boolean markOnly) {
        withDeferredOverlays(() -> {
            final EntityCreeper creeper = mock(EntityCreeper.class);
            final RenderCreeper creeperRenderer = mock(RenderCreeper.class);
            final var callback = mock(DeferredEntityOverlay.ShouldRenderPassFn.class);
            captureLiving(creeper, creeperRenderer);
            queueAura(callback, creeperRenderer, creeper, new float[]{TICK_DELTA, 1, 2, 3, 4, 5, 0.0625f},
                new Matrix4f(shadowView).mul(model));
            endCapture();
            EntityShadowReplay.beginFrame();
            GLSMMatrices.setModelViewMatrix(shadowView);
            EntityShadowReplay.beginPass(true, 0);
            captureLiving(creeper, creeperRenderer);
            assertTrue(batcher.queueTemplate(template(), EntityMaterials.TRANSLUCENT));
            if (markOnly) DeferredEntityOverlay.markOverlayPass(callback, creeperRenderer, creeper, TICK_DELTA);
            DeferredEntityOverlay.clearStaleOverlayFlag();
            endCaptureAndStartMainPass();
            final long draws = GLSMDraws.drawCalls;
            assertTrue(EntityShadowReplay.replay(manager, creeper, TICK_DELTA, 0));
            assertEquals(2, target.groupCount(), "the previous aura entry must be reusable as a model range");
            final List<?> deferred = Reflect.getStatic(DeferredEntityOverlay.class, "deferred");
            assertTrue(deferred.isEmpty());
            assertEquals(draws, GLSMDraws.drawCalls);
            verifyNoInteractions(callback, creeperRenderer);
        });
    }

    @Test
    void invalidRangeAfterAnAuraRejectsTheWholeReplay() {
        withDeferredOverlays(() -> {
            final EntityCreeper creeper = mock(EntityCreeper.class);
            final RenderCreeper creeperRenderer = mock(RenderCreeper.class);
            final var callback = mock(DeferredEntityOverlay.ShouldRenderPassFn.class);
            captureLiving(creeper, creeperRenderer);
            queueAura(callback, creeperRenderer, creeper, new float[]{TICK_DELTA, 1, 2, 3, 4, 5, 0.0625f},
                new Matrix4f(shadowView).mul(model));
            assertTrue(batcher.queueTemplate(template(), EntityMaterials.TRANSLUCENT));
            endCapture();
            final ObjectArrayList<EntityRenderCommands.Command> entries = commands();
            final RetainedTesrGroups.InstanceColumns source = ((EntityRenderCommands.Model) entries.getLast()).source;
            mainPass();
            source.clear();
            assertFalse(EntityShadowReplay.replay(manager, creeper, TICK_DELTA, 0));
            assertEquals(0, target.groupCount());
            final List<?> deferred = Reflect.getStatic(DeferredEntityOverlay.class, "deferred");
            assertTrue(deferred.isEmpty(), "the aura before a stale model range must not be enqueued");
            verifyNoInteractions(callback, creeperRenderer);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"armored", "unarmored", "stale"})
    void witherBodyReplaysWhileArmorKeepsItsDeferredPhase(String armor) {
        withBossStatus(() -> withDeferredOverlays(() -> {
            final EntityWither wither = wither("Armored", 100, 300);
            final RenderWither witherRenderer = mock(RenderWither.class);
            final ModelBase armorModel = mock(ModelBase.class);
            witherRenderer.renderPassModel = armorModel;
            final var callback = mock(DeferredEntityOverlay.ShouldRenderPassFn.class);
            when(callback.invoke(wither, 1, TICK_DELTA)).thenReturn(1);
            captureLiving(wither, witherRenderer);
            observeBossStatus(wither, true);
            final Matrix4f armorModelView = new Matrix4f(model).translate(0, -0.01f, 0).scale(1.1f);
            setCapturePose(armorModelView);
            if (!armor.equals("unarmored")) DeferredEntityOverlay.markOverlayPass(callback, witherRenderer, wither, TICK_DELTA);
            if (armor.equals("armored")) DeferredEntityOverlay.deferRender(1, 2, 3, 4, 5, 0.0625f);
            DeferredEntityOverlay.clearStaleOverlayFlag();
            endCaptureAndStartMainPass();
            resetBossStatus();
            final long draws = GLSMDraws.drawCalls;
            assertTrue(EntityShadowReplay.replay(manager, wither, TICK_DELTA, 0));
            assertBossStatus(wither, true);
            assertEquals(1, onlyGroup(target).templateColumns.size);
            assertEquals(draws, GLSMDraws.drawCalls);
            final List<?> deferred = Reflect.getStatic(DeferredEntityOverlay.class, "deferred");
            assertEquals(armor.equals("armored") ? 1 : 0, deferred.size());
            verifyNoInteractions(callback, armorModel, witherRenderer);
            if (armor.equals("armored")) {
                final Matrix4f expected = new Matrix4f(mainView).translate(0.5f, 0.12f, -0.25f).mul(armorModelView);
                assertAura(deferred.getFirst(), callback, witherRenderer, wither,
                    new float[]{TICK_DELTA, 1, 2, 3, 4, 5, 0.0625f}, expected);
            }
            try (CapturedStateScope ignored = new CapturedStateScope()) {
                DeferredEntityOverlay.renderAll();
            }
            if (armor.equals("armored")) {
                final var order = inOrder(callback, armorModel);
                order.verify(callback).invoke(wither, 1, TICK_DELTA);
                order.verify(armorModel).setLivingAnimations(wither, 1, 2, TICK_DELTA);
                order.verify(armorModel).render(wither, 1, 2, 3, 4, 5, 0.0625f);
                order.verify(callback).invoke(wither, 2, TICK_DELTA);
                verifyNoMoreInteractions(callback, armorModel);
            } else {
                verifyNoInteractions(callback, armorModel);
            }
            assertTrue(deferred.isEmpty());
            assertFalse(DeferredEntityOverlay.isOverlayPassActive());
            assertFalse(DeferredEntityOverlay.isReplaying());
            assertTrue(mainView.equals(GLSMMatrices.getModelViewMatrix(), 0));
            assertBossStatus(wither, true);
        }));
    }

    private void captureLiving(EntityLivingBase living, RendererLivingEntity livingRenderer) {
        when(manager.getEntityRenderObject(living)).thenReturn(livingRenderer);
        beginCapture(living);
        assertEquals(captureOpen, EntityShadowReplay.beginRenderer(livingRenderer, living, TICK_DELTA, BatchEligibility.SAFE));
        setCapturePose(model);
        assertTrue(batcher.queueTemplate(template(), EntityMaterials.CUTOUT));
    }

    private void queueAura(DeferredEntityOverlay.ShouldRenderPassFn callback, RendererLivingEntity renderer,
                           EntityLivingBase entity, float[] args, Matrix4f matrix) {
        if (captureOpen) setCapturePose(new Matrix4f(captureEntry).invert().mul(matrix));
        else GLSMMatrices.setModelViewMatrix(matrix);
        DeferredEntityOverlay.markOverlayPass(callback, renderer, entity, args[0]);
        DeferredEntityOverlay.deferRender(args[1], args[2], args[3], args[4], args[5], args[6]);
    }

    @Test
    void secondPassEntitiesAndExcludedSubclassesStayLive() {
        when(entity.shouldRenderInPass(1)).thenReturn(true);
        capture();
        endCaptureAndStartMainPass();
        assertFalse(EntityShadowReplay.replay(manager, entity, TICK_DELTA, 0));
        AngelicaConfig.entityShadowReplayExcludedClasses = new String[]{"net.minecraft.entity.monster.EntityMob"};
        assertTrue(EntityShadowReplay.isExcluded(EntityZombie.class));
        assertTrue(EntityShadowReplay.isExcluded(EntityPlayer.class));
    }
}

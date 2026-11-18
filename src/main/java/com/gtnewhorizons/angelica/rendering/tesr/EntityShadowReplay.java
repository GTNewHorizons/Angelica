package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.ExitState;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.DeferredEntityOverlay;
import com.gtnewhorizons.angelica.rendering.tesr.EntityRenderCommands.DrawInputs;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.InstanceColumns;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.Setter;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Arrays;
import java.util.Objects;

/**
 * Records a shadow-pass entity call, then submits its models, fire, and deferred overlays in the main pass.
 */
public final class EntityShadowReplay {
    private static final Tracy.ZoneId Z_REPLAY = Tracy.zoneId("entityReplay", Tracy.COLOR_CLIENT);
    private static final long P_REPLAYED = Tracy.plotHandle("entityReplay.replayed");
    private static final long P_SHARED_PARTS = Tracy.plotHandle("entityReplay.sharedParts");
    private static final long P_SHARED_RANGES = Tracy.plotHandle("entityReplay.sharedRanges");
    private static final long P_STREAM_ENTRIES = Tracy.plotHandle("entityReplay.streamEntries");
    private static final long P_RECORDED = Tracy.plotHandle("entityReplay.recorded");
    private static final long P_OUTSIDE_MAIN_VIEW = Tracy.plotHandle("entityReplay.outsideMainView");
    private static final Rejection[] REJECTIONS = Rejection.values();
    private static final long[] rejected = new long[REJECTIONS.length];
    private static final Reference2ObjectOpenHashMap<Entity, Record> records = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<Class<?>, Boolean> excluded = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<Class<?>, Reference2IntOpenHashMap<Class<?>>> loggedRejections = new Reference2ObjectOpenHashMap<>();
    private static final Pool<Record> recordPool = new Pool<>(Record::new, Record::clear);
    private static final EntityRenderCommands commands = new EntityRenderCommands();
    private static final Matrix4f shadowInverse = new Matrix4f();
    private static final Matrix4f mainFromShadow = new Matrix4f();
    private static Record recording;
    @Setter private static Frustrum mainView;
    private static Class<?> lastType;
    private static boolean lastExcluded;
    private static long drawsAtStart, replayed, outsideMainView;
    private static boolean shadowViewValid, shadowAvailable, mainReady, logRejections;
    private static double shadowX, shadowY, shadowZ;
    private static float shadowYaw, shadowPitch;
    private static float capturedBossHealthScale;
    private static int capturedBossBarTime;
    private static String capturedBossName;
    private static boolean capturedBossColorModifier;

    private EntityShadowReplay() {
    }

    public static void beginFrame() {
        if (Tracy.ENABLED) {
            Tracy.plotInt(P_REPLAYED, replayed);
            Tracy.plotInt(P_SHARED_PARTS, commands.sharedParts);
            Tracy.plotInt(P_SHARED_RANGES, commands.sharedRanges);
            Tracy.plotInt(P_STREAM_ENTRIES, commands.streamEntries);
            Tracy.plotInt(P_RECORDED, recordPool.size());
            Tracy.plotInt(P_OUTSIDE_MAIN_VIEW, outsideMainView);
            for (Rejection reason : REJECTIONS) Tracy.plotInt(reason.plot, rejected[reason.ordinal()]);
        }
        replayed = outsideMainView = 0;
        commands.sharedParts = commands.sharedRanges = commands.streamEntries = 0;
        Arrays.fill(rejected, 0);
        resetRecords();
        mainView = null;
        logRejections = SystemProperties.debugTooling();
    }

    private static void resetRecords() {
        if (recording != null && recording.capturingState) GLStateManager.cancelExitCapture();
        records.clear();
        commands.clear();
        recordPool.clear();
        recording = null;
        capturedBossName = null;
        shadowViewValid = shadowAvailable = mainReady = false;
    }

    static void beginPass(boolean shadow, int renderPass) {
        if (shadow) {
            resetRecords();
            shadowInverse.set(GLSMMatrices.getModelViewMatrix()).invert();
            shadowViewValid = shadowInverse.isFinite();
            shadowX = RenderManager.renderPosX;
            shadowY = RenderManager.renderPosY;
            shadowZ = RenderManager.renderPosZ;
            shadowYaw = RenderManager.instance.playerViewY;
            shadowPitch = RenderManager.instance.playerViewX;
        } else {
            mainReady = shadowAvailable && renderPass == 0;
            if (mainReady) {
                mainFromShadow.set(GLSMMatrices.getModelViewMatrix()).translate(
                    (float) (shadowX - RenderManager.renderPosX), (float) (shadowY - RenderManager.renderPosY),
                    (float) (shadowZ - RenderManager.renderPosZ)).mul(shadowInverse);
            }
        }
    }

    public static boolean beginEntity(Entity entity, float partialTicks) {
        if (recording != null) {
            reject(Rejection.FOREIGN_DRAW);
            return false;
        }
        if (!shadowViewValid || !ShadowRenderer.ACTIVE || !ModelPartBatcher.INSTANCE.entitiesActive()) return false;
        shadowAvailable = true;
        // replay() rejects these without a record
        if (isExcluded(entity.getClass()) || entity.shouldRenderInPass(1)) return false;
        if (!inMainView(entity)) {
            outsideMainView++;
            return false;
        }
        final Record record = recordPool.acquire();
        record.start = record.end = commands.size();
        record.entity = entity;
        record.partialTicks = partialTicks;
        record.hasBossStatus = false;
        record.bossColorModifier = false;
        record.rejection = null;
        records.put(entity, record);
        recording = record;
        record.capturingState = true;
        GLStateManager.beginExitCapture(record.exitState);
        drawsAtStart = GLSMDraws.drawCalls;
        return true;
    }

    public static void endEntity(boolean completed) {
        if (recording == null) return;
        if (recording.capturingState) {
            recording.capturingState = false;
            if (!GLStateManager.finishExitCapture(recording.exitState)) reject(Rejection.EXIT_STATE_UNSUPPORTED);
        }
        if (GLSMDraws.drawCalls != drawsAtStart) reject(Rejection.FOREIGN_DRAW);
        if (recording.renderer == null) reject(Rejection.UNSAFE_RENDERER);
        if (!completed) reject(Rejection.RENDER_FAILED);
        if (recording.entity instanceof IBossDisplayData && (!recording.hasBossStatus || bossStatusChanged())) {
            reject(Rejection.UNCAPTURED_STATE);
        }
        recording.end = commands.size();
        if (recording.start == recording.end) reject(Rejection.EMPTY_RECORD);
        if (recording.rejection == null) recording.texture.capture();
        recording = null;
        capturedBossName = null;
    }

    public static boolean beginRenderer(Render renderer, Entity entity, float partialTicks, byte batchState) {
        if (recording == null) return false;
        if (recording.renderer != null || recording.entity != entity) {
            reject(Rejection.FOREIGN_DRAW);
            return false;
        }
        recording.renderer = renderer;
        if (recording.partialTicks != partialTicks) reject(Rejection.PARTIAL_TICKS_CHANGED);
        if (batchState != BatchEligibility.SAFE) reject(Rejection.UNSAFE_RENDERER);
        return true;
    }

    public static void endRenderer(boolean completed, byte batchState) {
        if (!completed) reject(Rejection.RENDER_FAILED);
        if (batchState != BatchEligibility.SAFE) reject(Rejection.UNSAFE_RENDERER);
    }

    private static boolean inMainView(Entity entity) {
        return mainView == null || entity.isInRangeToRender3d(mainView.xPosition, mainView.yPosition, mainView.zPosition)
            && (entity.ignoreFrustumCheck || mainView.isBoundingBoxInFrustum(entity.boundingBox));
    }

    static boolean isExcluded(Class<?> type) {
        if (type == lastType) return lastExcluded;
        Boolean result = excluded.get(type);
        if (result == null) {
            result = EntityPlayer.class.isAssignableFrom(type);
            for (Class<?> parent = type; !result && parent != null; parent = parent.getSuperclass()) {
                final String[] names = AngelicaConfig.entityShadowReplayExcludedClasses;
                if (names != null) for (String name : names) {
                    if (parent.getName().equals(name)) {
                        result = true;
                        break;
                    }
                }
            }
            excluded.put(type, result);
        }
        lastType = type;
        lastExcluded = result;
        return result;
    }

    public static boolean replay(RenderManager manager, Entity entity, float partialTicks, int renderPass) {
        if (!shadowAvailable || ShadowRenderer.ACTIVE) return false;
        final Record record = records.get(entity);
        if (record == null && !Tracy.ENABLED && !logRejections) return false;
        Rejection reason = renderPass != 0 ? Rejection.RENDER_PASS
            : record != null ? record.rejection
            : isExcluded(entity.getClass()) ? Rejection.EXCLUDED
            : entity.shouldRenderInPass(1) ? Rejection.RENDER_PASS : Rejection.NOT_IN_SHADOW_PASS;
        if (reason == null) {
            if (!mainReady || !ModelPartBatcher.INSTANCE.canReplayEntity()) reason = Rejection.NOT_READY;
            else if (record.renderer != manager.getEntityRenderObject(entity)) reason = Rejection.RENDERER_CHANGED;
            else if (record.partialTicks != partialTicks) reason = Rejection.PARTIAL_TICKS_CHANGED;
            else if (shadowYaw != RenderManager.instance.playerViewY || shadowPitch != RenderManager.instance.playerViewX) reason = Rejection.VIEW_CHANGED;
            else if (!GLStateManager.canApplyExitState(record.exitState)) reason = Rejection.ENTRY_STATE_CHANGED;
            else if (GLSMRasterizer.getPolygonState().getOffsetClamp() != 0) reason = Rejection.UNCAPTURED_STATE;
        }
        if (reason == null) {
            reason = commands.validate(record.start, record.end);
        }
        if (reason != null) {
            rejected[reason.ordinal()]++;
            logRejection(entity.getClass(), record == null || record.renderer == null ? null : record.renderer.getClass(), reason);
            return false;
        }
        replayed++;
        if (Tracy.FINE_ZONES) Tracy.beginZone(Z_REPLAY);
        try {
            if (record.hasBossStatus) BossStatus.setBossStatus((IBossDisplayData) record.entity, record.bossColorModifier);
            ModelPartBatcher.INSTANCE.beginReplay(mainFromShadow);
            commands.replay(record.start, record.end);
            GLStateManager.applyExitState(record.exitState);
            record.texture.apply();
        } finally {
            if (Tracy.FINE_ZONES) Tracy.endZone();
        }
        return true;
    }

    private static void logRejection(Class<?> entity, Class<?> renderer, Rejection reason) {
        if (!logRejections) return;
        Reference2IntOpenHashMap<Class<?>> reasons = loggedRejections.get(entity);
        if (reasons == null) {
            reasons = new Reference2IntOpenHashMap<>();
            loggedRejections.put(entity, reasons);
        }
        final int bit = 1 << reason.ordinal();
        final int logged = reasons.getInt(renderer);
        if ((logged & bit) != 0) return;
        reasons.put(renderer, logged | bit);
        AngelicaMod.LOGGER.info("Entity shadow replay fallback: entity={} capturedRenderer={} reason={}: {}",
            entity.getName(), renderer == null ? "<not captured>" : renderer.getName(), reason.label, reason.description);
    }

    static EntityRenderCommands.Model recordModel(InstanceColumns source, RenderLayer layer, DrawInputs inputs, int entityId,
                                                  boolean cubesInsideTexture, Matrix4fc modelView, Matrix4fc texMatrix) {
        return captureInheritedState(inputs)
            ? commands.model(recording.start, source, layer, inputs, entityId, cubesInsideTexture, modelView, texMatrix) : null;
    }

    static void recordStream(Matrix4fc modelView, int[] vertices, int vertexCount, int groupId, DrawInputs inputs) {
        if (captureInheritedState(inputs)) commands.stream(modelView, vertices, vertexCount, groupId, inputs);
    }

    static EntityRenderCommands.ModelGlint recordArmorGlint(int start, int end, DrawInputs inputs) {
        return captureInheritedState(inputs) && start >= recording.start && end > start && end <= commands.size()
            ? commands.modelGlint(start, end, inputs) : null;
    }

    static EntityRenderCommands.ItemGlint recordItemGlint(Matrix4fc modelView, DrawInputs inputs) {
        return captureInheritedState(inputs) && commands.size() > recording.start ? commands.itemGlint(modelView, inputs) : null;
    }

    private static boolean captureInheritedState(DrawInputs inputs) {
        if (GLSMRasterizer.getPolygonState().getOffsetClamp() != 0) reject(Rejection.UNCAPTURED_STATE);
        if (!isRecording()) return false;
        final int offsetWrites = GLStateManager.getExitPolygonOffsetWrites();
        inputs.inheritedOffsetEnable = (offsetWrites & ExitState.POLYGON_OFFSET_ENABLE_WRITTEN) == 0;
        inputs.inheritedOffsetValues = (offsetWrites & ExitState.POLYGON_OFFSET_VALUES_WRITTEN) == 0;
        inputs.inheritedLighting = !GLStateManager.isExitLightingWritten();
        return true;
    }

    static boolean isRecording() {
        return recording != null && recording.rejection == null;
    }

    static int splitCommands() {
        return isRecording() ? commands.split() : -1;
    }

    public static void recordDeferredOverlay(DeferredEntityOverlay.DeferredEntry overlay) {
        if (isRecording()) commands.overlay(overlay);
    }

    public static void recordBossStatus(IBossDisplayData boss, boolean colorModifier) {
        if (!isRecording()) return;
        if (boss != recording.entity || recording.hasBossStatus) {
            reject(Rejection.UNCAPTURED_STATE);
            return;
        }
        recording.hasBossStatus = true;
        recording.bossColorModifier = colorModifier;
        capturedBossHealthScale = BossStatus.healthScale;
        capturedBossBarTime = BossStatus.statusBarTime;
        capturedBossName = BossStatus.bossName;
        capturedBossColorModifier = BossStatus.hasColorModifier;
    }

    private static boolean bossStatusChanged() {
        return Float.floatToIntBits(capturedBossHealthScale) != Float.floatToIntBits(BossStatus.healthScale)
            || capturedBossBarTime != BossStatus.statusBarTime
            || !Objects.equals(capturedBossName, BossStatus.bossName)
            || capturedBossColorModifier != BossStatus.hasColorModifier;
    }

    private static void reject(Rejection reason) {
        if (recording != null && recording.rejection == null) {
            recording.rejection = reason;
            if (recording.capturingState) {
                GLStateManager.cancelExitCapture();
                recording.capturingState = false;
            }
        }
    }

    public static void rejectForeignDraw() {
        reject(Rejection.FOREIGN_DRAW);
    }

    public static void rejectRenderFailed() {
        reject(Rejection.RENDER_FAILED);
    }

    public static void rejectShadowOnly() {
        reject(Rejection.SHADOW_ONLY_SKIP);
    }

    static void rejectUncapturedState() {
        reject(Rejection.UNCAPTURED_STATE);
    }

    static void rejectPartFallback() {
        reject(Rejection.PART_FALLBACK);
    }

    static void rejectBatcherBail() {
        reject(Rejection.BATCHER_BAIL);
    }

    public static void clear() {
        resetRecords();
        mainView = null;
        commands.release();
        recordPool.release();
        excluded.clear();
        lastType = null;
        loggedRejections.clear();
    }

    enum Rejection {
        EXCLUDED(               "excluded",             "Player or configured excluded class."),
        FOREIGN_DRAW(           "foreignDraw",          "Uncaptured draw or nested entity rendering."),
        SHADOW_ONLY_SKIP(       "shadowOnlySkip",       "Shadow rendering skipped work needed in the main view."),
        RENDER_PASS(            "renderPass",           "Entity requires a render pass outside the supported pass 0."),
        NOT_IN_SHADOW_PASS(     "notInShadowPass",      "No shadow record this frame; shadow range or culling can cause this!"),
        UNSAFE_RENDERER(        "unsafeRenderer",       "Renderer is not marked safe for batching, or no renderer ran."),
        EMPTY_RECORD(           "emptyRecord",          "No replayable rendering commands were captured."),
        UNCAPTURED_STATE(       "uncapturedState",      "Unsupported render state or boss HUD side effects."),
        PART_FALLBACK(          "partFallback",         "A model part used the live rendering path."),
        BATCHER_BAIL(           "batcherBail",          "A draw could not use the model batcher."),
        RENDER_FAILED(          "renderFailed",         "The captured renderer call did not complete successfully."),
        NOT_READY(              "notReady",             "Main-pass batching is inactive or a required instanced shader variant is missing."),
        RENDERER_CHANGED(       "rendererChanged",      "The main renderer differs from the captured renderer."),
        PARTIAL_TICKS_CHANGED(  "partialTicksChanged",  "Animation time differs from the one the shadow pass recorded."),
        VIEW_CHANGED(           "viewChanged",          "Camera angles changed between shadow and main views."),
        EXIT_STATE_UNSUPPORTED( "exitStateUnsupported", "Renderer state includes an unsupported operation, state query, or unbalanced stack."),
        ENTRY_STATE_CHANGED(    "entryStateChanged",    "Main renderer entry state differs from a captured dependency, or a texture was deleted."),
        SHARED_RANGE_CHANGED(   "sharedRangeChanged",   "Captured instance storage was rebuilt or shortened.");

        final long plot;
        final String label, description;

        Rejection(String name, String description) {
            plot = Tracy.plotHandle("entityReplay." + name);
            label = name;
            this.description = description;
        }
    }

    static final class Record {
        final ExitState exitState = new ExitState();
        final ModelPartBatcher.BoundTexture texture = new ModelPartBatcher.BoundTexture();
        int start, end;
        Render renderer;
        Entity entity;
        float partialTicks;
        boolean hasBossStatus, bossColorModifier, capturingState;
        Rejection rejection;

        void clear() {
            renderer = null;
            entity = null;
            texture.location = null;
        }
    }
}

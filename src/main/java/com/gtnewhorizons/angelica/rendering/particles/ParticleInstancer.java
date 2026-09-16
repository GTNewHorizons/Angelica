package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.client.rendering.DeferredDrawBatcher;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.ParticleInstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.ParticleQuadMesh;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.profiling.BailClassCounts;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.OperationArgs;
import com.gtnewhorizons.angelica.rendering.ParticleRunSplitter;
import com.gtnewhorizons.angelica.rendering.tesr.InstanceRing;
import com.gtnewhorizons.angelica.rendering.tesr.MeshBuffer;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;

public final class ParticleInstancer {

    private static final int STRIDE = ParticleInstancedAttribs.STRIDE;
    private static final int INITIAL_BYTES = 256 * STRIDE;

    private static final ParticleCaptureTessellator CAPTURE = new ParticleCaptureTessellator();
    private static final ParticleParams SCRATCH = new ParticleParams();
    private static final ParticleRenderState STATE = new ParticleRenderState();
    private static final ParticleRenderState RESTORE = new ParticleRenderState();
    private static final Matrix4f LAYER_MV = new Matrix4f();
    private static final Object[] ORIGINAL_ARGS = new Object[8];

    private static final ObjectArrayList<Group> GROUP_POOL = new ObjectArrayList<>();
    private static int groupCount;

    private static final class Group {
        final ParticleRenderState state = new ParticleRenderState();
        ByteBuffer list;
        boolean translucent;
    }

    private static boolean layerActive;
    private static boolean layerStarted;
    private static DeferredWorldRenderingPipeline unavailableFor;
    private static DeferredWorldRenderingPipeline deferred;
    private static Tessellator run;

    private static float rotX;
    private static float rotXZ;
    private static float rotZ;
    private static float rotYZ;
    private static float rotXY;

    private static long direct;
    private static long captured;
    private static long spilled;
    private static long undecodable;
    private static long draws;

    private ParticleInstancer() {}

    public static long getDirect() {
        return direct;
    }

    public static long getCaptured() {
        return captured;
    }

    public static long getDraws() {
        return draws;
    }

    public static long getSpilled() {
        return spilled;
    }

    public static long getUndecodable() {
        return undecodable;
    }

    private static void beginLayer() {
        layerActive = false;
        layerStarted = true;
        final SodiumGameOptions options = ClientProxy.options();
        if (options == null || !options.advanced.enableDeferredBatching) return;

        deferred = pipeline();
        if (available()) {
            layerActive = true;
            groupCount = 0;
            LAYER_MV.set(GLStateManager.getModelViewMatrix());
        } else {
            deferred = null;
            DeferredDrawBatcher.enter();
        }
    }

    public static void renderParticle(EntityFX fx, Tessellator runTessellator, float partialTicks, float rotationX, float rotationXZ, float rotationZ, float rotationYZ, float rotationXY, Operation<Void> original) {
        if (!layerStarted) beginLayer();
        if (!layerActive) {
            call(fx, runTessellator, partialTicks, rotationX, rotationXZ, rotationZ, rotationYZ, rotationXY, original);
            return;
        }
        renderActive(fx, runTessellator, partialTicks, rotationX, rotationXZ, rotationZ, rotationYZ, rotationXY, original);
    }

    private static void renderActive(EntityFX fx, Tessellator runTessellator, float partialTicks, float rotationX, float rotationXZ, float rotationZ, float rotationYZ, float rotationXY, Operation<Void> original) {

        run = runTessellator;
        rotX = rotationX;
        rotXZ = rotationXZ;
        rotZ = rotationZ;
        rotYZ = rotationYZ;
        rotXY = rotationXY;

        final ParticleDescriptor descriptor = ParticleDescriptorRegistry.forClass(fx.getClass());
        if (descriptor != ParticleDescriptorRegistry.CAPTURE) {
            STATE.sample();
            SCRATCH.brightness = runTessellator.brightness;
            if (!descriptor.describe(fx, partialTicks, SCRATCH, STATE)) return;

            runTessellator.color = SCRATCH.colorABGR;
            runTessellator.hasColor = true;
            emit(groupFor(STATE, deferred != null && ParticleRunSplitter.currentRunTranslucent()));
            direct++;
            return;
        }

        CAPTURE.begin(runTessellator.brightness, runTessellator, LAYER_MV);
        call(fx, CAPTURE, partialTicks, rotationX, rotationXZ, rotationZ, rotationYZ, rotationXY, original);
        CAPTURE.propagateColor();

        if (CAPTURE.spilled()) {
            spilled++;
            if (Tracy.ENABLED) BailClassCounts.PARTICLE_SPILL.add(fx.getClass());
            return;
        }
        if (CAPTURE.captured() == 0) return;

        if (!ParticleQuadDecoder.decode(CAPTURE.vertices(), CAPTURE.captured(), rotationX, rotationXZ, rotationZ, rotationYZ, rotationXY, SCRATCH)) {
            CAPTURE.replayInto(runTessellator);
            undecodable++;
            return;
        }

        SCRATCH.colorABGR = CAPTURE.capturedColor();
        SCRATCH.brightness = CAPTURE.capturedBrightness();
        emit(groupFor(CAPTURE.state(), deferred != null && ParticleRunSplitter.currentRunTranslucent()));
        captured++;
    }

    private static void emit(Group group) {
        final ByteBuffer list = MeshBuffer.ensureCapacity(group.list, STRIDE, true);
        group.list = list;
        ParticleInstancedAttribs.writeInstance(memAddress0(list) + list.position(), SCRATCH.centerX, SCRATCH.centerY, SCRATCH.centerZ, SCRATCH.half, SCRATCH.u0, SCRATCH.v0, SCRATCH.u1, SCRATCH.v1, SCRATCH.colorABGR, SCRATCH.brightness);
        list.position(list.position() + STRIDE);
    }

    public static void endLayer() {
        layerStarted = false;
        if (!layerActive) {
            if (DeferredDrawBatcher.isActive()) DeferredDrawBatcher.exitAndFlush();
            return;
        }
        layerActive = false;

        if (groupCount > 0) {
            RESTORE.sample();
            for (int i = 0; i < groupCount; i++) {
                final Group group = GROUP_POOL.get(i);
                if (!group.translucent) flush(group);
            }
            for (int i = 0; i < groupCount; i++) {
                final Group group = GROUP_POOL.get(i);
                if (group.translucent) flush(group);
            }
            RESTORE.apply();
            if (deferred != null) {
                GbufferPrograms.setTranslucencyDeclaration(ParticleRunSplitter.currentRunTranslucent());
            }
            ring().postDraw();
            groupCount = 0;
        }

        deferred = null;
        run = null;
    }

    private static Group groupFor(ParticleRenderState state, boolean translucent) {
        for (int i = 0; i < groupCount; i++) {
            final Group g = GROUP_POOL.get(i);
            if (g.translucent == translucent && g.state.matches(state)) return g;
        }
        if (groupCount == GROUP_POOL.size()) GROUP_POOL.add(new Group());
        final Group g = GROUP_POOL.get(groupCount++);
        g.state.set(state);
        g.translucent = translucent;
        if (g.list != null) g.list.clear();
        g.list = MeshBuffer.ensureCapacity(g.list, INITIAL_BYTES, true);
        return g;
    }

    public static void clear() {
        layerActive = false;
        layerStarted = false;
        deferred = null;
        run = null;
        unavailableFor = null;
        groupCount = 0;
        for (int i = 0; i < GROUP_POOL.size(); i++) {
            GROUP_POOL.get(i).list = null;
        }
        GROUP_POOL.clear();
        CAPTURE.release();
        ParticleQuadMesh.delete();
        ParticleDescriptorRegistry.clearCache();
    }

    private static void call(EntityFX fx, Tessellator target, float partialTicks, float rotationX, float rotationXZ, float rotationZ, float rotationYZ, float rotationXY, Operation<Void> original) {
        final Object[] args = ORIGINAL_ARGS;
        args[0] = fx;
        args[1] = target;
        args[2] = OperationArgs.boxed(args[2], partialTicks);
        args[3] = OperationArgs.boxed(args[3], rotationX);
        args[4] = OperationArgs.boxed(args[4], rotationXZ);
        args[5] = OperationArgs.boxed(args[5], rotationZ);
        args[6] = OperationArgs.boxed(args[6], rotationYZ);
        args[7] = OperationArgs.boxed(args[7], rotationXY);
        try {
            original.call(args);
        } finally {
            args[0] = null;
            args[1] = null;
        }
    }

    private static boolean available() {
        if (deferred != null && deferred == unavailableFor) return false;
        if (!ShaderManager.getInstance().isEnabled()) return false;
        return deferred == null || deferred.supportsInstancing(Instancing.PARTICLE);
    }

    private static DeferredWorldRenderingPipeline pipeline() {
        if (!Iris.enabled) return null;
        return Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline pipeline ? pipeline : null;
    }

    private static InstanceRing ring() {
        return TesrBatchRenderer.INSTANCE.instanceRing();
    }

    private static void flush(Group group) {
        final ByteBuffer list = group.list;
        final boolean listTranslucent = group.translucent;
        final int count = list.position() / STRIDE;
        if (count == 0) return;
        group.state.apply();
        list.flip();

        if (deferred != null) {
            GbufferPrograms.setTranslucencyDeclaration(listTranslucent);
            deferred.rebindCurrentPass();
            if (!deferred.hasInstancedVariant(Instancing.PARTICLE)) {
                unavailableFor = deferred;
                drawInstancesCpu(group.state, list, count, listTranslucent);
                list.clear();
                return;
            }
        }

        final long base = ring().upload(list, STRIDE);
        list.clear();

        if (deferred != null) {
            deferred.bindInstancedVariant(Instancing.PARTICLE);
            issueDraw(base, count, false);
            deferred.rebindCurrentPass();
        } else {
            issueDraw(base, count, true);
        }
    }

    private static void issueDraw(long base, int count, boolean ffp) {
        ParticleQuadMesh.update(rotX, rotXZ, rotZ, rotYZ, rotXY);
        final ImmediateExtendedAttribHandler extHandler = GLSMHooks.immediateExtendedHandler;
        if (extHandler != null && extHandler.wantsExtended()) {
            FfpExtendedAttribs.setNeutralCurrentValues();
        }
        GLStateManager.glBindVertexArray(ParticleQuadMesh.vao());
        VAOManager.setCurrentVertexFlags(ParticleQuadMesh.VERTEX_FLAGS);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, ring().bufferId());
        ParticleInstancedAttribs.pointInstanceAttribs(base);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        if (ffp) GLStateManager.ffpInstancing = Instancing.PARTICLE;
        GLStateManager.glDrawArraysInstanced(GL11.GL_QUADS, 0, ParticleQuadMesh.VERTEX_COUNT, count);
        GLStateManager.ffpInstancing = Instancing.NONE;
        GLStateManager.glBindVertexArray(0);
        draws++;
    }

    private static void drawInstancesCpu(ParticleRenderState state, ByteBuffer list, int count, boolean listTranslucent) {
        final Tessellator t = run;
        RESTORE.apply();
        GbufferPrograms.setTranslucencyDeclaration(ParticleRunSplitter.currentRunTranslucent());
        t.draw();
        t.startDrawingQuads();
        GbufferPrograms.setTranslucencyDeclaration(listTranslucent);
        state.apply();
        for (int i = 0; i < count; i++) {
            final int base = i * STRIDE;
            final float cx = list.getFloat(base);
            final float cy = list.getFloat(base + 4);
            final float cz = list.getFloat(base + 8);
            final float half = list.getFloat(base + 12);
            final float u0 = list.getFloat(base + ParticleInstancedAttribs.OFFSET_UV);
            final float v0 = list.getFloat(base + ParticleInstancedAttribs.OFFSET_UV + 4);
            final float u1 = list.getFloat(base + ParticleInstancedAttribs.OFFSET_UV + 8);
            final float v1 = list.getFloat(base + ParticleInstancedAttribs.OFFSET_UV + 12);
            final int color = list.getInt(base + ParticleInstancedAttribs.OFFSET_COLOR);
            final int light = (int) list.getFloat(base + ParticleInstancedAttribs.OFFSET_LIGHTMAP) | ((int) list.getFloat(base + ParticleInstancedAttribs.OFFSET_LIGHTMAP + 4) << 16);

            t.setBrightness(light);
            t.setColorRGBA(color & 255, (color >> 8) & 255, (color >> 16) & 255, (color >>> 24) & 255);
            for (int c = 0; c < ParticleQuadMesh.VERTEX_COUNT; c++) {
                final float a = ParticleQuadMesh.cornerA(c);
                final float b = ParticleQuadMesh.cornerB(c);
                emitCorner(t, cx, cy, cz, half, a, b, a < 0.0f ? u0 : u1, b < 0.0f ? v0 : v1);
            }
        }
        t.draw();
        t.startDrawingQuads();
    }

    private static void emitCorner(Tessellator t, float cx, float cy, float cz, float half, float a, float b, float u, float v) {
        final float ah = a * half;
        final float bh = b * half;
        t.addVertexWithUV(cx + ParticleQuadMesh.offsetX(ah, bh, rotX, rotYZ), cy + ParticleQuadMesh.offsetY(bh, rotXZ), cz + ParticleQuadMesh.offsetZ(ah, bh, rotZ, rotXY), u, v);
    }
}

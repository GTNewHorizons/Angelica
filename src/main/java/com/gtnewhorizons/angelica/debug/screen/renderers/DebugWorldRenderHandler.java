package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizons.angelica.debug.render.DebugDrawScope;
import com.gtnewhorizons.angelica.debug.render.DebugProgram;
import com.gtnewhorizons.angelica.debug.render.NativeScratch;
import com.gtnewhorizons.angelica.debug.screen.DebugRegistry;
import com.gtnewhorizons.angelica.glsm.GLContextState;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL15;

public final class DebugWorldRenderHandler {

    private static final Tracy.ZoneId Z_DEBUG_REBUILD = Tracy.zoneId("debugWorldRebuild", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_DEBUG_UPLOAD = Tracy.zoneId("debugWorldUpload", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_DEBUG_RENDER = Tracy.zoneId("debugWorldRender", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_DEBUG_LABELS = Tracy.zoneId("debugWorldLabels", Tracy.COLOR_CLIENT);

    private static final int SCRATCH_BYTES = 0x4000;

    private final DebugRenderSlots slots;
    private final DebugMesh[] meshes;
    private final DebugLabelBatch labels = new DebugLabelBatch();
    private final NativeScratch scratch = new NativeScratch(SCRATCH_BYTES);
    private final DebugDrawScope scope = new DebugDrawScope();
    private final Matrix4f base = new Matrix4f();
    private final Matrix4f projBase = new Matrix4f();
    private final Matrix4f scratchMatrix = new Matrix4f();

    DebugWorldRenderHandler(DebugRegistry registry) {
        slots = new DebugRenderSlots(registry);
        meshes = new DebugMesh[slots.count];
    }

    DebugRenderSlots slots() {
        return slots;
    }

    void processReleases() {
        for (int i = 0; i < slots.count; i++) {
            if (!slots.isPendingDiscard(i)) continue;
            slots.clearPendingDiscard(i);
            final DebugMesh mesh = meshes[i];
            if (mesh != null) mesh.delete();
        }
        slots.releaseIfIdle();
        if (!slots.isLive()) freeScratch();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote) return;
        if (slots.enabledCount() > 0) event.world.addWorldAccess(slots.changes);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) return;
        if (event.world != Minecraft.getMinecraft().theWorld) return;
        event.world.removeWorldAccess(slots.changes);
        slots.resetWorld();
        processReleases();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!slots.isLive()) return;
        processReleases();
        if (!slots.isLive()) return;
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityLivingBase viewer = mc.renderViewEntity;
        final World world = mc.theWorld;
        if (viewer == null || world == null) return;

        final float partialTicks = event.partialTicks;
        final double camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        final double camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        final double camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;
        final long now = System.nanoTime();

        final GLContextState glCtx = GLStateManager.ctx();
        final ScanState scan = slots.scan;
        final int slot;
        scan.begin(world, viewer);
        if (Tracy.ENABLED) Tracy.beginZone(Z_DEBUG_REBUILD);
        try {
            slot = slots.update(now, scan.viewerX(), scan.viewerY(), scan.viewerZ());
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
            scan.end();
        }
        if (slot >= 0 && slots.takeUpload(slot)) {
            if (Tracy.ENABLED) Tracy.beginZone(Z_DEBUG_UPLOAD);
            try {
                upload(glCtx, slot);
            } finally {
                if (Tracy.ENABLED) Tracy.endZone();
            }
        }

        if (Tracy.ENABLED) Tracy.beginZone(Z_DEBUG_RENDER);
        try {
            render(mc, glCtx, camX, camY, camZ);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private void upload(GLContextState glCtx, int slot) {
        final int vao = glCtx.boundVAO;
        final int vbo = glCtx.boundVBO;
        try {
            DebugMesh mesh = meshes[slot];
            if (mesh == null) {
                mesh = new DebugMesh();
                meshes[slot] = mesh;
            }
            mesh.upload(slots.build, scratch);
        } catch (RuntimeException | LinkageError e) {
            slots.fail(slot, e);
        } finally {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        }
    }

    private int nextDrawable(int from) {
        final int n = slots.count;
        for (int i = from; i < n; i++) {
            if (slots.isDrawable(i) && meshes[i] != null) return i;
        }
        return n;
    }

    private void render(Minecraft mc, GLContextState glCtx, double camX, double camY, double camZ) {
        final int n = slots.count;
        if (nextDrawable(0) == n) return;

        base.set(GLStateManager.getModelViewMatrix());
        projBase.set(GLStateManager.getProjectionMatrix()).mul(base);
        scope.beginWorld();
        try {
            for (int i = nextDrawable(0); i < n; i = nextDrawable(i + 1)) {
                meshes[i].prepare(projBase, camX, camY, camZ);
            }

            drawPass(false);

            boolean anyLabels = false;
            for (int i = nextDrawable(0); i < n; i = nextDrawable(i + 1)) {
                anyLabels |= meshes[i].labels.count > 0;
            }
            if (anyLabels) {
                if (Tracy.ENABLED) Tracy.beginZone(Z_DEBUG_LABELS);
                try {
                    drawLabels(mc, glCtx, camX, camY, camZ);
                } finally {
                    if (Tracy.ENABLED) Tracy.endZone();
                }
            }

            GLStateManager.disableDepthTest();
            drawPass(true);
        } finally {
            scope.end();
        }
    }

    private void drawPass(boolean onTop) {
        final int n = slots.count;
        final DebugProgram color = useProgram(false, onTop);
        if (color != null) {
            for (int i = nextDrawable(0); i < n; i = nextDrawable(i + 1)) {
                try {
                    meshes[i].drawPlain(onTop, color);
                } catch (RuntimeException | LinkageError e) {
                    slots.fail(i, e);
                }
            }
        }
        final DebugProgram lines = useProgram(true, onTop);
        if (lines != null) {
            for (int i = nextDrawable(0); i < n; i = nextDrawable(i + 1)) {
                try {
                    meshes[i].drawWide(onTop, lines);
                } catch (RuntimeException | LinkageError e) {
                    slots.fail(i, e);
                }
            }
        }
    }

    private DebugProgram useProgram(boolean wide, boolean onTop) {
        final int n = slots.count;
        int first = nextDrawable(0);
        while (first < n && !meshes[first].has(wide, onTop)) first = nextDrawable(first + 1);
        if (first == n) return null;
        try {
            return wide ? DebugMesh.useLines() : DebugMesh.useColor();
        } catch (RuntimeException | LinkageError e) {
            for (int i = first; i < n; i = nextDrawable(i + 1)) {
                if (meshes[i].has(wide, onTop)) slots.fail(i, e);
            }
            return null;
        }
    }

    private void drawLabels(Minecraft mc, GLContextState glCtx, double camX, double camY, double camZ) {
        final int n = slots.count;
        try {
            labels.begin(mc.fontRenderer, GLStateManager.getProjectionMatrix(), glCtx.viewportState.height);
            for (int i = nextDrawable(0); i < n; i = nextDrawable(i + 1)) {
                final DebugMesh mesh = meshes[i];
                if (mesh.labels.count == 0) continue;
                try {
                    mesh.modelView(base, camX, camY, camZ, scratchMatrix);
                    labels.add(mesh.labels, scratchMatrix);
                } catch (RuntimeException | LinkageError e) {
                    slots.fail(i, e);
                }
            }
            labels.end();
        } catch (RuntimeException | LinkageError e) {
            failLabelled(e);
        }
    }

    private void failLabelled(Throwable cause) {
        final int n = slots.count;
        for (int i = nextDrawable(0); i < n; i = nextDrawable(i + 1)) {
            if (meshes[i].labels.count > 0) slots.fail(i, cause);
        }
    }

    private void freeScratch() {
        labels.free();
        scratch.free();
    }
}

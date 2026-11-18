package com.gtnewhorizons.angelica.debug;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.debug.render.DebugDrawScope;
import com.gtnewhorizons.angelica.debug.render.DebugGeometry;
import com.gtnewhorizons.angelica.debug.render.DebugPrograms;
import com.gtnewhorizons.angelica.debug.render.DebugProgram;
import com.gtnewhorizons.angelica.debug.render.NativeScratch;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import com.gtnewhorizons.angelica.glsm.streaming.LineQuads;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

public class F3Direction {
    private static final float AXIS_LENGTH = 10.0F;
    private static final float LINE_WIDTH = 2.0F;
    private static final int SEGMENTS = 3;
    private static final int LINE_VERTICES = SEGMENTS * 2;
    private static final int QUAD_VERTICES = SEGMENTS * 6;
    private static final int FLAGS = VertexFlags.COLOR_BIT;
    private static final VertexFormat FORMAT = DefaultVertexFormat.ALL_FORMATS[FLAGS];
    private static final VertexFormat QUAD_FORMAT = LineQuads.quadFormat(FORMAT);
    private static final int STRIDE = FORMAT.getVertexSize();
    private static final int COLOR_OFFSET = DebugGeometry.offsetOf(FORMAT, VertexFormatElement.Usage.COLOR);

    private static final DebugDrawScope SCOPE = new DebugDrawScope();
    private static final Matrix4f MVP = new Matrix4f();
    private static int vao;

    public static void renderWorldDirectionsEvent(Minecraft mc, RenderGameOverlayEvent.Pre event) {
        if (mc.gameSettings.thirdPersonView == 0) {
            final int width = event.resolution.getScaledWidth();
            final int height = event.resolution.getScaledHeight();

            GLSMMatrices.glPushMatrix();

            GLSMMatrices.glTranslatef((float) (width / 2), (float) (height / 2), -90);

            final Entity entity = mc.renderViewEntity;
            GLSMMatrices.glRotatef(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * event.partialTicks, -1.0F, 0.0F, 0.0F);
            GLSMMatrices.glRotatef(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * event.partialTicks, 0.0F, 1.0F, 0.0F);

            GLSMMatrices.glScalef(-1.0F, -1.0F, -1.0F);

            renderWorldDirections();

            GLSMMatrices.glPopMatrix();
        }
    }

    public static void renderWorldDirections() {
        GLSMMatrices.getProjectionMatrix().mul(GLSMMatrices.getModelViewMatrix(), MVP);
        final ViewportState viewport = GLSMRasterizer.getViewportState();

        SCOPE.beginHud();
        try {
            if (vao == 0) build();
            final DebugProgram lines = DebugPrograms.lines();
            lines.use();
            lines.mvp(MVP);
            lines.lineWidth(LINE_WIDTH);
            lines.viewport(viewport.width, viewport.height);
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, QUAD_VERTICES);
        } finally {
            SCOPE.end();
        }
    }

    private static void build() {
        final int quadBytes = QUAD_VERTICES * QUAD_FORMAT.getVertexSize();
        final NativeScratch source = new NativeScratch(LINE_VERTICES * STRIDE);
        final NativeScratch expanded = new NativeScratch(quadBytes);
        try {
            source.ensure(LINE_VERTICES * STRIDE);
            expanded.ensure(quadBytes);
            long p = source.address();
            p = putAxis(p, AXIS_LENGTH, 0.0F, 0.0F, 0xFFFF0000);
            p = putAxis(p, 0.0F, 0.0F, AXIS_LENGTH, 0xFF0000FF);
            putAxis(p, 0.0F, AXIS_LENGTH, 0.0F, 0xFF00FF00);
            LineQuads.expandWithOtherEnd(source.address(), FORMAT, GL11.GL_LINES, LINE_VERTICES, expanded.address());

            final int vbo = GLSMBuffers.glGenBuffers();
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, expanded.window(quadBytes), GL15.GL_STATIC_DRAW);
            vao = DebugGeometry.createVao(vbo, QUAD_FORMAT);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        } finally {
            source.free();
            expanded.free();
        }
    }

    private static long putAxis(long address, float x, float y, float z, int argb) {
        long p = address;
        DebugGeometry.putPosition(p, 0.0F, 0.0F, 0.0F);
        DebugGeometry.putColor(p + COLOR_OFFSET, argb);
        p += STRIDE;
        DebugGeometry.putPosition(p, x, y, z);
        DebugGeometry.putColor(p + COLOR_OFFSET, argb);
        return p + STRIDE;
    }
}

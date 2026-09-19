package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.PendingProgramSelection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags.COLOR_BIT;
import static com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags.NORMAL_BIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class InstancedFFPDrawGLTest {

    private static final int TEMPLATE_STRIDE = 32;
    private static final int INSTANCE_STRIDE = InstancedAttribs.STRIDE;

    private static final int PARITY_SIZE = 256;
    private static final int INTEGER_WIDTH = 300;
    private static final int INTEGER_HEIGHT = 100;

    private static final List<String> log = new ArrayList<>();

    private static final PendingProgramSelection LOGGING_SELECTION = () -> log.add("resolve");

    private static final class LoggingBlendHandler implements DeferredBlendHandler {
        @Override
        public boolean isBlendLocked() {
            return false;
        }

        @Override
        public boolean isOverrideHeld() {
            return false;
        }

        @Override
        public void deferBlendModeToggle(boolean enabled) {}

        @Override
        public void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {}

        @Override
        public void flushDeferredBlend() {
            log.add("flush");
        }
    }

    private static boolean capturing;

    private int vao, templateVbo, instanceVbo;

    @BeforeAll
    static void registerForeignDrawEndListener() {
        capturing = true;
        GLSMHooks.FOREIGN_DRAW_END.addListener(event -> {
            if (capturing) log.add("listener");
        });
    }

    @AfterAll
    static void stopCapturing() {
        capturing = false;
    }

    @AfterEach
    void cleanup() {
        GLSMHooks.pendingProgramSelection = null;
        GLSMHooks.blendHandler = null;
        log.clear();
        FfpFixture.IntegerInstances.delete();
        FfpFixture.resetFfpState();
        CubeParityFixture.disableDirectionalLight();
        CubeParityFixture.deleteResources();
        ParticleParityFixture.deleteResources();
        if (templateVbo != 0) { GLStateManager.glDeleteBuffers(templateVbo); templateVbo = 0; }
        if (instanceVbo != 0) { GLStateManager.glDeleteBuffers(instanceVbo); instanceVbo = 0; }
        if (vao != 0) { GLStateManager.glDeleteVertexArrays(vao); vao = 0; }
    }

    @Test
    void drawArraysResolvesBeforeFlushDeferredBlend() {
        GLSMHooks.pendingProgramSelection = LOGGING_SELECTION;
        GLSMHooks.blendHandler = new LoggingBlendHandler();

        GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 0);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

        assertEquals(List.of("resolve", "flush"), log);
    }

    @Test
    void resolveIsSuppressedWhileDisplayListIsRecording() {
        GLSMHooks.pendingProgramSelection = LOGGING_SELECTION;
        final int list = GLStateManager.glGenLists(1);

        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLSMHooks.resolvePendingProgram();
        assertTrue(log.isEmpty());
        GLStateManager.glEndList();

        GLSMHooks.resolvePendingProgram();
        assertEquals(List.of("resolve"), log);
    }

    @Test
    void nestedForeignDrawsResolveOnceAtOutermostEndBeforeListeners() {
        GLSMHooks.pendingProgramSelection = LOGGING_SELECTION;

        GLStateManager.beginForeignDraw();
        GLStateManager.beginForeignDraw();
        GLStateManager.endForeignDraw();
        assertTrue(log.isEmpty());

        GLStateManager.endForeignDraw();
        assertEquals(List.of("resolve", "listener"), log);
    }

    @Test
    void unitCubeMatchesBakedQuadsWithLightmap() {
        assertCubeParity(true, false);
    }

    @Test
    void unitCubeMatchesBakedQuadsWhenLit() {
        assertCubeParity(false, true);
    }

    private void assertCubeParity(boolean lightmap, boolean lit) {
        GLStateManager.glViewport(0, 0, PARITY_SIZE, PARITY_SIZE);
        CubeParityFixture.setupScene(lightmap, lit);

        final ShaderManager sm = ShaderManager.getInstance();
        sm.enable();
        sm.activate();

        final CubeParityFixture.Spec[] specs = CubeParityFixture.specs();

        FfpFixture.clear();
        CubeParityFixture.drawReference(specs, lightmap);
        final int[] reference = FfpFixture.readRegion(PARITY_SIZE);

        FfpFixture.clear();
        CubeParityFixture.drawInstanced(specs);
        final int[] instanced = FfpFixture.readRegion(PARITY_SIZE);

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "parity draws must not raise a GL error");

        CubeParityFixture.assertPixelParity(reference, instanced, PARITY_SIZE, 0xFF000000, pixel -> "0x" + Integer.toHexString(pixel));
    }

    @Test
    void particleUnit0TextureMatrixMatchesReference() {
        final ParticleParityFixture.Rotation rot = ParticleParityFixture.Rotation.of(35f, -12f, false);
        GLStateManager.glViewport(0, 0, PARITY_SIZE, PARITY_SIZE);
        ParticleParityFixture.setupScene(true);

        final ShaderManager sm = ShaderManager.getInstance();
        sm.enable();
        sm.activate();

        final ParticleParityFixture.Particle[] particles = ParticleParityFixture.particles();

        FfpFixture.clear();
        ParticleParityFixture.drawReference(particles, rot.x(), rot.xz(), rot.z(), rot.yz(), rot.xy());
        final int[] reference = FfpFixture.readRegion(PARITY_SIZE);

        FfpFixture.clear();
        ParticleParityFixture.drawInstanced(particles, rot.x(), rot.xz(), rot.z(), rot.yz(), rot.xy());
        final int[] instanced = FfpFixture.readRegion(PARITY_SIZE);

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "parity draws must not raise a GL error");
        ParticleParityFixture.assertPixelParity(reference, instanced, PARITY_SIZE, 0xFF000000, pixel -> "0x" + Integer.toHexString(pixel));
    }

    @Test
    void entityAttribSelectsColorPerInstance() {
        GLStateManager.glViewport(0, 0, INTEGER_WIDTH, INTEGER_HEIGHT);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();

        FfpFixture.IntegerInstances.build(0.66f);

        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);

        FfpFixture.IntegerInstances.draw();

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "instanced ivec4 draw must not raise a GL error");

        assertPixel(51, 50, 255, 0, 0, "entity -1 must render red");
        assertPixel(150, 50, 0, 255, 0, "entity 7 must render green");
        assertPixel(249, 50, 0, 0, 255, "entity 42 must render blue");
    }

    private static void assertPixel(int x, int y, int r, int g, int b, String label) {
        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        final int gotR = pixel.get(0) & 0xFF;
        final int gotG = pixel.get(1) & 0xFF;
        final int gotB = pixel.get(2) & 0xFF;
        assertEquals(r, gotR, () -> label + ": red channel, got (" + gotR + "," + gotG + "," + gotB + ")");
        assertEquals(g, gotG, () -> label + ": green channel, got (" + gotR + "," + gotG + "," + gotB + ")");
        assertEquals(b, gotB, () -> label + ": blue channel, got (" + gotR + "," + gotG + "," + gotB + ")");
    }

    @Test
    void instancedSourceUsesInstanceAttribs() {
        GLStateManager.ffpInstancing = Instancing.TEMPLATE;
        final VertexKey key = VertexKey.fromState(true, true, false, false, 0b10);
        GLStateManager.ffpInstancing = Instancing.NONE;

        final String source = VertexShaderGenerator.generate(key);
        assertTrue(source.contains("a_InstRow0"), "instance matrix attribs declared");
        assertTrue(source.contains("mat4 instMV"), "instance matrix used as modelview");
        assertTrue(source.contains("a_InstColor"), "instance color multiplier used");
        assertTrue(source.contains("a_InstLightmap"), "instance lightmap coord used");
        assertFalse(source.contains("u_MVPMatrix * pos4"), "uniform MVP transform unused in instanced variant");
    }

    @Test
    void twoInstancesDrawWithDistinctTransformsAndColors() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, 800, 600);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();

        buildTemplate();
        uploadInstances(0xFF0000FF, 0, 0xFF00FF00, 0);

        final ShaderManager sm = ShaderManager.getInstance();
        sm.enable();
        sm.activate();

        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);

        drawInstances();

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "instanced draw must not raise a GL error");

        final int[] left = FfpFixture.readPixel(200, 300);
        final int[] right = FfpFixture.readPixel(600, 300);
        assertTrue(left[0] > 200 && left[1] < 50, "left instance red, got " + left[0] + "," + left[1] + "," + left[2]);
        assertTrue(right[1] > 200 && right[0] < 50, "right instance green, got " + right[0] + "," + right[1] + "," + right[2]);
    }

    @Test
    void instancedAlphaTestDiscardsLowAlphaInstance() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, 800, 600);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();

        buildTemplate();
        uploadInstances(0xFF0000FF, 0, 0x0D0000FF, 0);

        final ShaderManager sm = ShaderManager.getInstance();
        sm.enable();
        sm.activate();
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);

        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);

        drawInstances();
        GLStateManager.disableAlphaTest();

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        final int[] left = FfpFixture.readPixel(200, 300);
        final int[] right = FfpFixture.readPixel(600, 300);
        assertTrue(left[0] > 200, "opaque instance drawn, got " + left[0]);
        assertTrue(right[0] < 20 && right[1] < 20, "alpha 0.05 instance discarded, got " + right[0] + "," + right[1]);
    }

    @Test
    void equalDepthOverlayBlendsOnlyOverBase() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, 800, 600);
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.disableCull();

        buildTemplate();
        uploadInstances(0xFF0000FF, 0, 0xFF00FF00, 0);

        final ShaderManager sm = ShaderManager.getInstance();
        sm.enable();
        sm.activate();

        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        drawInstances();

        GLStateManager.glDepthFunc(GL11.GL_EQUAL);
        GLStateManager.enableBlend();
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glColor4f(1f, 1f, 1f, 1f);
        uploadInstances(0x800000FF, 0, 0x8000FF00, 0);
        drawInstances();

        GLStateManager.disableBlend();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.disableDepthTest();

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        final int[] left = FfpFixture.readPixel(200, 300);
        final int[] background = FfpFixture.readPixel(400, 550);
        assertTrue(left[0] > 200, "overlay blended over base, got " + left[0]);
        assertTrue(background[0] < 20 && background[1] < 20, "overlay must not draw off-base, got " + background[0]);
    }

    @Test
    void perInstanceOverlayMixesOnlyTintedInstances() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, 800, 600);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();

        buildTemplate();
        uploadInstances(0xFFFFFFFF, 0, 0xFFFFFFFF, 0xFF0000FF);

        final ShaderManager sm = ShaderManager.getInstance();
        sm.enable();
        sm.activate();

        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GLStateManager.setOverlayColor(0f, 1f, 0f, 1f);
        drawInstances();
        GLStateManager.setOverlayColor(0f, 0f, 0f, 0f);

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "instanced overlay draw must not raise a GL error");

        final int[] left = FfpFixture.readPixel(200, 300);
        final int[] right = FfpFixture.readPixel(600, 300);
        assertTrue(left[0] > 200 && left[1] > 200 && left[2] > 200, "left instance overlay alpha 0 keeps its base color, got " + left[0] + "," + left[1] + "," + left[2]);
        assertTrue(right[0] > 200 && right[1] < 50 && right[2] < 50, "right instance is mixed to its own opaque red overlay, got " + right[0] + "," + right[1] + "," + right[2]);
    }

    private void uploadInstances(int leftColor, int leftOverlay, int rightColor, int rightOverlay) {
        final ByteBuffer instances = BufferUtils.createByteBuffer(2 * INSTANCE_STRIDE);
        putInstance(instances, -0.5f, leftColor, leftOverlay);
        putInstance(instances, 0.5f, rightColor, rightOverlay);
        instances.flip();

        final boolean first = instanceVbo == 0;
        if (first) instanceVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STATIC_DRAW);
        if (first) {
            pointAndEnableInstanceAttribs();
        }
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void buildTemplate() {
        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);

        final ByteBuffer template = BufferUtils.createByteBuffer(3 * TEMPLATE_STRIDE);
        putVertex(template, -0.4f, -0.4f);
        putVertex(template, 0.4f, -0.4f);
        putVertex(template, 0.0f, 0.4f);
        template.flip();

        templateVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, templateVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, template, GL15.GL_STATIC_DRAW);
        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, TEMPLATE_STRIDE, 0L);
        GLStateManager.glEnableVertexAttribArray(1);
        GLStateManager.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, TEMPLATE_STRIDE, 20L);
        GLStateManager.glEnableVertexAttribArray(4);
        GLStateManager.glVertexAttribPointer(4, 3, GL11.GL_BYTE, true, TEMPLATE_STRIDE, 24L);
        VAOManager.setCurrentVertexFlags(COLOR_BIT | NORMAL_BIT);
    }

    private void putVertex(ByteBuffer buf, float x, float y) {
        final int base = buf.position();
        buf.putFloat(base, x).putFloat(base + 4, y).putFloat(base + 8, 0f);
        buf.putInt(base + 20, 0xFFFFFFFF);
        buf.putInt(base + 24, 0x00007F00);
        buf.position(base + TEMPLATE_STRIDE);
    }

    private static void pointAndEnableInstanceAttribs() {
        InstancedAttribs.enableHeadArrays();
        InstancedAttribs.pointTemplate(0L);
    }

    private static void putInstance(ByteBuffer buf, float translateX, int colorABGR, int overlayABGR) {
        final int base = buf.position();
        final long ptr = memAddress0(buf) + base;
        InstancedAttribs.writeHead(ptr, FfpFixture.translationMatrix(translateX), 0, colorABGR, overlayABGR, 0L);
        memPutFloat(ptr + InstancedAttribs.OFFSET_LIGHTMAP, 240f);
        memPutFloat(ptr + InstancedAttribs.OFFSET_LIGHTMAP + 4, 240f);
        buf.position(base + INSTANCE_STRIDE);
    }

    private static void drawInstances() {
        GLStateManager.ffpInstancing = Instancing.TEMPLATE;
        GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 3, 2);
        GLStateManager.ffpInstancing = Instancing.NONE;
    }
}

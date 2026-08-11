package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@GLCoreTest
class FfpUniformBlockDedupTest {

    private static int boundRangeStart() {
        return GL30.glGetInteger(GL31.GL_UNIFORM_BUFFER_START, FFPUniformBlock.BINDING_POINT);
    }

    private static FloatBuffer floats(float... values) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(values.length);
        buf.put(values).flip();
        return buf;
    }

    private static DoubleBuffer doubles(double... values) {
        final DoubleBuffer buf = BufferUtils.createDoubleBuffer(values.length);
        buf.put(values).flip();
        return buf;
    }

    private static void resetState() {
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glLineWidth(1.0f);
        GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
        GLStateManager.glNormal3f(0.0f, 0.0f, 1.0f);
        GLStateManager.glTexCoord2f(0.0f, 0.0f);
        GLSMConfig.lastBrightnessX = 0.0f;
        GLSMConfig.lastBrightnessY = 0.0f;
        GLStateManager.glFog(GL11.GL_FOG_COLOR, floats(0.0f, 0.0f, 0.0f, 1.0f));
        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 1.0f);
        GLStateManager.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, floats(0.0f, 0.0f, 0.0f, 0.0f));
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floats(1.0f, 1.0f, 1.0f, 1.0f));
        GLStateManager.glMaterialf(GL11.GL_FRONT, GL11.GL_SHININESS, 0.0f);
        GLStateManager.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, floats(1.0f, 0.0f, 0.0f, 0.0f));
        GLStateManager.glClipPlane(GL11.GL_CLIP_PLANE0, doubles(0.0, 0.0, 0.0, 0.0));
        GLStateManager.glLineStipple(1, (short) 0xFFFF);
        GLStateManager.glViewport(0, 0, 854, 480);
    }

    @Test
    void identicalContentSkipsTheWriteAndTheRebind() {
        resetState();
        final Uniforms uniforms = new Uniforms();
        try {
            uniforms.upload();
            assertEquals(1, uniforms.blockWrites, "first upload always writes");
            final int firstOffset = boundRangeStart();

            GLStateManager.glLoadIdentity();
            uniforms.upload();
            assertEquals(1, uniforms.blockWrites, "a dirty generation with unchanged bytes must not write");
            assertEquals(1, uniforms.blockSkips, "and must be counted as a skip");
            assertEquals(firstOffset, boundRangeStart(), "the skipped write must not advance the ring or rebind");

            GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);
            uniforms.upload();
            assertEquals(2, uniforms.blockWrites, "a changed matrix must write");
            assertEquals(1, uniforms.blockSkips, "a write is not a skip");
            assertNotEquals(firstOffset, boundRangeStart(), "a write takes a fresh ring range and rebinds");
        } finally {
            uniforms.destroy();
            resetState();
        }
    }

    @Test
    void anUnboundInstanceWritesEvenWhenTheContentMatches() {
        resetState();
        final Uniforms first = new Uniforms();
        final Uniforms second = new Uniforms();
        try {
            first.upload();
            first.upload();
            assertEquals(1, first.blockWrites);

            second.upload();
            assertEquals(1, second.blockWrites, "a never-bound instance must write regardless of content");
        } finally {
            first.destroy();
            second.destroy();
            resetState();
        }
    }

    @TestFactory
    List<DynamicTest> everyStagingCategoryForcesAWrite() {
        final List<DynamicTest> tests = new ArrayList<>();
        addCategory(tests, "modelViewMatrix", () -> GLStateManager.glTranslatef(0.0f, 3.0f, 0.0f));
        addCategory(tests, "projectionMatrix", () -> {
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glTranslatef(0.0f, 0.0f, 5.0f);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        });
        addCategory(tests, "normalMatrix", () -> GLStateManager.glRotatef(35.0f, 0.0f, 1.0f, 0.0f));
        addCategory(tests, "textureMatrix", () -> {
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            GLStateManager.glTranslatef(0.5f, 0.25f, 0.0f);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        });
        addCategory(tests, "lighting", () -> GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floats(0.3f, 0.6f, 0.9f, 1.0f)));
        addCategory(tests, "materialShininess", () -> GLStateManager.glMaterialf(GL11.GL_FRONT, GL11.GL_SHININESS, 42.0f));
        addCategory(tests, "currentColor", () -> GLStateManager.glColor4f(0.25f, 0.5f, 0.75f, 1.0f));
        addCategory(tests, "currentNormal", () -> GLStateManager.glNormal3f(0.0f, 1.0f, 0.0f));
        addCategory(tests, "currentTexCoord", () -> GLStateManager.glTexCoord2f(0.125f, 0.375f));
        addCategory(tests, "lightmapCoord", () -> GLSMConfig.lastBrightnessX = 96.0f);
        addCategory(tests, "texGenPlane", () -> GLStateManager.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, floats(0.5f, 0.0f, 0.0f, 0.25f)));
        addCategory(tests, "clipPlane", () -> GLStateManager.glClipPlane(GL11.GL_CLIP_PLANE0, doubles(0.0, 1.0, 0.0, 7.0)));
        addCategory(tests, "alphaRef", () -> GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.35f));
        addCategory(tests, "fogColor", () -> GLStateManager.glFog(GL11.GL_FOG_COLOR, floats(0.1f, 0.2f, 0.3f, 0.4f)));
        addCategory(tests, "fogDensity", () -> GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.75f));
        addCategory(tests, "texEnvColor", () -> GLStateManager.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, floats(0.9f, 0.8f, 0.7f, 0.6f)));
        addCategory(tests, "lineWidth", () -> GLStateManager.glLineWidth(3.0f));
        addCategory(tests, "lineStipple", () -> GLStateManager.glLineStipple(2, (short) 0x00FF));
        addCategory(tests, "viewport", () -> GLStateManager.glViewport(0, 0, 640, 480));
        return tests;
    }

    private static void addCategory(List<DynamicTest> tests, String name, Runnable mutate) {
        tests.add(DynamicTest.dynamicTest(name, () -> {
            resetState();
            final Uniforms uniforms = new Uniforms();
            try {
                uniforms.upload();
                uniforms.upload();
                final int writesBefore = uniforms.blockWrites;
                final int settledOffset = boundRangeStart();

                mutate.run();
                uniforms.upload();
                assertEquals(writesBefore + 1, uniforms.blockWrites, name + ": a changed member must write");
                assertNotEquals(settledOffset, boundRangeStart(), name + ": a write must rebind to a fresh range");

                final int skipsBefore = uniforms.blockSkips;
                final int writtenOffset = boundRangeStart();
                uniforms.upload();
                assertEquals(writesBefore + 1, uniforms.blockWrites, name + ": re-uploading unchanged bytes must not write");
                assertEquals(writtenOffset, boundRangeStart(), name + ": and must not rebind");
                if (uniforms.blockSkips != skipsBefore) {
                    assertEquals(skipsBefore + 1, uniforms.blockSkips, name + ": at most one skip per upload");
                }
            } finally {
                uniforms.destroy();
                resetState();
            }
        }));
    }
}

package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatch;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.SharedDrawable;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_SharedDrawableContext_GLTest {

    private SharedDrawable sharedDrawable;
    private int listVbo;
    private int listEbo;
    private int indexedList;
    private int sharedContextVao;

    @AfterEach
    void cleanup() {
        if (indexedList != 0) GLSMDraws.glDeleteLists(indexedList, 1);
        if (listVbo != 0) GLSMBuffers.glDeleteBuffers(listVbo);
        if (listEbo != 0) GLSMBuffers.glDeleteBuffers(listEbo);
        if (sharedDrawable != null) {
            DrawableContexts.destroy(sharedDrawable);
            sharedDrawable = null;
        }
    }

    @Test
    void sharedDrawableBindsItsOwnContext() throws Exception {
        final GLContextState primary = GLStateManager.ctx();
        sharedDrawable = new SharedDrawable(Display.getDrawable());

        TestThreads.run("SharedDrawableContext-Bind-Thread", () -> {
            GLStateManager.makeCurrent(sharedDrawable);
            try {
                final GLContextState workerCtx = GLStateManager.ctx();
                assertNotSame(primary, workerCtx);
                assertNotEquals(0, workerCtx.defaultVAO);
                assertEquals(workerCtx.defaultVAO, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));

                final IntBuffer viewport = BufferUtils.createIntBuffer(16);
                GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
                assertEquals(0, viewport.get(0));
                assertEquals(0, viewport.get(1));
                assertEquals(Display.getWidth(), viewport.get(2));
                assertEquals(Display.getHeight(), viewport.get(3));
            } finally {
                GLStateManager.releaseContext(sharedDrawable);
            }
            assertSame(primary, GLStateManager.ctx());
        });
    }

    @Test
    void failedContextSwitchRestoresPreviousBinding() throws Exception {
        final GLContextState primary = GLStateManager.ctx();
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final CountDownLatch acquired = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final TestThreads.Worker worker = DrawableContexts.start(sharedDrawable, "SharedDrawableContext-Owner-Thread", () -> {
            acquired.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timed out");
        });
        try {
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> GLStateManager.makeCurrent(sharedDrawable));
            assertSame(primary, GLStateManager.ctx());
            assertEquals(primary.defaultVAO, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        } finally {
            release.countDown();
            worker.join();
        }
        GLStateManager.makeCurrent(sharedDrawable);
        assertNotSame(primary, GLStateManager.ctx());
        GLStateManager.releaseContext(sharedDrawable);
        GLStateManager.makeCurrent(Display.getDrawable());
        assertSame(primary, GLStateManager.ctx());
    }

    @Test
    void foreignThreadCannotFinishOrRecordIntoActiveList() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final int existing = GLSMDraws.glGenLists(1);
        final int active = GLSMDraws.glGenLists(1);
        final int rejected = GLSMDraws.glGenLists(1);
        GLSMDraws.glNewList(existing, GL11.GL_COMPILE);
        GLSMBlend.enableBlend();
        GLSMDraws.glEndList();
        GLSMBlend.disableBlend();

        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(1);
        final TestThreads.Worker worker = DrawableContexts.start(sharedDrawable, "SharedDrawableContext-List-Thread", () -> {
            GLSMDraws.glNewList(active, GL11.GL_COMPILE);
            started.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("finish timed out");
            GLSMDraws.glEndList();
        });
        try {
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> GLSMDraws.glNewList(rejected, GL11.GL_COMPILE));
                assertThrows(IllegalStateException.class, GLSMDraws::glEndList);
                assertEquals(0, GLSMDraws.getListMode());
                DisplayListManager.abortIfLeaked();
                assertThrows(IllegalStateException.class, DisplayListManager::abortCompilation);
                GLSMDraws.glCallList(existing);
                assertTrue(GLSMEnable.glIsEnabled(GL11.GL_BLEND));
                assertEquals(0, GLSMDraws.getListMode());
            } finally {
                finish.countDown();
                worker.join();
            }
            assertTrue(DisplayListManager.displayListExists(active));
            GLSMDraws.glCallList(active);
        } finally {
            GLSMDraws.glDeleteLists(existing, 1);
            GLSMDraws.glDeleteLists(active, 1);
            GLSMDraws.glDeleteLists(rejected, 1);
            GLSMBlend.disableBlend();
        }
    }

    @Test
    void simultaneousListStartsClaimOneOwner() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final int first = GLSMDraws.glGenLists(1);
        final int second = GLSMDraws.glGenLists(1);
        final CyclicBarrier start = new CyclicBarrier(2);
        final CountDownLatch attempted = new CountDownLatch(2);
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        final TestThreads.Worker worker = DrawableContexts.start(sharedDrawable, "SharedDrawableContext-List-Race-Thread", () -> {
            start.await();
            try {
                GLSMDraws.glNewList(second, GL11.GL_COMPILE);
            } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
                attempted.countDown();
                return;
            }
            attempted.countDown();
            if (!attempted.await(5, TimeUnit.SECONDS)) throw new AssertionError("attempts timed out");
            GLSMDraws.glEndList();
            completed.incrementAndGet();
        });
        try {
            start.await();
            try {
                GLSMDraws.glNewList(first, GL11.GL_COMPILE);
            } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
                attempted.countDown();
            }
            if (GLSMDraws.getListMode() != 0) {
                attempted.countDown();
                assertTrue(attempted.await(5, TimeUnit.SECONDS));
                GLSMDraws.glEndList();
                completed.incrementAndGet();
            }
            worker.join();
        } finally {
            GLSMDraws.glDeleteLists(first, 1);
            GLSMDraws.glDeleteLists(second, 1);
        }
        assertEquals(1, completed.get());
        assertEquals(1, rejected.get());
    }

    @Test
    void reenteredContextRebindsFfpProgram() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());

        DrawableContexts.run(sharedDrawable, "SharedDrawableContext-Reentry-Thread", () -> {
            final ShaderManager sm = ShaderManager.getInstance();
            ShaderManager.enable();
            sm.activate();
            try {
                final FloatBuffer vertices = BufferUtils.createFloatBuffer(9);
                vertices.put(new float[] { -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0f, 0.5f, 0f });
                vertices.flip();
                final int vbo = GLSMBuffers.glGenBuffers();
                GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
                FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, 12, 0);
                VAOManager.setCurrentVertexFlags(0);

                GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

                GLStateManager.releaseContext(sharedDrawable);
                GLStateManager.makeCurrent(sharedDrawable);

                GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

                assertNotEquals(0, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), "invalidateProgram must force a rebind after replay clears the program to 0");

                GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GLSMBuffers.glDeleteBuffers(vbo);
            } finally {
                sm.deactivate();
            }
        });
    }

    private int compileIndexedListOnSharedDrawable() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        DrawableContexts.run(sharedDrawable, "SharedDrawableContext-IndexedList-Thread", () -> {
            final FloatBuffer vertices = BufferUtils.createFloatBuffer(9);
            vertices.put(new float[] { -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0f, 0.5f, 0f });
            vertices.flip();
            final ByteBuffer indices = BufferUtils.createByteBuffer(6);
            indices.putShort((short) 0).putShort((short) 1).putShort((short) 2);
            indices.flip();

            listVbo = GLSMBuffers.glGenBuffers();
            listEbo = GLSMBuffers.glGenBuffers();
            GLSMVertexInput.glBindVertexArray(GLSMVertexInput.glGenVertexArrays());
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, listVbo);
            GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            GLSMVertexInput.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
            GLSMVertexInput.glEnableVertexAttribArray(0);
            GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, listEbo);
            GLSMBuffers.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);

            indexedList = GLSMDraws.glGenLists(1);
            GLSMDraws.glNewList(indexedList, GL11.GL_COMPILE);
            GLSMDraws.glDrawElements(GL11.GL_TRIANGLES, 3, GL11.GL_UNSIGNED_SHORT, 0L);
            GLSMDraws.glEndList();

            final IndexedDrawBatch batch = indexedBatch();
            batch.bind();
            sharedContextVao = Reflect.<Integer>get(batch, "vao");
        });
        return indexedList;
    }

    private IndexedDrawBatch indexedBatch() {
        return DisplayListManager.getDisplayList(indexedList).getIndexedBatches().get(0);
    }

    @Test
    void indexedListCompiledOnSharedDrawableReplaysOnDisplay() throws Exception {
        final int list = compileIndexedListOnSharedDrawable();
        final ShaderManager sm = ShaderManager.getInstance();
        ShaderManager.enable();
        sm.activate();
        try {
            GLSMDraws.glCallList(list);

            indexedBatch().bind();
            assertEquals(indexedBatch().getSharedEBO(), GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
        } finally {
            sm.deactivate();
            GLSMVertexInput.glBindVertexArray(0);
        }
    }

    @Test
    void deletingListLeavesDisplayVaoWithTheSharedContextName() throws Exception {
        final int list = compileIndexedListOnSharedDrawable();
        final IntBuffer made = BufferUtils.createIntBuffer(4096);
        while (!GL30.glIsVertexArray(sharedContextVao) && made.hasRemaining()) {
            final int v = GLSMVertexInput.glGenVertexArrays();
            made.put(v);
            if (v == sharedContextVao) GLSMVertexInput.glBindVertexArray(v);
        }
        GLSMVertexInput.glBindVertexArray(0);
        assumeTrue(GL30.glIsVertexArray(sharedContextVao));

        GLSMDraws.glDeleteLists(list, 1);
        indexedList = 0;

        assertTrue(GL30.glIsVertexArray(sharedContextVao));
        made.flip();
        while (made.hasRemaining()) GLSMVertexInput.glDeleteVertexArrays(made.get());
    }

    @Test
    void recordedVaoBindReplaysOnlyOnItsCompileContext() throws Exception {
        final int bound = GLSMVertexInput.glGenVertexArrays();
        final int local = GLSMVertexInput.glGenVertexArrays();
        final int localList = GLSMDraws.glGenLists(1);
        final int foreignList = GLSMDraws.glGenLists(1);
        GLSMDraws.glNewList(localList, GL11.GL_COMPILE);
        GLSMVertexInput.glBindVertexArray(local);
        GLSMDraws.glEndList();

        sharedDrawable = new SharedDrawable(Display.getDrawable());
        DrawableContexts.run(sharedDrawable, "SharedDrawableContext-VaoBind-Thread", () -> {
            int foreign;
            do foreign = GLSMVertexInput.glGenVertexArrays(); while (foreign == bound);
            GLSMDraws.glNewList(foreignList, GL11.GL_COMPILE);
            GLSMVertexInput.glBindVertexArray(foreign);
            GLSMDraws.glEndList();
        });

        GLSMVertexInput.glBindVertexArray(bound);
        GLSMDraws.glCallList(foreignList);
        assertEquals(bound, GLSMVertexInput.getBoundVAO());
        assertEquals(bound, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        GLSMDraws.glCallList(localList);
        assertEquals(local, GLSMVertexInput.getBoundVAO());
        assertEquals(local, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));

        GLSMVertexInput.glBindVertexArray(0);
        GLSMVertexInput.glDeleteVertexArrays(bound);
        GLSMVertexInput.glDeleteVertexArrays(local);
        GLSMDraws.glDeleteLists(localList, 1);
        GLSMDraws.glDeleteLists(foreignList, 1);
    }

    @Test
    void deferredTextureDeletesWaitForSplashAndScrubDrawableContexts() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        final int t = GLSMTextures.glGenTextures();

        final AtomicReference<GLContextState> workerCtxRef = new AtomicReference<>();
        DrawableContexts.run(sharedDrawable, "SharedDrawableContext-DeferredDelete-Thread", () -> {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, t);
            workerCtxRef.set(GLStateManager.ctx());
        });

        final GLContextState workerCtx = workerCtxRef.get();
        int deferredGen = 0;
        int flushGen = 0;

        try {
            SplashWindow.setSplashComplete(false);
            GLSMTextures.glDeleteTextures(t);
            deferredGen = GLSMTextures.glGenTextures();

            assertTrue(GL11.glIsTexture(t), "delete must stay deferred while the splash is not complete");
            assertEquals(t, workerCtx.textures.getTextureUnitBindings(0).getBinding(), "worker ctx binding must survive until the flush");

            SplashWindow.setSplashComplete(true);
            flushGen = GLSMTextures.glGenTextures();

            assertFalse(GL11.glIsTexture(t), "the flush must delete the texture once the splash is complete");
            assertEquals(0, workerCtx.textures.getTextureUnitBindings(0).getBinding(), "the flush must scrub the drawable context's binding");
        } finally {
            SplashWindow.setSplashComplete(true);
            if (deferredGen != 0) GL11.glDeleteTextures(deferredGen);
            if (flushGen != 0) GL11.glDeleteTextures(flushGen);
        }
    }

    @Test
    void concurrentContextsDoNotShareAttribOrCurrentState() throws Exception {
        final GLContextState primary = GLStateManager.ctx();
        sharedDrawable = new SharedDrawable(Display.getDrawable());

        final CyclicBarrier barrier = new CyclicBarrier(2);
        final AtomicReference<GLContextState> workerCtxRef = new AtomicReference<>();
        final TestThreads.Worker worker = DrawableContexts.start(sharedDrawable, "SharedDrawableContext-Concurrent-Thread", () -> {
            workerCtxRef.set(GLStateManager.ctx());
            barrier.await();
            for (int i = 0; i < 20000; i++) {
                final int t = GLSMAttribStack.pushState(StateSet.FONT);
                GLSMCurrentAttribs.glNormal3f(0.1f, 0.2f, 0.3f);
                GLSMAttribStack.popStateTo(t);
            }
            GLSMCurrentAttribs.glNormal3f(0.1f, 0.2f, 0.3f);
        });

        try {
            barrier.await();
            for (int i = 0; i < 20000; i++) {
                final int t = GLSMAttribStack.pushState(StateSet.FONT);
                GLSMCurrentAttribs.glNormal3f(0.25f, 0.5f, 0.75f);
                GLSMAttribStack.popStateTo(t);
            }
            GLSMCurrentAttribs.glNormal3f(0.25f, 0.5f, 0.75f);
            worker.join();

            final GLContextState workerCtx = workerCtxRef.get();
            assertNotSame(primary, workerCtx);
            assertEquals(0, primary.attribDepth);
            assertEquals(0, workerCtx.attribDepth);
            assertEquals(0.25f, primary.ffp.currentNormal.x, 0.0f);
            assertEquals(0.5f, primary.ffp.currentNormal.y, 0.0f);
            assertEquals(0.75f, primary.ffp.currentNormal.z, 0.0f);
            assertEquals(0.1f, workerCtx.ffp.currentNormal.x, 0.0f);
            assertEquals(0.2f, workerCtx.ffp.currentNormal.y, 0.0f);
            assertEquals(0.3f, workerCtx.ffp.currentNormal.z, 0.0f);
        } finally {
            GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, 1.0f);
        }
    }
}

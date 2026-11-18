package net.coderbot.iris.gl.program;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.iris.IrisGLSMBridge;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.PipelineManager;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import org.junit.jupiter.api.Test;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

@GLCoreTest
class ProgramBindingRegressionTest {
    @Test
    void fallbackInitializationExecutesImmediatelyWithoutRecordingProgramBinds() {
        final String vertex = ShaderLoader.getShaderSource("angelica:fontFilter.vsh");
        final String fragment = ShaderLoader.getShaderSource("angelica:fontFilter.fsh");
        final int caller = linkedProgram();
        try {
            for (int mode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
                final int list = GLSMDraws.glGenLists(1);
                Program initialized = null;
                GLSMPrograms.glUseProgram(caller);
                try {
                    GLSMDraws.glNewList(list, mode);
                    initialized = ProgramBuilder.begin("fallback-test", vertex, null, fragment, ImmutableSet.of(0)).build();
                    assertEquals(mode == GL11.GL_COMPILE ? DisplayListManager.RecordMode.COMPILE
                        : DisplayListManager.RecordMode.COMPILE_AND_EXECUTE, DisplayListManager.getRecordMode());
                    assertEquals(caller, GLSMPrograms.getActiveProgram());
                    assertEquals(caller, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
                    GLSMDraws.glEndList();
                    final int location = GLSMPrograms.glGetUniformLocation(initialized.getProgramId(), "alphaTestRef");
                    assertNotEquals(-1, location);
                    final FloatBuffer value = BufferUtils.createFloatBuffer(4);
                    GLSMPrograms.glGetUniform(initialized.getProgramId(), location, value);
                    assertEquals(-1f, value.get(0));
                    assertEquals(0, DisplayListManager.getDisplayList(list).getCommandCounts().get(GLCommand.USE_PROGRAM));
                    GLSMDraws.glCallList(list);
                    assertEquals(caller, GLSMPrograms.getActiveProgram());
                } finally {
                    if (DisplayListManager.isRecording()) GLSMDraws.glEndList();
                    GLSMDraws.glDeleteLists(list, 1);
                    if (initialized != null) initialized.destroy();
                }
            }
        } finally {
            GLSMPrograms.glUseProgram(0);
            GLSMPrograms.glDeleteProgram(caller);
        }
    }

    @Test
    void deletionBookkeepingRetainsOnlyDeferredDeletes() {
        final int bound = linkedProgram();
        GLSMPrograms.glUseProgram(bound);
        GLSMPrograms.glDeleteProgram(bound);
        assertTrue(GLSMPrograms.isProgramPendingDeletion(bound));
        assertTrue(GL20.glIsProgram(bound), "driver keeps the bound program alive");
        final int other = linkedProgram();
        GLSMPrograms.glUseProgram(other);
        for (int i = 0; i < 8; i++) {
            final int unused = GLSMPrograms.glCreateProgram();
            GLSMPrograms.glDeleteProgram(unused);
            assertFalse(GLSMPrograms.isProgramPendingDeletion(unused));
            assertFalse(GL20.glIsProgram(unused), "driver deletes an unbound program immediately");
            assertFalse(GLSMPrograms.isProgramPendingDeletion(bound));
        }
        GLSMPrograms.glUseProgram(0);
        GLSMPrograms.glDeleteProgram(other);
    }

    private static int linkedProgram() {
        return GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
    }

    @Test
    void managedSwitchesUpdateOnceAndForeignBindsStillUpdate() {
        IrisGLSMBridge.register();
        PipelineManager manager = Iris.getPipelineManager();
        WorldRenderingPipeline previous = manager.getPipelineNullable();
        boolean enabled = Iris.enabled;
        int id = linkedProgram();
        int other = linkedProgram();
        ProgramUniforms uniforms = mock(ProgramUniforms.class);
        Program program = new Program(id, null, uniforms, mock(ProgramSamplers.class), mock(ProgramImages.class));
        DeferredWorldRenderingPipeline pipeline = mock(DeferredWorldRenderingPipeline.class);
        DeferredWorldRenderingPipeline.Pass pass = Reflect.allocate(DeferredWorldRenderingPipeline.Pass.class);
        Reflect.set(pass, "program", program);
        when(pipeline.getActivePassProgram()).thenReturn(pass);
        Reflect.set(manager, "pipeline", pipeline);
        Reflect.setStaticFinal(Iris.class, "enabled", boolean.class, true);
        try {
            for (int i = 0; i < 3; i++) {
                GLSMPrograms.glUseProgram(other);
                program.use();
                verify(uniforms, times(i + 1)).update();
                assertFalse(Program.isManagedBind(id));
            }
            program.use(); // Cached binds must still update dynamic uniforms.
            verify(uniforms, times(4)).update();
            GLSMPrograms.glUseProgram(other);
            GLSMPrograms.glUseProgram(id);
            verify(uniforms, times(5)).update();
        } finally {
            Reflect.setStaticFinal(Iris.class, "enabled", boolean.class, enabled);
            Reflect.set(manager, "pipeline", previous);
            GLSMPrograms.glUseProgram(0);
            program.destroy();
            GLSMPrograms.glDeleteProgram(other);
        }
    }

    @Test
    void replayCachesValidityAndRevalidatesDeletionIncludingBoundPrograms() {
        int id = linkedProgram();
        int other = linkedProgram();
        int list = GLSMDraws.glGenLists(1);
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMPrograms.glUseProgram(id);
        GLSMDraws.glEndList();
        RenderBackend original = BackendManager.RENDER_BACKEND;
        RenderBackend backend = mock(RenderBackend.class, delegatesTo(original));
        Reflect.setStaticFinal(BackendManager.class, "RENDER_BACKEND", RenderBackend.class, backend);
        boolean idDeleted = false;
        boolean otherDeleted = false;
        try {
            for (int i = 0; i < 5; i++) GLSMDraws.glCallList(list);
            verify(backend, times(1)).isProgram(id);
            assertEquals(id, GLSMPrograms.getActiveProgram());
            assertEquals(id, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));

            GLSMPrograms.glDeleteProgram(id);
            idDeleted = true;
            GLSMDraws.glCallList(list); // Still valid while bound, but must not be cached.
            assertEquals(id, GLSMPrograms.getActiveProgram());
            assertEquals(id, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            GLSMPrograms.glUseProgram(other); // Completes deferred deletion.
            GLSMDraws.glCallList(list);
            assertEquals(0, GLSMPrograms.getActiveProgram());
            verify(backend, times(4)).isProgram(id);
            GLSMDraws.glCallList(list);
            verify(backend, times(4)).isProgram(id);

            GLSMPrograms.glDeleteProgram(other);
            otherDeleted = true;
            GLSMDraws.glCallList(list);
            verify(backend, times(5)).isProgram(id);
        } finally {
            Reflect.setStaticFinal(BackendManager.class, "RENDER_BACKEND", RenderBackend.class, original);
            GLSMPrograms.glUseProgram(0);
            GLSMDraws.glDeleteLists(list, 1);
            // FFP's glUseProgram(0) is lazy, so other stays current; deleting it again is GL_INVALID_OPERATION on macOS
            if (!idDeleted) GLSMPrograms.glDeleteProgram(id);
            if (!otherDeleted) GLSMPrograms.glDeleteProgram(other);
        }
    }
}

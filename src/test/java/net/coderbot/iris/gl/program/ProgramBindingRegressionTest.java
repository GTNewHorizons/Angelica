package net.coderbot.iris.gl.program;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
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
                final int list = GLStateManager.glGenLists(1);
                Program initialized = null;
                GLStateManager.glUseProgram(caller);
                try {
                    GLStateManager.glNewList(list, mode);
                    initialized = ProgramBuilder.begin("fallback-test", vertex, null, fragment, ImmutableSet.of(0)).build();
                    assertEquals(mode == GL11.GL_COMPILE ? DisplayListManager.RecordMode.COMPILE
                        : DisplayListManager.RecordMode.COMPILE_AND_EXECUTE, DisplayListManager.getRecordMode());
                    assertEquals(caller, GLStateManager.getActiveProgram());
                    assertEquals(caller, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
                    GLStateManager.glEndList();
                    final int location = GLStateManager.glGetUniformLocation(initialized.getProgramId(), "alphaTestRef");
                    assertNotEquals(-1, location);
                    final FloatBuffer value = BufferUtils.createFloatBuffer(4);
                    GLStateManager.glGetUniform(initialized.getProgramId(), location, value);
                    assertEquals(-1f, value.get(0));
                    assertEquals(0, DisplayListManager.getDisplayList(list).getCommandCounts().get(GLCommand.USE_PROGRAM));
                    GLStateManager.glCallList(list);
                    assertEquals(caller, GLStateManager.getActiveProgram());
                } finally {
                    if (DisplayListManager.isRecording()) GLStateManager.glEndList();
                    GLStateManager.glDeleteLists(list, 1);
                    if (initialized != null) initialized.destroy();
                }
            }
        } finally {
            GLStateManager.glUseProgram(0);
            GLStateManager.glDeleteProgram(caller);
        }
    }

    @Test
    void deletionBookkeepingRetainsOnlyDeferredDeletes() {
        final int bound = linkedProgram();
        GLStateManager.glUseProgram(bound);
        GLStateManager.glDeleteProgram(bound);
        assertTrue(GLStateManager.isProgramPendingDeletion(bound));
        final int other = linkedProgram();
        GLStateManager.glUseProgram(other);
        for (int i = 0; i < 8; i++) {
            final int unused = GLStateManager.glCreateProgram();
            GLStateManager.glDeleteProgram(unused);
            assertFalse(GLStateManager.isProgramPendingDeletion(unused));
            assertFalse(GLStateManager.isProgramPendingDeletion(bound));
        }
        GLStateManager.glUseProgram(0);
        GLStateManager.glDeleteProgram(other);
    }

    private static int linkedProgram() {
        int vertex = GLStateManager.glCreateShader(GL20.GL_VERTEX_SHADER);
        int fragment = GLStateManager.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GLStateManager.glShaderSource(vertex, "#version 330 core\nvoid main() { gl_Position = vec4(0); }");
        GLStateManager.glShaderSource(fragment, "#version 330 core\nout vec4 color; void main() { color = vec4(1); }");
        GLStateManager.glCompileShader(vertex);
        GLStateManager.glCompileShader(fragment);
        int program = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(program, vertex);
        GLStateManager.glAttachShader(program, fragment);
        GLStateManager.glLinkProgram(program);
        GLStateManager.glDeleteShader(vertex);
        GLStateManager.glDeleteShader(fragment);
        assertEquals(GL11.GL_TRUE, GLStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS));
        return program;
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
                GLStateManager.glUseProgram(other);
                program.use();
                verify(uniforms, times(i + 1)).update();
                assertFalse(Program.isManagedBind(id));
            }
            program.use(); // Cached binds must still update dynamic uniforms.
            verify(uniforms, times(4)).update();
            GLStateManager.glUseProgram(other);
            GLStateManager.glUseProgram(id);
            verify(uniforms, times(5)).update();
        } finally {
            Reflect.setStaticFinal(Iris.class, "enabled", boolean.class, enabled);
            Reflect.set(manager, "pipeline", previous);
            GLStateManager.glUseProgram(0);
            program.destroy();
            GLStateManager.glDeleteProgram(other);
        }
    }

    @Test
    void replayCachesValidityAndRevalidatesDeletionIncludingBoundPrograms() {
        int id = linkedProgram();
        int other = linkedProgram();
        int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glUseProgram(id);
        GLStateManager.glEndList();
        RenderBackend original = BackendManager.RENDER_BACKEND;
        RenderBackend backend = mock(RenderBackend.class, delegatesTo(original));
        Reflect.setStaticFinal(BackendManager.class, "RENDER_BACKEND", RenderBackend.class, backend);
        try {
            for (int i = 0; i < 5; i++) GLStateManager.glCallList(list);
            verify(backend, times(1)).isProgram(id);
            assertEquals(id, GLStateManager.getActiveProgram());

            GLStateManager.glDeleteProgram(id);
            GLStateManager.glCallList(list); // Still valid while bound, but must not be cached.
            assertEquals(id, GLStateManager.getActiveProgram());
            GLStateManager.glUseProgram(other); // Completes deferred deletion.
            GLStateManager.glCallList(list);
            assertEquals(0, GLStateManager.getActiveProgram());
            verify(backend, times(4)).isProgram(id);
            GLStateManager.glCallList(list);
            verify(backend, times(4)).isProgram(id);

            GLStateManager.glDeleteProgram(other);
            GLStateManager.glCallList(list);
            verify(backend, times(5)).isProgram(id);
        } finally {
            Reflect.setStaticFinal(BackendManager.class, "RENDER_BACKEND", RenderBackend.class, original);
            GLStateManager.glUseProgram(0);
            GLStateManager.glDeleteLists(list, 1);
            if (GLStateManager.glIsProgram(id)) GLStateManager.glDeleteProgram(id);
            if (GLStateManager.glIsProgram(other)) GLStateManager.glDeleteProgram(other);
        }
    }
}

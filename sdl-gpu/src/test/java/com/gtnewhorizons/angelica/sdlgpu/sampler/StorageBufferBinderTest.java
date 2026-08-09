package com.gtnewhorizons.angelica.sdlgpu.sampler;

import java.lang.reflect.InvocationTargetException;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageBufferBinderTest {

    @Test
    void bindStage_emptySlotsIsNoOp() {
        final ShaderManager.ProgramObject prog = new ShaderManager.ProgramObject();
        prog.linked = true;
        prog.fragmentGraphicsBindingMap = ShaderManager.GraphicsBindingMap.EMPTY;

        final ContextState st = new ContextState();
        st.boundProgram = 1;

        final StorageBufferBinder binder = new StorageBufferBinder(null, null);
        assertDoesNotThrow(() -> invokeBindStage(binder, st, prog, true));
        assertEquals(0, st.lastFragStorageBufProgram);
    }

    @Test
    void bindStage_rejectsOverCapDeclaration() {
        final int[] tooMany = new int[ContextState.MAX_STORAGE_BUFFERS_PER_STAGE + 1];
        final ShaderManager.ProgramObject prog = new ShaderManager.ProgramObject();
        prog.linked = true;
        prog.fragmentGraphicsBindingMap = new ShaderManager.GraphicsBindingMap(new int[0], new int[0], tooMany);

        final ContextState st = new ContextState();
        st.boundProgram = 1;

        final StorageBufferBinder binder = new StorageBufferBinder(null, null);
        assertThrows(InvocationTargetException.class, () -> invokeBindStage(binder, st, prog, true));
    }

    private static void invokeBindStage(StorageBufferBinder binder, ContextState st, ShaderManager.ProgramObject prog, boolean fragment) throws Exception {
        final var m = StorageBufferBinder.class.getDeclaredMethod("bindStage", long.class, ContextState.class, ShaderManager.ProgramObject.class, boolean.class);
        m.setAccessible(true);
        m.invoke(binder, 0xDEADBEEFL, st, prog, fragment);
    }
}

package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.SdlReflect;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDL_GPUStorageBufferReadWriteBinding;
import org.lwjgl.sdl.SDL_GPUStorageTextureReadWriteBinding;
import org.lwjgl.system.MemoryStack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ComputeBinderFallbackTest {

    private static final int RGBA8 = InternalTextureFormat.RGBA8.getGlFormat();
    private static final long STAND_IN_RW = 0x515A_0001L;
    private static final long STAND_IN_RO = 0x515A_0002L;

    private ResourceManager rm;
    private ComputeBinder binder;
    private ContextState st;
    private ShaderManager.ProgramObject prog;

    @BeforeEach
    void setUp() {
        rm = SdlTestRig.resourceManager();
        SdlReflect.putComputeStandIn(rm, RGBA8, GL11.GL_TEXTURE_2D, true, STAND_IN_RW);
        SdlReflect.putComputeStandIn(rm, RGBA8, GL11.GL_TEXTURE_2D, false, STAND_IN_RO);
        binder = new ComputeBinder(null, rm, null, null);
        st = new ContextState();
        st.boundProgram = 1;
        prog = new ShaderManager.ProgramObject();
        prog.linked = true;
    }

    private static ShaderManager.ComputeBindingMap mapWith(int[] roImages, int[] rwImages, int[] rwSsbos, int[] formats, int[] targets) {
        return new ShaderManager.ComputeBindingMap(
            new int[0], roImages, rwImages, new int[0], rwSsbos, new int[0],
            new String[0], new String[roImages.length], new String[rwImages.length],
            new boolean[0], new int[0],
            roImages.length == 0 ? new int[0] : formats,
            rwImages.length == 0 ? new int[0] : formats,
            roImages.length == 0 ? new int[0] : targets,
            rwImages.length == 0 ? new int[0] : targets);
    }

    @Test
    void rwStorageImageSubstitutesStandInAtMipZero() {
        prog.computeBindingMap = mapWith(new int[0], new int[] { 0 }, new int[0], new int[] { RGBA8 }, new int[] { GL11.GL_TEXTURE_2D });
        st.boundStorageTextureLevel[0] = 3;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final SDL_GPUStorageTextureReadWriteBinding.Buffer buf = Reflect.invoke(binder, "buildRwTextureBindings",
                new Class<?>[] { ContextState.class, ShaderManager.ProgramObject.class, ShaderManager.ComputeBindingMap.class, MemoryStack.class },
                st, prog, prog.computeBindingMap, stack);
            assertNotNull(buf, "an unbound RW image substitutes rather than dropping the dispatch");
            assertEquals(STAND_IN_RW, buf.get(0).texture());
            assertEquals(0, buf.get(0).mip_level(), "the stand-in has a single level, so the bound level cannot carry over");
        }
    }

    @Test
    void rwStorageBufferStillDropsTheDispatch() {
        prog.computeBindingMap = mapWith(new int[0], new int[0], new int[] { 2 }, new int[0], new int[0]);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final SDL_GPUStorageBufferReadWriteBinding.Buffer buf = Reflect.invoke(binder, "buildRwBufferBindings",
                new Class<?>[] { ContextState.class, int[].class, MemoryStack.class, boolean.class },
                st, new int[] { 2 }, stack, true);
            assertNull(buf, "no robust buffer access, so an undersized stand-in would make an OOB write undefined");
        }
    }

    @Test
    void storageImageStandInIsPickedByDeclaredFormatAndRole() {
        final ShaderManager.ComputeBindingMap map = mapWith(new int[] { 0 }, new int[] { 0 }, new int[0], new int[] { RGBA8 }, new int[] { GL11.GL_TEXTURE_2D });
        assertEquals(STAND_IN_RO, standIn(map, 0, false));
        assertEquals(STAND_IN_RW, standIn(map, 0, true), "SDL forbids reading a texture bound read-write without SIMULTANEOUS");
    }

    @Test
    void storageImageWithoutDeclaredFormatHasNoStandIn() {
        final ShaderManager.ComputeBindingMap map = mapWith(new int[0], new int[] { 0 }, new int[0], new int[] { 0 }, new int[] { GL11.GL_TEXTURE_2D });
        assertEquals(0L, standIn(map, 0, true));
    }

    @Test
    void unqualifiedStorageImageFallsBackToTheBoundImageFormat() {
        final ShaderManager.ComputeBindingMap map = mapWith(new int[0], new int[] { 0 }, new int[0], new int[] { 0 }, new int[] { GL11.GL_TEXTURE_2D });
        st.boundStorageTextureFormat[0] = RGBA8;
        assertEquals(STAND_IN_RW, standIn(map, 0, true));
    }

    @Test
    void declaredFormatOutranksTheBoundImageFormat() {
        final ShaderManager.ComputeBindingMap map = mapWith(new int[0], new int[] { 0 }, new int[0], new int[] { RGBA8 }, new int[] { GL11.GL_TEXTURE_2D });
        st.boundStorageTextureFormat[0] = InternalTextureFormat.R32UI.getGlFormat();
        assertEquals(STAND_IN_RW, standIn(map, 0, true), "Vulkan needs the view format to match the shader declaration");
    }

    private long standIn(ShaderManager.ComputeBindingMap map, int index, boolean readWrite) {
        return Reflect.invoke(binder, "standInStorageTex",
            new Class<?>[] { ContextState.class, ShaderManager.ComputeBindingMap.class, int.class, int.class, boolean.class },
            st, map, index, 0, readWrite);
    }
}

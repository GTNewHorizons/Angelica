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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeBatchJoinTest {

    private static final int RGBA8 = InternalTextureFormat.RGBA8.getGlFormat();
    private static final long STAND_IN_RW = 0x515A_0001L;

    private ResourceManager rm;
    private ComputeBinder binder;
    private ContextState st;
    private ShaderManager.ProgramObject prog;

    @BeforeEach
    void setUp() {
        rm = SdlTestRig.resourceManager();
        SdlReflect.putComputeStandIn(rm, RGBA8, GL11.GL_TEXTURE_2D, true, STAND_IN_RW);
        binder = new ComputeBinder(null, rm, null, null);
        st = new ContextState();
        st.boundProgram = 1;
        st.computeBatchProgram = 1;
        prog = new ShaderManager.ProgramObject();
        prog.linked = true;
        prog.sdlComputePipeline = 0x9001L;
        prog.computeBindingMap = new ShaderManager.ComputeBindingMap(
            new int[0], new int[0], new int[] { 0 }, new int[0], new int[0], new int[0],
            new String[0], new String[0], new String[1],
            new boolean[0], new int[0],
            new int[0], new int[] { RGBA8 }, new int[0], new int[] { GL11.GL_TEXTURE_2D });
    }

    @Test
    void substitutedRwImageStillJoinsItsOwnBatch() {
        snapshot();
        assertTrue(canJoin(), "the second dispatch resolves the same stand-in the snapshot recorded");
    }

    @Test
    void realBindingAppearingAfterSnapshotBreaksTheBatch() {
        snapshot();
        SdlReflect.putTextureHandle(rm, 77, 0xBEEF_0077L);
        st.boundStorageTextureByUnit[0] = 77;
        assertFalse(canJoin(), "a real texture is a different RW binding and needs its own pass");
    }

    private void snapshot() {
        Reflect.invoke(binder, "snapshotRwBindings", new Class<?>[] { ContextState.class, ShaderManager.ProgramObject.class }, st, prog);
    }

    private boolean canJoin() {
        return Reflect.invoke(binder, "canJoinBatch", new Class<?>[] { ContextState.class, ShaderManager.ProgramObject.class }, st, prog);
    }
}

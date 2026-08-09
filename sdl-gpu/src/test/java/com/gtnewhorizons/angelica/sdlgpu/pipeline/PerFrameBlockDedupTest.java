package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.ProgramObject;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.UniformMemberInfo;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.spvc.Spvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerFrameBlockDedupTest {

    private static final int BLOCK_SIZE = 64;

    private static ProgramObject programWithBlockMemberAt(int offset, int blockIdx) {
        final ProgramObject prog = new ProgramObject();
        prog.blockSize[blockIdx] = BLOCK_SIZE;
        prog.nextUniformLocation = 1;
        prog.blockMemberInfo[blockIdx].put(0, new UniformMemberInfo(offset, 4, 0, false, 1, 1, Spvc.SPVC_BASETYPE_FP32, 1));
        prog.buildUniformSlotArrays();
        return prog;
    }

    private final PipelineApplier applier = new PipelineApplier(null, null, null, null, null, null, null, null, null);
    private final ContextState st = new ContextState();

    @Test
    void identicalValueFromASecondProgramDoesNotDirtyTheBlock() {
        for (int b = 0; b < ShaderManager.BLOCK_COUNT; b++) {
            final ProgramObject a = programWithBlockMemberAt(16, b);
            final ProgramObject c = programWithBlockMemberAt(16, b);

            st.boundProgramObj = a;
            applier.putUniform(st, 0, new float[]{0.75f});
            assertTrue(st.uniformBlocks[b].dirty, "first write must dirty block " + b);

            st.uniformBlocks[b].dirty = false;
            st.boundProgramObj = c;
            applier.putUniform(st, 0, new float[]{0.75f});
            assertFalse(st.uniformBlocks[b].dirty, "a second program writing the same value must not dirty block " + b + " - that is what breaks a render pass");
        }
    }

    @Test
    void changedValueFromASecondProgramStillDirties() {
        final ProgramObject a = programWithBlockMemberAt(16, ShaderManager.BLOCK_PER_FRAME);
        final ProgramObject b = programWithBlockMemberAt(16, ShaderManager.BLOCK_PER_FRAME);

        st.boundProgramObj = a;
        applier.putUniform(st, 0, new float[]{0.75f});
        st.uniformBlocks[0].dirty = false;

        st.boundProgramObj = b;
        applier.putUniform(st, 0, new float[]{0.25f});
        assertTrue(st.uniformBlocks[0].dirty, "a genuinely new value must still reach the buffer");
    }

    @Test
    void distinctOffsetsAreTrackedSeparately() {
        st.boundProgramObj = programWithBlockMemberAt(16, ShaderManager.BLOCK_PER_FRAME);
        applier.putUniform(st, 0, new float[]{0.75f});
        st.uniformBlocks[0].dirty = false;

        st.boundProgramObj = programWithBlockMemberAt(32, ShaderManager.BLOCK_PER_FRAME);
        applier.putUniform(st, 0, new float[]{0.75f});
        assertTrue(st.uniformBlocks[0].dirty, "same value at a different offset is a different member");
    }

    @Test
    void blocksTrackHashesIndependently() {
        st.boundProgramObj = programWithBlockMemberAt(16, ShaderManager.BLOCK_PER_FRAME);
        applier.putUniform(st, 0, new float[]{0.75f});
        st.uniformBlocks[0].dirty = false;

        st.boundProgramObj = programWithBlockMemberAt(16, ShaderManager.BLOCK_PER_PASS);
        applier.putUniform(st, 0, new float[]{0.75f});
        assertTrue(st.uniformBlocks[1].dirty, "per-pass block has its own hash slots");
        assertFalse(st.uniformBlocks[0].dirty, "per-frame block untouched");
    }

    @Test
    void dedupDoesNotDropTheValueItself() {
        st.boundProgramObj = programWithBlockMemberAt(16, ShaderManager.BLOCK_PER_FRAME);
        applier.putUniform(st, 0, new float[]{0.75f});
        final float first = st.uniformBlocks[0].staging(BLOCK_SIZE).get(16 >> 2);

        st.boundProgramObj = programWithBlockMemberAt(16, ShaderManager.BLOCK_PER_FRAME);
        applier.putUniform(st, 0, new float[]{0.25f});
        final float second = st.uniformBlocks[0].staging(BLOCK_SIZE).get(16 >> 2);

        assertNotEquals(first, second, "the new value must be in the staging buffer");
    }
}

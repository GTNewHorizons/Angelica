package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLSMCoreExtension;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL31;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(GLSMCoreExtension.class)
class FFPUniformBlockLayoutGLTest {

    @Test
    void driverOffsetsMatchJavaConstants() {
        final VertexKey vk = VertexKey.fromState(false, false, false, false, 0);
        final FragmentKey fk = FragmentKey.fromState();
        final Program program = Program.create(vk, fk, VertexShaderGenerator.generate(vk), FragmentShaderGenerator.generate(fk), null);
        try {
            final int programId = program.getProgramId();
            final int blockIndex = GL31.glGetUniformBlockIndex(programId, FFPUniformBlock.BLOCK_NAME);
            assertNotEquals(GL31.GL_INVALID_INDEX, blockIndex, "block must be active");

            final int blockSize = GL31.glGetActiveUniformBlocki(programId, blockIndex, GL31.GL_UNIFORM_BLOCK_DATA_SIZE);
            assertEquals(FFPUniformBlock.SIZE, blockSize, "std140 block data size");

            final Object2IntMap<String> expected = FFPUniformBlock.MEMBER_OFFSETS;
            final CharSequence[] names = expected.keySet().toArray(new CharSequence[0]);
            final IntBuffer indices = BufferUtils.createIntBuffer(names.length);
            GL31.glGetUniformIndices(programId, names, indices);
            final IntBuffer offsets = BufferUtils.createIntBuffer(names.length);
            GL31.glGetActiveUniforms(programId, indices, GL31.GL_UNIFORM_OFFSET, offsets);

            for (int i = 0; i < names.length; i++) {
                final String name = names[i].toString();
                assertNotEquals(GL31.GL_INVALID_INDEX, indices.get(i), name + " must be an active block member");
                assertEquals(expected.getInt(name), offsets.get(i), "std140 offset of " + name);
            }
        } finally {
            program.destroy();
        }
    }
}

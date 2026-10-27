package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.EntityIdsOnlyHandler;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorEntityIdsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.BLUE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.GREEN;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlTessellatorEntityIdsTest {

    private ImmediateExtendedAttribHandler previousHandler;
    private int program;

    @BeforeEach
    void setUp() {
        previousHandler = EntityIdsOnlyHandler.install();
        program = GlTestSupport.program(TessellatorEntityIdsFixture.VERTEX, TessellatorEntityIdsFixture.FRAGMENT);
    }

    @AfterEach
    void tearDown() {
        GLSMPrograms.glUseProgram(0);
        GLSMPrograms.glDeleteProgram(program);
        GLSMHooks.immediateExtendedHandler = previousHandler;
    }

    @Test
    void idsChangedMidBatchApplyToTheVerticesAddedAfterTheChange() {
        GlsmSdlHeadlessRig.beginFrameAndClear();
        GLSMPrograms.glUseProgram(program);
        TessellatorEntityIdsFixture.drawIdsChangedMidBatch();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 4, SIZE / 2, GREEN, "left quad was added under item 7");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, 3 * SIZE / 4, SIZE / 2, BLUE, "right quad was added under item 50020");
    }

    @Test
    void idsChangedAfterTheLastVertexLeaveTheBatchAlone() {
        GlsmSdlHeadlessRig.beginFrameAndClear();
        GLSMPrograms.glUseProgram(program);
        TessellatorEntityIdsFixture.drawIdsChangedAfterTheLastVertex();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 4, SIZE / 2, GREEN, "every vertex was added under item 7");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, 3 * SIZE / 4, SIZE / 2, GREEN, "every vertex was added under item 7");
    }
}

package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.SdlAsserts;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.ProgramObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniformLocationKindTest {

    private static final int PLAIN_LOC = 0;
    private static final int SAMPLER_LOC = 1;
    private static final int IMAGE_LOC = 2;

    private static SDLGPURenderBackend backend;

    private ContextState st;
    private ProgramObject prog;
    private ProgramObject savedProgramObj;
    private int savedProgram;

    @BeforeAll
    static void createBackend() {
        backend = new SDLGPURenderBackend();
    }

    @BeforeEach
    void setUp() {
        st = SdlTestRig.contextState();
        savedProgramObj = st.boundProgramObj;
        savedProgram = st.boundProgram;

        prog = new ProgramObject();
        prog.linked = true;
        prog.nextUniformLocation = 3;
        registerLocation(PLAIN_LOC, "u_plain");
        registerLocation(SAMPLER_LOC, "u_sampler");
        registerLocation(IMAGE_LOC, "u_image");
        prog.allSamplerNames.add("u_sampler");
        prog.allImageNames.add("u_image");
        prog.buildUniformSlotArrays();

        st.boundProgramObj = prog;
        st.boundProgram = 1;
    }

    @AfterEach
    void tearDown() {
        st.releaseUniformStaging(prog);
        st.boundProgramObj = savedProgramObj;
        st.boundProgram = savedProgram;
    }

    private void registerLocation(int loc, String name) {
        prog.nameToLocation.put(name, loc);
        prog.locationToName.put(loc, name);
    }

    @Test
    void buildUniformSlotArraysClassifiesEveryLocation() {
        assertEquals(ProgramObject.LOCATION_KIND_PLAIN, prog.locationKind[PLAIN_LOC]);
        assertEquals(ProgramObject.LOCATION_KIND_SAMPLER, prog.locationKind[SAMPLER_LOC]);
        assertEquals(ProgramObject.LOCATION_KIND_IMAGE, prog.locationKind[IMAGE_LOC]);
        assertEquals("u_plain", prog.locationName[PLAIN_LOC]);
        assertEquals("u_sampler", prog.locationName[SAMPLER_LOC]);
        assertEquals("u_image", prog.locationName[IMAGE_LOC]);
        assertEquals(-1, prog.locationSamplerUnit[SAMPLER_LOC], "a fresh link has not bound a sampler unit yet");
    }

    @Test
    void samplerLocationRoutesToTheSamplerMapAndDirtiesOnFirstWrite() {
        backend.uniform1i(SAMPLER_LOC, 3);
        assertEquals(3, prog.samplerTextureUnits.getInt("u_sampler"));
        assertEquals(3, prog.locationSamplerUnit[SAMPLER_LOC]);
        assertTrue(prog.samplerUnitsDirty);

        prog.samplerUnitsDirty = false;
        backend.uniform1i(SAMPLER_LOC, 3);
        assertFalse(prog.samplerUnitsDirty, "an unchanged sampler unit must not re-dirty");
    }

    @Test
    void imageLocationRoutesToTheImageMap() {
        backend.uniform1i(IMAGE_LOC, 5);
        assertEquals(5, prog.imageTextureUnits.getInt("u_image"));
    }

    @Test
    void changedSamplerValueDirtiesAndUpdatesBothTheArrayAndTheMap() {
        backend.uniform1i(SAMPLER_LOC, 3);
        prog.samplerUnitsDirty = false;

        backend.uniform1i(SAMPLER_LOC, 4);
        assertEquals(4, prog.samplerTextureUnits.getInt("u_sampler"));
        assertEquals(4, prog.locationSamplerUnit[SAMPLER_LOC]);
        assertTrue(prog.samplerUnitsDirty);
    }

    @Test
    void repeatedUnchangedSamplerWritesDoNotAllocate() {
        backend.uniform1i(SAMPLER_LOC, 7);
        SdlAsserts.assertAllocationFree(() -> backend.uniform1i(SAMPLER_LOC, 7), "unchanged sampler-unit uniform1i");
    }
}

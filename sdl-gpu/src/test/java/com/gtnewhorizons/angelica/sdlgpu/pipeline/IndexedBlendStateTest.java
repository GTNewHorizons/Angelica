package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.SdlAsserts;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BLENDFACTOR_ONE;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BLENDFACTOR_SRC_ALPHA;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BLENDFACTOR_ZERO;

class IndexedBlendStateTest {

    private SDLGPURenderBackend backend;
    private ContextState st;

    @BeforeEach
    void resetBlend() {
        backend = new SDLGPURenderBackend();
        st = SdlTestRig.contextState();
        backend.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        backend.disable(GL11.GL_BLEND);
    }

    @AfterEach
    void restoreBlend() {
        backend.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        backend.disable(GL11.GL_BLEND);
    }

    @Test
    void indexedBlendFuncChangesOnlyThatDrawBuffer() {
        backend.blendFuncSeparatei(1, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);

        for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
            if (i == 1) {
                SdlAsserts.assertBlendFactors(st.pipeline, i, SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE, "indexed override");
            } else {
                SdlAsserts.assertBlendFactors(st.pipeline, i, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO, "indexed override");
            }
        }
    }

    @Test
    void globalBlendFuncSetsEveryDrawBuffer() {
        backend.blendFuncSeparatei(2, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        backend.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
            SdlAsserts.assertBlendFactors(st.pipeline, i, SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO, "global set");
        }
    }

    @Test
    void indexedEnableSetsOnlyThatDrawBuffer() {
        backend.enablei(GL11.GL_BLEND, 2);

        for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
            assertEquals(i == 2, st.pipeline.blendEnabledPerDrawBuffer[i], "blend enable of draw buffer " + i);
        }
    }

    @Test
    void outOfRangeIndexedBlendFuncIsIgnored() {
        final int pastEnd = ContextState.MAX_COLOR_ATTACHMENTS;
        backend.blendFuncSeparatei(pastEnd, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        backend.blendFuncSeparatei(-1, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);

        for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
            SdlAsserts.assertBlendFactors(st.pipeline, i, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ZERO, "out of range ignored");
        }
    }
}

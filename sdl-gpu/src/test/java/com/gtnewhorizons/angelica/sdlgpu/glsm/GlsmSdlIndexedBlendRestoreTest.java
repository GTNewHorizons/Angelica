package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.SdlAsserts;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BLENDFACTOR_ONE;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BLENDFACTOR_SRC_ALPHA;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlIndexedBlendRestoreTest {

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    private static PipelineCache pipeline() {
        final ContextState st = Reflect.invokeStatic(SDLGPURenderBackend.class, "s", new Class<?>[0]);
        return st.pipeline;
    }

    private static void vanillaBlendFunc() {
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Test
    void vanillaBlendFuncAfterIndexedOverrideReachesEveryDrawBuffer() {
        try {
            vanillaBlendFunc();
            RenderSystem.blendFuncSeparatei(1, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);

            final PipelineCache p = pipeline();
            SdlAsserts.assertBlendFactors(p, 1, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE, "override");
            SdlAsserts.assertBlendFactors(p, 0, SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, "override");

            vanillaBlendFunc();

            for (int i = 0; i < ContextState.MAX_COLOR_ATTACHMENTS; i++) {
                SdlAsserts.assertBlendFactors(p, i, SDL_GPU_BLENDFACTOR_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, SDL_GPU_BLENDFACTOR_ONE, SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA, "restore");
            }
            assertFalse(GLStateManager.getBlendState().isFuncUnknown(), "restore forward must clear the unknown flag");
        } finally {
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }
}

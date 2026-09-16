package com.gtnewhorizons.umbra.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Mixins implements IMixins {

    UMBRA_STARTUP(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "early.startup.MixinInitGLStateManager"
        )
    ),

    UMBRA_CORE_PROFILE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "early.ffp.MixinTessellator_CoreProfile"
        )
    ),

    UMBRA_GLSM(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "early.glsm.MixinForgeHooksClient_CoreProfile",
            "early.glsm.MixinGameSettings_VSync",
            "early.glsm.MixinSplashProgressCaching",
            "early.glsm.MixinMinecraft_FrameHook",
            "early.glsm.MixinMinecraft_IconifyGuard",
            "early.glsm.MixinMinecraft_VSync",
            "early.glsm.MixinWorldRenderer_VertexState",
            "early.textures.MixinTextureUtil"
        )
    ),

    UMBRA_SDL_GPU_DISPLAY(new MixinBuilder("SDL-GPU-aware Display.create path")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> SystemProperties.USE_SDL_GPU && SDLGPUGate.isSDLGPUAvailable())
        .addClientMixins(
            "early.sdlgpu.MixinForgeHooksClient_SDLGPUDisplay",
            "early.sdlgpu.MixinMinecraft_SDLGPUIcons"
        )
    ),

    ;

    private final MixinBuilder builder;
}

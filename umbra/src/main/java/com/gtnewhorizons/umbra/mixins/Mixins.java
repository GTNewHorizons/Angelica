package com.gtnewhorizons.umbra.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
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
            "early.glsm.MixinSplashProgressCaching",
            "early.glsm.MixinMinecraft_FrameHook",
            "early.glsm.MixinWorldRenderer_VertexState"
        )
    ),

    ;

    private final MixinBuilder builder;
}

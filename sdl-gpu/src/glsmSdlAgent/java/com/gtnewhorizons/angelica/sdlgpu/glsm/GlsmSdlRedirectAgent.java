package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GlsmRedirectAgent;

import java.lang.instrument.Instrumentation;

public final class GlsmSdlRedirectAgent {

    private GlsmSdlRedirectAgent() {}

    public static void premain(String args, Instrumentation instrumentation) {
        GlsmRedirectAgent.install(instrumentation);
        GlsmRedirectAgent.addTransformer(instrumentation, MovedSamplerProbe::instrument);
    }
}

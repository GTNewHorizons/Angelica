package com.gtnewhorizons.angelica.experimental.surround;

import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

public final class SurroundBootstrap {

    private SurroundBootstrap() {
    }

    private static final class Registration {

        static {
            InjectionInfo.register(SurroundInjectionInfo.class);
        }

        static void ensure() {
        }
    }

    public static void init() {
        Registration.ensure();
        if (GlobalProperties.get(GlobalProperties.Keys.INIT) != null && MixinEnvironment.getDefaultEnvironment().getActiveTransformer() instanceof IMixinTransformer) {
            SurroundApplicatorExtension.instance();
        }
    }
}

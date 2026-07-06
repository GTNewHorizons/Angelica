package com.gtnewhorizons.angelica.rendering.celeritas.api;

import org.jetbrains.annotations.Nullable;

public final class IrisShaderProviderHolder {

    private static IrisShaderProvider provider;

    private IrisShaderProviderHolder() {}

    public static void setProvider(@Nullable IrisShaderProvider provider) {
        IrisShaderProviderHolder.provider = provider;
    }

    @Nullable
    public static IrisShaderProvider getProvider() {
        return provider;
    }

    public static boolean isActive() {
        return provider != null && provider.isShadersEnabled();
    }

    public static boolean isShadowPass() {
        return provider != null && provider.isShadowPass();
    }

    public static boolean shouldUseFaceCulling() {
        return provider == null || provider.shouldUseFaceCulling();
    }
}

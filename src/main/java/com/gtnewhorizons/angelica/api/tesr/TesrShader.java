package com.gtnewhorizons.angelica.api.tesr;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/** A registered mod shader; see {@link TesrShaders#register} */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class TesrShader {

    private final String name;
    @Nullable private final Runnable bind;
    @Nullable private final Runnable release;
    @Nullable private final ResourceLocation vertex;
    @Nullable private final ResourceLocation fragment;
    @Nullable private final TesrShaderUniforms uniforms;
}

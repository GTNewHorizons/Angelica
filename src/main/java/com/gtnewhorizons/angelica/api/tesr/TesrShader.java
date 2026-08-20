package com.gtnewhorizons.angelica.api.tesr;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** A registered mod-owned GL program; see {@link TesrShaders#register} */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class TesrShader {

    private final String name;
    private final Runnable bind;
    private final Runnable release;
}

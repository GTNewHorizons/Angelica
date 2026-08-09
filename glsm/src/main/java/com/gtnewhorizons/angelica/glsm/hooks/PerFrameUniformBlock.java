package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.shader.UniformType;

import java.util.List;

/**
 * Shared per-frame uniform block for SDL GPU. Produced by Iris once per shader pack load. Every shader declares the
 * whole block regardless of which members it reads.
 */
public record PerFrameUniformBlock(List<Member> members) {

    public record Member(String name, UniformType type) {}

    public boolean isEmpty() {
        return members.isEmpty();
    }
}

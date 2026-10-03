package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniform;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.nio.FloatBuffer;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

@Mixin(value = GlUniformMatrix4f.class, remap = false)
public abstract class MixinGlUniformMatrix4f extends GlUniform<Matrix4fc> {

    @Unique private final FloatBuffer angelica$buf = BufferUtils.createFloatBuffer(16);

    private MixinGlUniformMatrix4f(int index) {
        super(index);
    }

    /**
     * @author Angelica
     * @reason reuse the upload buffer
     */
    @Overwrite
    public void set(Matrix4fc value) {
        value.get(this.angelica$buf);
        LWJGL.glUniformMatrix4fv(this.index, false, this.angelica$buf);
    }
}

package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniform;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix3f;
import org.joml.Matrix3f;
import org.lwjgl.BufferUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.nio.FloatBuffer;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

@Mixin(value = GlUniformMatrix3f.class, remap = false)
public abstract class MixinGlUniformMatrix3f extends GlUniform<Matrix3f> {

    @Unique private final FloatBuffer angelica$buf = BufferUtils.createFloatBuffer(9);

    private MixinGlUniformMatrix3f(int index) {
        super(index);
    }

    /**
     * @author Angelica
     * @reason reuse the upload buffer
     */
    @Overwrite
    public void set(Matrix3f value) {
        value.get(this.angelica$buf);
        LWJGL.glUniformMatrix3fv(this.index, false, this.angelica$buf);
    }
}

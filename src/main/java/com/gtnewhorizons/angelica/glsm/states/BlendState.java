package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.stacks.IStackableState;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;


@Getter @Setter
public class BlendState implements IStackableState<BlendState>  {
    protected int srcRgb = GL11.GL_ONE;
    protected int dstRgb = GL11.GL_ZERO;
    protected int srcAlpha = GL11.GL_ONE;
    protected int dstAlpha = GL11.GL_ZERO;

    @Override
    public BlendState set(BlendState state) {
        this.srcRgb = state.srcRgb;
        this.dstRgb = state.dstRgb;
        this.srcAlpha = state.srcAlpha;
        this.dstAlpha = state.dstAlpha;
        return this;
    }

}

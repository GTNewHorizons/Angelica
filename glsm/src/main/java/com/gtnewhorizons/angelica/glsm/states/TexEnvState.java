package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.CowDepths;
import com.gtnewhorizons.angelica.glsm.stacks.CowStateStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Per-texture-unit GL_TEXTURE_ENV state for FFP emulation. Tracks the simple texenv mode and all GL_COMBINE sub-parameters.
 */
public final class TexEnvState implements CowStateStack<TexEnvState> {

    public int mode;

    public int combineRgb;
    public int combineAlpha;

    public final int[] sourceRgb = new int[3];
    public final int[] sourceAlpha = new int[3];

    public final int[] operandRgb = new int[3];
    public final int[] operandAlpha = new int[3];

    public float scaleRgb;
    public float scaleAlpha;

    public float envColorR;
    public float envColorG;
    public float envColorB;
    public float envColorA;

    private final TexEnvState[] stack;
    private final CowDepths cow;
    private final boolean isStackEntry;

    public TexEnvState(int id) {
        this(false);
        cow.id = id;
    }

    private TexEnvState(boolean isStackEntry) {
        this.isStackEntry = isStackEntry;
        if (isStackEntry) {
            stack = null;
            cow = null;
        } else {
            stack = new TexEnvState[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
            for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) {
                stack[i] = new TexEnvState(true);
            }
            cow = new CowDepths();
        }
        reset();
    }

    public void copyFrom(TexEnvState other) {
        this.mode = other.mode;
        this.combineRgb = other.combineRgb;
        this.combineAlpha = other.combineAlpha;
        System.arraycopy(other.sourceRgb, 0, this.sourceRgb, 0, 3);
        System.arraycopy(other.sourceAlpha, 0, this.sourceAlpha, 0, 3);
        System.arraycopy(other.operandRgb, 0, this.operandRgb, 0, 3);
        System.arraycopy(other.operandAlpha, 0, this.operandAlpha, 0, 3);
        this.scaleRgb = other.scaleRgb;
        this.scaleAlpha = other.scaleAlpha;
        this.envColorR = other.envColorR;
        this.envColorG = other.envColorG;
        this.envColorB = other.envColorB;
        this.envColorA = other.envColorA;
    }

    public void reset() {
        mode = GL11.GL_MODULATE;
        combineRgb = GL11.GL_MODULATE;
        combineAlpha = GL11.GL_MODULATE;
        sourceRgb[0] = GL11.GL_TEXTURE; sourceRgb[1] = GL13.GL_PREVIOUS; sourceRgb[2] = GL13.GL_CONSTANT;
        sourceAlpha[0] = GL11.GL_TEXTURE; sourceAlpha[1] = GL13.GL_PREVIOUS; sourceAlpha[2] = GL13.GL_CONSTANT;
        operandRgb[0] = GL11.GL_SRC_COLOR; operandRgb[1] = GL11.GL_SRC_COLOR; operandRgb[2] = GL11.GL_SRC_ALPHA;
        operandAlpha[0] = GL11.GL_SRC_ALPHA; operandAlpha[1] = GL11.GL_SRC_ALPHA; operandAlpha[2] = GL11.GL_SRC_ALPHA;
        scaleRgb = 1.0f;
        scaleAlpha = 1.0f;
        envColorR = 0.0f; envColorG = 0.0f; envColorB = 0.0f; envColorA = 0.0f;
    }

    @Override
    public CowDepths cowDepths() {
        if (cow == null) throw new IllegalStateException("Cannot push/pop a stack entry");
        return cow;
    }

    @Override
    public void captureSlot(int s) {
        stack[s].copyFrom(this);
    }

    @Override
    public void restoreSlot(int s) {
        copyFrom(stack[s]);
    }

    public boolean isCombineMode() {
        return mode == GL13.GL_COMBINE;
    }

    @Override
    public int stackId() {
        return cow == null ? -1 : cow.id;
    }
}

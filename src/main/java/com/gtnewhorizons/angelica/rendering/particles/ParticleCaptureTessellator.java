package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

public final class ParticleCaptureTessellator extends Tessellator {

    public static final int MAX_VERTICES = 4;

    private final float[] verts = new float[MAX_VERTICES * 5];
    private final ParticleRenderState state = new ParticleRenderState();
    private final ParticleRenderState scratch = new ParticleRenderState();
    private int captured;
    private boolean spilled;
    private boolean uvSet;
    private float pendingU;
    private float pendingV;
    private boolean closed;
    private Matrix4fc layerModelView;
    private Tessellator run;

    public void begin(int startBrightness, Tessellator runTessellator, Matrix4fc layerModelView) {
        this.layerModelView = layerModelView;
        this.run = runTessellator;
        this.captured = 0;
        this.spilled = false;
        this.closed = false;
        this.uvSet = false;
        this.pendingU = 0.0f;
        this.pendingV = 0.0f;
        this.brightness = startBrightness;
        this.hasBrightness = true;
        this.color = runTessellator.hasColor ? runTessellator.color : 0xFFFFFFFF;
        this.hasColor = runTessellator.hasColor;
        this.hasTexture = false;
        this.hasNormals = false;
        this.isColorDisabled = false;
        this.xOffset = 0.0D;
        this.yOffset = 0.0D;
        this.zOffset = 0.0D;
    }

    public void release() {
        run = null;
        layerModelView = null;
        captured = 0;
    }

    public boolean spilled() {
        return spilled;
    }

    public void propagateColor() {
        if (spilled || closed || !hasColor || run == null) return;
        run.color = color;
        run.hasColor = true;
    }

    public int captured() {
        return captured;
    }

    public float[] vertices() {
        return verts;
    }

    public int capturedBrightness() {
        return brightness;
    }

    public ParticleRenderState state() {
        return state;
    }

    public int capturedColor() {
        return color;
    }

    public void replayInto(Tessellator t) {
        t.setBrightness(brightness);
        if (hasColor) {
            t.setColorRGBA(color & 255, (color >> 8) & 255, (color >> 16) & 255, (color >>> 24) & 255);
        }
        if (uvSet) {
            t.setTextureUV(pendingU, pendingV);
        }
        for (int i = 0, o = 0; i < captured; i++, o += 5) {
            t.addVertexWithUV(verts[o], verts[o + 1], verts[o + 2], verts[o + 3], verts[o + 4]);
        }
    }

    private void spill() {
        if (spilled) return;
        spilled = true;
        if (closed) {
            scratch.sample();
            state.apply();
            final boolean wasDrawing = run.isDrawing;
            if (!wasDrawing) run.startDrawingQuads();
            replayInto(run);
            run.draw();
            if (wasDrawing) run.startDrawingQuads();
            scratch.apply();
        } else {
            replayInto(run);
        }
        captured = 0;
    }

    @Override
    public void setTextureUV(double u, double v) {
        if (spilled) {
            run.setTextureUV(u, v);
            return;
        }
        if (closed) {
            run.setTextureUV(u, v);
            pendingU = (float) u;
            pendingV = (float) v;
            return;
        }
        hasTexture = true;
        uvSet = true;
        pendingU = (float) u;
        pendingV = (float) v;
    }

    @Override
    public void addVertexWithUV(double x, double y, double z, double u, double v) {
        setTextureUV(u, v);
        addVertex(x, y, z);
    }

    @Override
    public void addVertex(double x, double y, double z) {
        if (spilled) {
            run.addVertex(x, y, z);
            return;
        }
        boolean overflow = !uvSet || closed || captured == MAX_VERTICES;
        if (!overflow && captured == 0) {
            if (GLStateManager.getModelViewMatrix().equals(layerModelView)) state.sample();
            else overflow = true;
        }
        if (overflow) {
            spill();
            if (uvSet) run.addVertexWithUV(x, y, z, pendingU, pendingV);
            else run.addVertex(x, y, z);
            return;
        }
        final int o = captured * 5;
        verts[o] = (float) (x + xOffset);
        verts[o + 1] = (float) (y + yOffset);
        verts[o + 2] = (float) (z + zOffset);
        verts[o + 3] = pendingU;
        verts[o + 4] = pendingV;
        captured++;
    }

    @Override
    public void setColorRGBA(int red, int green, int blue, int alpha) {
        if (spilled || closed) {
            run.setColorRGBA(red, green, blue, alpha);
            return;
        }
        final int r = Math.clamp(red, 0, 255);
        final int g = Math.clamp(green, 0, 255);
        final int b = Math.clamp(blue, 0, 255);
        final int a = Math.clamp(alpha, 0, 255);
        final int packed = (a << 24) | (b << 16) | (g << 8) | r;
        if (hasColor && packed != color && captured > 0) {
            spill();
            run.setColorRGBA(red, green, blue, alpha);
            return;
        }
        hasColor = true;
        color = packed;
    }

    @Override
    public void setBrightness(int value) {
        if (spilled || closed) {
            run.setBrightness(value);
            return;
        }
        hasBrightness = true;
        brightness = value;
    }

    @Override
    public void setNormal(float x, float y, float z) {
        if (!closed) spill();
        run.setNormal(x, y, z);
    }

    @Override
    public void disableColor() {
        if (!closed) spill();
        run.disableColor();
    }

    @Override
    public void startDrawingQuads() {
        if (!spilled && !closed && captured != 0) spill();
        run.startDrawingQuads();
    }

    @Override
    public void startDrawing(int mode) {
        if (mode == GL11.GL_QUADS) {
            startDrawingQuads();
            return;
        }
        spill();
        run.startDrawing(mode);
    }

    @Override
    public int draw() {
        if (!spilled && !closed) {
            if (captured == MAX_VERTICES) {
                closed = true;
            } else if (captured != 0) {
                spill();
            }
        }
        return run.draw();
    }
}

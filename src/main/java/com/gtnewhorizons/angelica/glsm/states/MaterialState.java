package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.utils.GLHelper.i2f;

public class MaterialState implements ISettableState<MaterialState> {

    private static final Vector4f vector4f = new Vector4f();
    private static final Vector4i vector4i = new Vector4i();
    private static final Vector3f vector3f = new Vector3f();
    private static final Vector3i vector3i = new Vector3i();

    public int face;

    public final Vector4f ambient;
    public final Vector4f diffuse;
    public final Vector4f specular;
    public final Vector4f emission;
    public float shininess;
    public final Vector3f colorIndexes;

    public MaterialState(int face) {
        this.face = face;
        ambient = new Vector4f(0.2F, 0.2F, 0.2F, 1.0F);
        diffuse = new Vector4f(0.8F, 0.8F, 0.8F, 1.0F);
        specular = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
        emission = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
        shininess = 0.0F;
        colorIndexes = new Vector3f(0.0F, 1.0F, 1.0F);
    }

    public void setAmbient(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
            GL11.glMaterialfv(face, GL11.GL_AMBIENT, newBuffer);
        }
    }

    public void setAmbient(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
            GL11.glMaterialiv(face, GL11.GL_AMBIENT, newBuffer);
        }
    }

    public void setDiffuse(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.diffuse.equals(vector4f)) {
            this.diffuse.set(vector4f);
            GL11.glMaterialfv(face, GL11.GL_DIFFUSE, newBuffer);
        }
    }

    public void setDiffuse(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.diffuse.equals(vector4f)) {
            this.diffuse.set(vector4f);
            GL11.glMaterialiv(face, GL11.GL_DIFFUSE, newBuffer);
        }
    }

    public void setSpecular(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.specular.equals(vector4f)) {
            this.specular.set(vector4f);
            GL11.glMaterialfv(face, GL11.GL_SPECULAR, newBuffer);
        }
    }

    public void setSpecular(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.specular.equals(vector4f)) {
            this.specular.set(vector4f);
            GL11.glMaterialiv(face, GL11.GL_SPECULAR, newBuffer);
        }
    }

    public void setEmission(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.emission.equals(vector4f)) {
            this.emission.set(vector4f);
            GL11.glMaterialfv(face, GL11.GL_EMISSION, newBuffer);
        }
    }

    public void setEmission(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.emission.equals(vector4f)) {
            this.emission.set(vector4f);
            GL11.glMaterialiv(face, GL11.GL_EMISSION, newBuffer);
        }
    }

    public void setShininess(float val) {
        if (GLStateManager.shouldBypassCache() || Float.compare(this.shininess, val) != 0) {
            this.shininess = val;
            GL11.glMaterialf(face, GL11.GL_SHININESS, val);
        }
    }

    public void setShininess(FloatBuffer newBuffer) { setShininess(newBuffer.get()); }

    public void setShininess(IntBuffer newBuffer) { setShininess((float) newBuffer.get());}

    public void setShininess(int val) { setShininess((float) val);}

    public void setColorIndexes(FloatBuffer newBuffer) {
        // You are reading this correctly, nvidia drivers flip the y and z values in
        // glMaterial specifically for GL_COLOR_INDEXES. Other drivers do not do this.
        // This probably breaks things but who knows, GLSM is setup to track to what the
        // driver is doing for it right now.
        if (GLStateManager.isNVIDIA()) {
            vector3f.set(newBuffer.get(0), newBuffer.get(2), newBuffer.get(1));
        } else {
            vector3f.set(newBuffer);
        }

        if (GLStateManager.shouldBypassCache() || !this.colorIndexes.equals(vector3f)) {
            this.colorIndexes.set(vector3f);
            GL11.glMaterialfv(face, GL11.GL_COLOR_INDEXES, newBuffer);
        }
    }

    public void setColorIndexes(IntBuffer newBuffer) {
        vector3i.set(newBuffer.get(0), newBuffer.get(2), newBuffer.get(1));
        vector3f.set((float) vector3i.x, (float) vector3i.y, (float) vector3i.z);
        if (GLStateManager.shouldBypassCache() || !this.colorIndexes.equals(vector3f)) {
            this.colorIndexes.set(vector3f);
            GL11.glMaterialiv(face, GL11.GL_COLOR_INDEXES, newBuffer);
        }
    }

    @Override
    public MaterialState set(MaterialState state) {
        this.ambient.set(state.ambient);
        this.diffuse.set(state.diffuse);
        this.specular.set(state.specular);
        this.emission.set(state.emission);
        this.shininess = state.shininess;
        this.colorIndexes.set(state.colorIndexes);

        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof MaterialState materialState)) return false;
        return this.ambient.equals(materialState.ambient)
            && this.diffuse.equals(materialState.diffuse)
            && this.specular.equals(materialState.specular)
            && this.emission.equals(materialState.emission)
            && Float.compare(materialState.shininess, this.shininess) == 0
            && this.colorIndexes.equals(materialState.colorIndexes);
    }

    @Override
    public MaterialState copy() { return new MaterialState(this.face).set(this); }
}
